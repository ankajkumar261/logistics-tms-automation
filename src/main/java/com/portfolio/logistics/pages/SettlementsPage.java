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
 * Module 6 — Accounting / Driver Settlements (/accounting/settlements).
 * List cols: (checkbox), Settlement #, Driver, Period, Gross, Deductions, Net, Status.
 * Detail sections: Settlement Summary, Adjustments, Pay Summary. Actions: Raise adjustment,
 * plus Approve Settlement (Pending) or Mark as Paid (Approved). There is no "Generate" affordance
 * on the list, so 6.1's create is not exercised; we verify list + detail structure instead.
 */
public class SettlementsPage {

    private final Page page;

    public SettlementsPage(Page page) {
        this.page = page;
    }

    private void navigateWithRetry(String url) {
        try { page.navigate(url); }
        catch (Exception e) { page.waitForTimeout(1500); page.navigate(url); }
    }

    public void open() {
        navigateWithRetry(ConfigReader.baseUrl() + "/accounting/settlements");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("table tbody tr").first().waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
    }

    public int rowCount() { return page.locator("table tbody tr").count(); }

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

    /** Settlement # lives in the 2nd cell (1st is the row checkbox). */
    public String firstSettlementNo() {
        return page.locator("table tbody tr").first().locator("td").nth(1).innerText().trim();
    }

    public int filterBySettlementNo(String no) {
        page.locator("input[placeholder='Settlement No']").first().fill(no);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Search$"))).first().click();
        page.waitForTimeout(1500);
        return rowCount();
    }

    public int search(String term) {
        Locator box = page.locator("input[placeholder='Search settlements...'], input[placeholder*='Search' i]").first();
        box.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        box.fill(term);
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

    /** Open the first settlement's detail page. Returns its Settlement #. */
    public String openFirstSettlement() {
        String no = firstSettlementNo();
        page.locator("table tbody tr").first().locator("td").nth(1).click();
        page.waitForURL("**/accounting/settlements/**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(2000);
        return no;
    }

    /** 6.1 EXP: the detail shows line items + net (Settlement Summary / Adjustments / Net). */
    public boolean detailShowsSummaryAndNet() {
        boolean summary = page.getByText(Pattern.compile("Settlement Summary", Pattern.CASE_INSENSITIVE)).count() > 0;
        boolean adjustments = page.getByText(Pattern.compile("Adjustments", Pattern.CASE_INSENSITIVE)).count() > 0;
        boolean net = page.getByText(Pattern.compile("\\bNet\\b", Pattern.CASE_INSENSITIVE)).count() > 0;
        return summary && adjustments && net;
    }

    /** 6.2: the detail exposes adjust + finalize actions (Raise adjustment + Approve/Mark as Paid). */
    public boolean detailHasAdjustAndFinalizeActions() {
        boolean adjust = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("Raise adjustment", Pattern.CASE_INSENSITIVE))).count() > 0;
        boolean finalize = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("Approve Settlement|Mark as Paid", Pattern.CASE_INSENSITIVE))).count() > 0;
        return adjust && finalize;
    }
}
