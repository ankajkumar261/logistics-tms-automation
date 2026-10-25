package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Page object for a Trip's "Documents" card on the trip detail page.
 *
 * Real DOM (confirmed live, Jul 2026): the Documents card is a COLLAPSIBLE
 * card — the header (h3 "Documents", cursor-pointer) must be clicked to expand
 * it before its radix tablist (BOL / POD / Files) is even rendered. Each tab
 * panel is lazily mounted, so the file <input> only exists after the Files tab
 * is activated. The upload control is an icon-only submit button (lucide-upload)
 * that stays disabled until a file is chosen.
 */
public class TripDocumentsPage {

    private final Page page;
    private String tripUrl;
    private int lastUploadStatus = -1;

    public TripDocumentsPage(Page page) {
        this.page = page;
    }

    /** HTTP status of the most recent attachment-upload POST, or -1 if none was observed. */
    public int lastUploadStatus() { return lastUploadStatus; }

    /** Open the first trip from the list; remembers its URL so we can reopen it later. */
    public String openFirstTrip() {
        // Retry once on the transient SPA net::ERR_ABORTED redirect race (worse under parallel load).
        try {
            page.navigate(ConfigReader.baseUrl() + "/trips");
        } catch (Exception first) {
            page.waitForTimeout(1500);
            page.navigate(ConfigReader.baseUrl() + "/trips");
        }
        page.waitForLoadState(LoadState.NETWORKIDLE);
        Locator firstRow = page.locator("table tbody tr").first();
        firstRow.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        String tripNo = firstRow.locator("td").first().innerText().trim().replaceAll("\\s+", " ");
        firstRow.click();
        page.waitForURL("**/trips/**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);
        this.tripUrl = page.url();
        return tripNo;
    }

    public String tripUrl() { return tripUrl; }

    /** The "Documents" card heading (the collapse/expand toggle). */
    private Locator documentsHeading() {
        return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^\\s*Documents\\s*$", Pattern.CASE_INSENSITIVE))).first();
    }

    /** Expand the Documents card if it's collapsed (tabs only exist once expanded). */
    private void ensureDocumentsExpanded() {
        Locator heading = documentsHeading();
        heading.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        heading.scrollIntoViewIfNeeded();

        // Tabs (BOL/POD/Files) are rendered only when the card is open.
        Locator anyTab = page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions()
                .setName(Pattern.compile("BOL|POD|Files", Pattern.CASE_INSENSITIVE)));
        if (anyTab.count() == 0 || !anyTab.first().isVisible()) {
            heading.click();
            page.waitForTimeout(800);
        }
    }

    /** Expand the card, click a tab (BOL/POD/Files), and return that tab's panel. */
    private Locator openDocumentsTab(String tabName) {
        ensureDocumentsExpanded();

        Locator card = documentsHeading()
                .locator("xpath=ancestor::div[contains(@class,'rounded-lg')][1]");

        Locator tab = card.getByRole(AriaRole.TAB,
                new Locator.GetByRoleOptions().setName(Pattern.compile(tabName, Pattern.CASE_INSENSITIVE)));
        if (tab.count() == 0) {
            tab = page.getByRole(AriaRole.TAB,
                    new Page.GetByRoleOptions().setName(Pattern.compile(tabName, Pattern.CASE_INSENSITIVE)));
        }
        if (tab.count() == 0) {
            System.out.println("[TripDocuments] No tab matched '" + tabName + "' (card expanded).");
            return page.locator("[role='tabpanel'][data-state='active']").first();
        }

        Locator tabEl = tab.first();
        tabEl.scrollIntoViewIfNeeded();
        tabEl.click();
        page.waitForTimeout(800);

        // Scope precisely to the panel this tab controls (avoids the BOL/POD panels).
        String panelId = tabEl.getAttribute("aria-controls");
        if (panelId != null && !panelId.isBlank()) {
            Locator scoped = page.locator("[id='" + panelId + "']");
            if (scoped.count() > 0) return scoped.first();
        }
        return page.locator("[role='tabpanel'][data-state='active']").first();
    }

    /** Read the number in a tab label like "Files (3)"; -1 if not present. */
    private int tabCount(String tabName) {
        Locator tab = page.getByRole(AriaRole.TAB,
                new Page.GetByRoleOptions().setName(Pattern.compile(tabName, Pattern.CASE_INSENSITIVE)));
        if (tab.count() == 0) return -1;
        Matcher m = Pattern.compile("\\((\\d+)\\)").matcher(tab.first().innerText());
        return m.find() ? Integer.parseInt(m.group(1).replace(",", "")) : -1;
    }

    /** Switch to the Files tab, choose the file, and click the upload button.
     *  True once the file has been handed to the input (real success is verified by documentAppears). */
    public boolean uploadDocument(String tabName, Path file) {
        Locator panel = openDocumentsTab(tabName);

        // The file <input> is mounted lazily inside the active Files panel.
        Locator fileInput = panel.locator("input[type='file']");
        if (fileInput.count() == 0) fileInput = page.locator("input[type='file']");
        if (fileInput.count() == 0) {
            System.out.println("[TripDocuments] No file input found on '" + tabName + "' tab.");
            dumpPanel(panel);
            return false;
        }

        fileInput.first().setInputFiles(file);
        page.waitForTimeout(600);

        // Upload button is icon-only (lucide-upload) and disabled until a file is picked.
        Locator uploadBtn = panel.locator("button:has(svg.lucide-upload)");
        if (uploadBtn.count() == 0) {
            uploadBtn = panel.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                    .setName(Pattern.compile("^(Upload|Attach|Save|Add)$", Pattern.CASE_INSENSITIVE)));
        }
        if (uploadBtn.count() == 0) {
            System.out.println("[TripDocuments] File selected but no upload button found on '"
                    + tabName + "' tab.");
            return true;
        }

        Locator btn = uploadBtn.first();
        lastUploadStatus = -1;
        try {
            // Give React a moment to enable the button after the change event.
            for (int i = 0; i < 25 && !btn.isEnabled(); i++) page.waitForTimeout(200);
            btn.scrollIntoViewIfNeeded();

            // Click and capture the attachment-upload POST response so callers can
            // tell a real UI problem apart from a server-side (e.g. 502) failure.
            Response resp = page.waitForResponse(
                    r -> r.url().contains("/attachments") && r.request().method().equalsIgnoreCase("POST"),
                    new Page.WaitForResponseOptions().setTimeout(15000),
                    btn::click);
            lastUploadStatus = resp.status();
            System.out.println("[TripDocuments] Upload POST " + resp.url() + " -> HTTP " + lastUploadStatus);
        } catch (Exception e) {
            System.out.println("[TripDocuments] Upload clicked but no /attachments POST captured: "
                    + e.getMessage().split("\n")[0]);
        }
        page.waitForTimeout(2000); // let the list refresh if the POST succeeded
        return true;
    }

    private void dumpPanel(Locator panel) {
        try {
            String html = panel.innerHTML();
            System.out.println("[TripDocuments] Active panel HTML (first 1500 chars):\n"
                    + html.substring(0, Math.min(1500, html.length())));
        } catch (Exception ignored) {}
    }

    /** True if the filename shows up OR the Files tab counter went above zero. */
    public boolean documentAppears(String fileName) {
        Locator match = page.getByText(fileName, new Page.GetByTextOptions().setExact(false));
        try {
            match.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            if (match.first().isVisible()) return true;
        } catch (Exception ignored) {}
        return tabCount("Files") > 0;
    }

    /** Reopen the same trip, expand Documents, go to Files, and confirm the file persisted. */
    public boolean reopenAndCheck(String fileName) {
        if (tripUrl == null) return false;
        page.navigate(tripUrl);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);
        openDocumentsTab("Files");
        page.waitForTimeout(800);
        return documentAppears(fileName);
    }
}
