package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TripsListPage {

    private final Page page;

    public TripsListPage(Page page) {
        this.page = page;
    }

    public void open() {
        try {
            page.navigate(ConfigReader.baseUrl() + "/trips");
        } catch (Exception first) {
            page.waitForTimeout(1500);
            page.navigate(ConfigReader.baseUrl() + "/trips");
        }
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("table tbody tr").first().waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
    }

    public int rowCount() {
        return page.locator("table tbody tr").count();
    }

    public String firstRowText() {
        return page.locator("table tbody tr").first().innerText().trim();
    }

    /** Filter by the "Trip No" field + Search button. Returns rows after applying. */
    public int filterByTripNo(String tripNo) {
        page.locator("input[placeholder='Trip No']").first().fill(tripNo);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Search$"))).first().click();
        page.waitForTimeout(1500);
        return rowCount();
    }

    /** Filter by the "Order No" field + Search button. */
    public int filterByOrderNo(String orderNo) {
        page.locator("input[placeholder='Order No']").first().fill(orderNo);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Search$"))).first().click();
        page.waitForTimeout(1500);
        return rowCount();
    }

    public void resetFilters() {
        Locator reset = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Reset$")));
        if (reset.count() > 0) { reset.first().click(); page.waitForTimeout(1200); }
    }

    public void sortByColumn(String header) {
        Locator sortBtn = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(header))));
        if (sortBtn.count() == 0) {
            System.out.println("[TripsListPage] '" + header + "' is not a sortable button — skipping sort.");
            return;
        }
        try {
            sortBtn.first().click(new Locator.ClickOptions().setTimeout(5000));
            page.waitForTimeout(1200);
        } catch (Exception e) {
            System.out.println("[TripsListPage] Sort click on '" + header + "' failed (skipped): " + e.getMessage());
        }
    }

    public int totalResults() {
        try {
            String text = page.getByText(Pattern.compile("Showing .* of [\\d,]+ results")).first().innerText();
            Matcher m = Pattern.compile("of ([\\d,]+) results").matcher(text);
            if (m.find()) return Integer.parseInt(m.group(1).replace(",", ""));
        } catch (Exception ignored) { }
        // Fallback: some lists (e.g. staging) don't render an "of N results" count — use visible rows.
        java.util.List<String> __rows = page.locator("table tbody tr").allInnerTexts();
        if (__rows.size() == 1 && __rows.get(0).toLowerCase().matches("(?s).*no .*(found|data|result).*")) return 0;
        return __rows.isEmpty() ? -1 : __rows.size();
    }

    public boolean goToNextPage() {
        Locator next = page.locator("button[aria-label='Go to next page']").first();
        if (next.count() == 0 || !next.isEnabled()) return false;
        String before = firstRowText();
        next.click();
        page.waitForTimeout(1200);
        return !firstRowText().equals(before);
    }
}