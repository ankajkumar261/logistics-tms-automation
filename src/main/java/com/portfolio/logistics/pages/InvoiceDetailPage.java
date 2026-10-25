package com.portfolio.logistics.pages;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Module 5 — a single invoice's detail page (/accounting/invoices/:id).
 * Draft actions: Edit, Download, Send Invoice, Mark as Sent, Void.
 * Sent actions:  Edit, Download, Send Invoice, Mark as Paid, Void.
 */
public class InvoiceDetailPage {

    private final Page page;

    public InvoiceDetailPage(Page page) {
        this.page = page;
    }

    /** Navigate, retrying once if post-login SPA routing ERR_ABORTED the first attempt. */
    private void navigateWithRetry(String url) {
        try {
            page.navigate(url);
        } catch (Exception e) {
            page.waitForTimeout(1500);
            page.navigate(url);
        }
    }

    /** Open a specific invoice from the list (filter by Invoice#, click the row). */
    public void open(String invoiceNo) {
        navigateWithRetry(ConfigReader.baseUrl() + "/accounting/invoices");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        Locator filter = page.locator("input[placeholder='Invoice No']").first();
        if (filter.count() > 0) {
            filter.fill(invoiceNo);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                    .setName(Pattern.compile("^Search$"))).first().click();
            page.waitForTimeout(1500);
        }
        page.locator("table tbody tr").filter(new Locator.FilterOptions().setHasText(invoiceNo))
                .first().click();
        page.waitForURL("**/accounting/invoices/**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1200);

        // The invoice's action buttons render async — wait for them so callers don't probe too early.
        try {
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                            .setName(Pattern.compile("Edit|Download|Void", Pattern.CASE_INSENSITIVE)))
                    .first().waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
        } catch (Exception ignored) { }
    }

    public String invoiceId() {
        return page.url().replaceAll(".*/accounting/invoices/", "").replaceAll("[/?].*", "");
    }

    public boolean hasDownloadButton() {
        Locator dl = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("Download", Pattern.CASE_INSENSITIVE)));
        try {
            dl.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Click the invoice's "Download" action and save the resulting PDF into {targetDir}.
     *  Returns the saved file path (named by the app, e.g. QA-INV-1234.pdf). */
    public Path downloadInvoicePdf(Path targetDir) {
        Locator dl = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("Download", Pattern.CASE_INSENSITIVE)));
        dl.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        Download download = page.waitForDownload(
                new Page.WaitForDownloadOptions().setTimeout(20000),
                () -> dl.first().click());
        Path target = targetDir.resolve(download.suggestedFilename());
        download.saveAs(target);
        System.out.println("[InvoiceDetailPage] Saved invoice PDF: " + target.toAbsolutePath());
        return target;
    }

    public boolean markAsSent() { return clickAction("Mark as Sent"); }
    public boolean markAsPaid() { return clickAction("Mark as Paid"); }
    public boolean sendInvoice() { return clickAction("Send Invoice"); }

    /** Click a detail action button and accept any confirmation dialog that appears.
     *  NB: pass the label straight to Pattern.compile — NOT Pattern.quote(). Playwright turns Java
     *  patterns into JS RegExp, and JS has no \Q…\E, so a quoted label silently matches nothing
     *  (this is why "Mark as Sent" was "not found" while plain "Download" worked). Our labels have
     *  no regex-special chars, so a plain (non-anchored) pattern is correct here. */
    private boolean clickAction(String label) {
        Locator btn = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile(label, Pattern.CASE_INSENSITIVE)));
        try {
            btn.first().scrollIntoViewIfNeeded();
            btn.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(6000));
        } catch (Exception e) {
            System.out.println("[InvoiceDetailPage] No '" + label + "' action. Buttons: "
                    + page.locator("button").allInnerTexts());
            return false;
        }
        btn.first().click();
        page.waitForTimeout(1200);

        // Confirm dialog (e.g. "Are you sure?") — accept it, scoped so we don't re-hit the page button.
        Locator dlg = page.locator("[role='dialog'], [role='alertdialog']");
        if (dlg.count() > 0 && dlg.first().isVisible()) {
            Locator confirm = dlg.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                    .setName(Pattern.compile("Confirm|Yes|OK|Continue|Mark as (Sent|Paid)|^Send$", Pattern.CASE_INSENSITIVE)));
            if (confirm.count() > 0) {
                try { confirm.last().click(); page.waitForTimeout(1500); } catch (Exception ignored) { }
            }
        }
        page.waitForTimeout(1500);
        return true;
    }

    /** Go to the list, filter by Invoice#, and confirm its Status cell reads the expected status.
     *  Uses an anchored match so "Sent" never matches the Delivery column's "Not sent". */
    public boolean listShowsStatus(String invoiceNo, String expectedStatus) {
        navigateWithRetry(ConfigReader.baseUrl() + "/accounting/invoices");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        Locator filter = page.locator("input[placeholder='Invoice No']").first();
        if (filter.count() > 0) {
            filter.fill(invoiceNo);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                    .setName(Pattern.compile("^Search$"))).first().click();
            page.waitForTimeout(1500);
        }
        Locator row = page.locator("table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(invoiceNo)).first();
        try {
            Locator statusPill = row.getByText(Pattern.compile("^\\s*" + expectedStatus + "\\s*$", Pattern.CASE_INSENSITIVE));
            statusPill.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            return statusPill.first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }
}
