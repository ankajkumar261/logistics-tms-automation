package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrdersListPage {

    private final Page page;

    public OrdersListPage(Page page) {
        this.page = page;
    }

    /** Open the Orders list and ensure the Orders tab (not Drafts) is active. */
    public void open() {
        // /orders occasionally net::ERR_ABORTs on the first hit (SPA redirect race under parallel
        // load) — retry once before letting it surface.
        try {
            page.navigate(ConfigReader.baseUrl() + "/orders");
        } catch (Exception first) {
            page.waitForTimeout(1500);
            page.navigate(ConfigReader.baseUrl() + "/orders");
        }
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator ordersTab = page.getByRole(AriaRole.TAB,
                new Page.GetByRoleOptions().setName(Pattern.compile("^Orders$")));
        if (ordersTab.count() > 0) {
            ordersTab.first().click();
            page.waitForTimeout(600);
        }
        page.locator("table tbody tr").first().waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
    }

    public int rowCount() {
        return page.locator("table tbody tr").count();
    }

    public String firstOrderCellText() {
        return page.locator("table tbody tr").first().locator("td").first().innerText().trim();
    }

    /** Free-text search via the plain "Search..." box. Returns rows after searching. */
    public int search(String term) {
        Locator search = page.locator("input[placeholder='Search...'], input[placeholder*='Search' i]").first();
        search.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        search.fill(term);
        page.waitForTimeout(1500);
        return rowCount();
    }

    public void clearSearch() {
        Locator search = page.locator("input[placeholder='Search...'], input[placeholder*='Search' i]").first();
        search.fill("");
        page.waitForTimeout(1000);
    }

    /** Filter using the "Order#" field + Search button. Returns rows after applying. */
    public int filterByOrderNo(String orderNo) {
        page.locator("input[placeholder='Order No']").first().fill(orderNo);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Search$"))).first().click();
        page.waitForTimeout(1500);
        return rowCount();
    }

    /** Filter using the "Cust. Order#" field + Search button. Returns rows after applying. */
    public int filterByCustomerOrder(String custOrderNo) {
        page.locator("input[placeholder='Customer Order']").first().fill(custOrderNo);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Search$"))).first().click();
        page.waitForTimeout(1500);
        return rowCount();
    }

    public void resetFilters() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Reset$"))).first().click();
        page.waitForTimeout(1200);
    }

    public boolean tableContains(String text) {
        Locator match = page.locator("table tbody").getByText(text, new Locator.GetByTextOptions().setExact(false));
        try {
            match.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            return match.first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    /** Click a sortable column header by its visible name (e.g. "Order #", "Date"). */
    public void sortByColumn(String header) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile(header))).first().click();
        page.waitForTimeout(1200);
    }

    /** Read the "of N results" total shown in the pager, or -1 if not found. */
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

    /** Advance to the next page. Returns true if the first row changed. */
    public boolean goToNextPage() {
        Locator next = page.locator("button[aria-label='Go to next page']").first();
        if (next.count() == 0 || !next.isEnabled()) return false;
        String before = firstOrderCellText();
        next.click();
        page.waitForTimeout(1200);
        return !firstOrderCellText().equals(before);
    }
}