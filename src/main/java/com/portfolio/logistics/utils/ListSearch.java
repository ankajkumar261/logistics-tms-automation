package com.portfolio.logistics.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Finds a value in a paginated list that has no search box (the C-Panel master-data lists page at
 * 10/row and sort new records onto LATER pages). Walks every page via "Go to next page" so a
 * just-added record isn't missed by only checking page 1.
 */
public final class ListSearch {

    private static final int MAX_PAGES = 30;

    private ListSearch() { }

    /** True if {text} is visible on the current list or any following page. */
    public static boolean containsAcrossPages(Page page, String text) {
        return rowTextAcrossPages(page, text) != null;
    }

    /**
     * The full row text of the first row containing {text}, walking every page; null if not found.
     * Returning the row (not just a boolean) lets a test assert on the record's OTHER columns —
     * e.g. that a truck's License Plate actually persisted, not merely that the truck exists.
     */
    public static String rowTextAcrossPages(Page page, String text) {
        awaitTable(page); // the list data can load async (caller may only have waited for LOAD)
        for (int p = 0; p < MAX_PAGES; p++) {
            Locator row = page.locator("table tbody tr")
                    .filter(new Locator.FilterOptions().setHasText(text)).first();
            try {
                row.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(1500));
                return row.innerText().replaceAll("\\s+", " ").trim();
            } catch (Exception notOnThisPage) {
                Locator next = page.locator("button[aria-label='Go to next page']").first();
                if (next.count() == 0 || !next.isEnabled()) return null; // last page
                next.click();
                page.waitForTimeout(800);
                awaitTable(page);
            }
        }
        return null;
    }

    private static void awaitTable(Page page) {
        try {
            page.locator("table tbody tr").first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        } catch (Exception ignored) { /* empty list */ }
    }
}
