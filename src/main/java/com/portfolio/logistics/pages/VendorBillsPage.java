package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Module 7 — Accounting / Vendor Bills (AP). List at /accounting/bills and the create form
 * at /accounting/bills/new. Required: Bill Number, Vendor Name, Category (default "Other"),
 * Amount, Currency (default CAD), Bill Date, Due Date. Create → returns to the list.
 */
public class VendorBillsPage {

    private final Page page;

    public VendorBillsPage(Page page) {
        this.page = page;
    }

    private void navigateWithRetry(String url) {
        try { page.navigate(url); }
        catch (Exception e) { page.waitForTimeout(1500); page.navigate(url); }
    }

    public void open() {
        navigateWithRetry(ConfigReader.baseUrl() + "/accounting/bills");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("table tbody tr").first().waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
    }

    public int rowCount() { return page.locator("table tbody tr").count(); }

    public String firstBillNo() {
        return page.locator("table tbody tr").first().locator("td").first().innerText().trim();
    }

    public int totalResults() {
        try {
            String text = page.getByText(Pattern.compile("of [\\d,]+ results")).first().innerText();
            Matcher m = Pattern.compile("of ([\\d,]+) results").matcher(text);
            if (m.find()) return Integer.parseInt(m.group(1).replace(",", ""));
        } catch (Exception ignored) { }
        // Fallback: some lists (e.g. staging) don't render an "of N results" count — use visible rows.
        java.util.List<String> __rows = page.locator("table tbody tr").allInnerTexts();
        if (__rows.size() == 1 && __rows.get(0).toLowerCase().matches("(?s).*no .*(found|data|result).*")) return 0;
        return __rows.isEmpty() ? -1 : __rows.size();
    }

    public int search(String term) {
        Locator box = page.locator("input[placeholder='Search bills...'], input[placeholder*='Search' i]").first();
        box.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        box.fill(term);
        page.waitForTimeout(1500);
        return rowCount();
    }

    public int filterByBillNo(String billNo) {
        page.locator("input[placeholder='Bill No']").first().fill(billNo);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Search$"))).first().click();
        page.waitForTimeout(1500);
        return rowCount();
    }

    public void reset() {
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

    /** Create a vendor bill via /accounting/bills/new. Returns true if it was created (form left). */
    public boolean createBill(String billNo, String vendor, String amount,
                              String billDateYyyyMmDd, String dueDateYyyyMmDd) {
        navigateWithRetry(ConfigReader.baseUrl() + "/accounting/bills/new");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("input[name='billNumber']").first().waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));

        page.locator("input[name='billNumber']").first().fill(billNo);
        page.locator("input[name='vendorName']").first().fill(vendor);
        page.locator("input[name='amount']").first().fill(amount);
        page.locator("input[name='billDate']").first().fill(billDateYyyyMmDd);
        page.locator("input[name='dueDate']").first().fill(dueDateYyyyMmDd);
        // Category (default "Other") and Currency (default CAD) keep their defaults.

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("Create Bill", Pattern.CASE_INSENSITIVE))).first().click();

        try {
            page.waitForURL(u -> u.contains("/accounting/bills") && !u.endsWith("/new"),
                    new Page.WaitForURLOptions().setTimeout(15000));
            page.waitForTimeout(800);
            return true;
        } catch (Exception e) {
            java.util.List<String> errs = page.locator("[role='alert'], .text-destructive, [data-sonner-toast]").allInnerTexts();
            System.out.println("[VendorBillsPage] Create Bill did not complete (still on the form). Messages: " + errs);
            return false;
        }
    }
}
