package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TripDetailPage {

    private final Page page;

    public TripDetailPage(Page page) {
        this.page = page;
    }

    /** Open the first trip that still has a "Dispatch Trip" button (i.e. not yet completed).
     *  Returns the Trip #, or null if none on the first page are actionable. */
    public String openDispatchableTrip() {
        page.navigate(ConfigReader.baseUrl() + "/trips");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("table tbody tr").first().waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));

        int rows = page.locator("table tbody tr").count();
        for (int i = 0; i < rows; i++) {
            Locator row = page.locator("table tbody tr").nth(i);

            // Only open trips the LIST marks Scheduled/Planned — otherwise we waste time opening
            // completed/in-progress trips (and the async "Dispatch Trip" button never appears).
            String rowText;
            try { rowText = row.innerText(); } catch (Exception e) { continue; }
            if (!Pattern.compile("Scheduled|Planned", Pattern.CASE_INSENSITIVE).matcher(rowText).find()) {
                continue;
            }

            // Read the trip # from the Trip# cell only (matching the whole row would let digits
            // from the adjacent Date column bleed into the number).
            String firstCell = row.locator("td").first().innerText().replaceAll("\\s+", "");
            Matcher tm = Pattern.compile("TRP-\\d+").matcher(firstCell);
            String tripNo = tm.find() ? tm.group() : firstCell;

            row.click();
            page.waitForURL("**/trips/**");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // The detail's action buttons render asynchronously — WAIT for "Dispatch Trip"
            // (an instant count() check here is the bug that made this skip dispatchable trips).
            Locator dispatch = page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(Pattern.compile("Dispatch Trip", Pattern.CASE_INSENSITIVE)));
            try {
                dispatch.first().waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
                System.out.println("[TripDetailPage] Dispatchable trip: " + tripNo);
                return tripNo;
            } catch (Exception notDispatchable) {
                // Status said Scheduled but no Dispatch action — go back and try the next one.
                page.navigate(ConfigReader.baseUrl() + "/trips");
                page.waitForLoadState(LoadState.NETWORKIDLE);
                page.locator("table tbody tr").first().waitFor(
                        new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
            }
        }
        System.out.println("[TripDetailPage] No dispatchable (Scheduled/Planned) trip found on the first page.");
        return null;
    }

    /** Open a specific trip by its number (search the list, click its row) and confirm it's
     *  dispatchable (the async "Dispatch Trip" button appears). Returns true if opened & dispatchable. */
    public boolean openTripByNumber(String tripNo) {
        if (tripNo == null) return false;
        Matcher m = Pattern.compile("TRP-\\d+").matcher(tripNo);
        String q = m.find() ? m.group() : tripNo;

        page.navigate(ConfigReader.baseUrl() + "/trips");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator tripFilter = page.locator("input[placeholder='Trip No']").first();
        if (tripFilter.count() > 0) {
            tripFilter.fill(q);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                    .setName(Pattern.compile("^Search$"))).first().click();
            page.waitForTimeout(1500);
        }

        Locator row = page.locator("table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(q)).first();
        try {
            row.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            row.click();
            page.waitForURL("**/trips/**");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                            .setName(Pattern.compile("Dispatch Trip", Pattern.CASE_INSENSITIVE)))
                    .first().waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            System.out.println("[TripDetailPage] Opened trip by number: " + q);
            return true;
        } catch (Exception e) {
            System.out.println("[TripDetailPage] Could not open dispatchable trip " + q + ": " + e.getMessage().split("\n")[0]);
            return false;
        }
    }

    /** Click the workflow advance button matching one of the given labels. */
    public boolean advanceStep(String... labelAlternatives) {
        for (String label : labelAlternatives) {
            Locator btn = page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(Pattern.compile(label, Pattern.CASE_INSENSITIVE)));
            if (btn.count() > 0 && btn.first().isVisible()) {
                btn.first().scrollIntoViewIfNeeded();
                btn.first().click();
                page.waitForTimeout(1500);

                // Accept a confirm dialog if one appears.
                Locator dlg = page.locator("div[role='dialog']").getByRole(AriaRole.BUTTON,
                        new Locator.GetByRoleOptions().setName(Pattern.compile("Confirm|Yes|OK|Mark|Arrived|Deliver", Pattern.CASE_INSENSITIVE)));
                if (dlg.count() > 0) {
                    try { dlg.first().click(); page.waitForTimeout(1500); } catch (Exception ignored) { }
                }
                page.waitForTimeout(1000);
                return true;
            }
        }
        System.out.println("[TripDetailPage] None of these workflow buttons found: "
                + String.join(" / ", labelAlternatives)
                + " | Buttons on page: " + page.locator("button").allInnerTexts());
        return false;
    }

    /** Verify the trip's status in the list (search by trip #). */
    public boolean listStatusIs(String tripNo, String expectedStatus) {
        page.navigate(ConfigReader.baseUrl() + "/trips");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // trip # may include a "TRP-..." token; extract it.
        Matcher m = Pattern.compile("TRP-\\d+").matcher(tripNo);
        String q = m.find() ? m.group() : tripNo;

        // Filter by Trip No if the field exists.
        Locator tripFilter = page.locator("input[placeholder='Trip No']").first();
        if (tripFilter.count() > 0) {
            tripFilter.fill(q);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                    .setName(Pattern.compile("^Search$"))).first().click();
            page.waitForTimeout(1500);
        }

        // Check the status WITHIN this trip's row (a page-wide text match could hit another trip).
        Locator row = page.locator("table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(q)).first();
        try {
            row.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            Locator status = row.getByText(Pattern.compile(expectedStatus, Pattern.CASE_INSENSITIVE));
            status.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
            return status.first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }
}