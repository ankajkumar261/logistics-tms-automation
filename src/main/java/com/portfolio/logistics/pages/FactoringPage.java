package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.util.regex.Pattern;

/**
 * Module 9 — Accounting / Factoring companies (/accounting/factoring).
 * List + the "Add Company" dialog (required: Company Name, Advance Rate %, Factoring Fee %,
 * Reserve Rate %; Payment Terms defaults "24 Hours", Status defaults "Active"; address optional).
 */
public class FactoringPage {

    private final Page page;

    public FactoringPage(Page page) {
        this.page = page;
    }

    private void navigateWithRetry(String url) {
        try { page.navigate(url); }
        catch (Exception e) { page.waitForTimeout(1500); page.navigate(url); }
    }

    public void open() {
        navigateWithRetry(ConfigReader.baseUrl() + "/accounting/factoring");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("table tbody tr").first().waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
    }

    public int rowCount() { return page.locator("table tbody tr").count(); }

    public int search(String term) {
        Locator box = page.locator("input[placeholder='Search companies...'], input[placeholder*='Search' i]").first();
        box.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        box.fill(term);
        page.waitForTimeout(1500);
        return rowCount();
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

    /** Add a factoring company via the dialog. Returns true if the dialog closed (saved). */
    public boolean addCompany(String name, String advanceRate, String factoringFee, String reserveRate) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("Add Company", Pattern.CASE_INSENSITIVE))).first().click();

        Locator dlg = page.locator("[role='dialog']").first();
        dlg.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        page.waitForTimeout(500);

        dlg.locator("input[name='companyName']").first().fill(name);
        dlg.locator("input[name='advanceRate']").first().fill(advanceRate);
        dlg.locator("input[name='factoringFee']").first().fill(factoringFee);
        dlg.locator("input[name='reserveRate']").first().fill(reserveRate);
        // Contact Name / Phone / Email became required on the form — fill them with valid data.
        fillIfPresent(dlg, "input[name='contactName']", "QA Contact");
        fillIfPresent(dlg, "input[name='phone']", "(555) 010-2030");
        fillIfPresent(dlg, "input[name='email']", "qa-factor@example.test");
        // Payment Terms (24 Hours) and Status (Active) keep their defaults; address is optional.

        dlg.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("Add Company", Pattern.CASE_INSENSITIVE))).last().click();

        try {
            dlg.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(10000));
            page.waitForTimeout(1000);
            return true;
        } catch (Exception e) {
            java.util.List<String> errs = dlg.locator("[role='alert'], .text-destructive").allInnerTexts();
            System.out.println("[FactoringPage] Add Company blocked. Validation: " + errs);
            return false;
        }
    }

    /** Fill a field inside {scope} by selector when it exists (covers optional-then-required fields). */
    private void fillIfPresent(Locator scope, String selector, String value) {
        Locator f = scope.locator(selector);
        if (f.count() > 0) f.first().fill(value);
    }
}
