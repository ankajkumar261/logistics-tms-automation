package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.util.regex.Pattern;

/**
 * Module 8 — Accounting / Bank Reconciliation (/accounting/bank-reconciliation).
 * Affordances: New bank account (dialog), Import CSV (file upload), Unmatched, Export.
 * We automate account creation (8.1 foundation); full statement import + matching (8.1/8.2)
 * is CSV/data-driven and is not exercised here.
 */
public class BankReconciliationPage {

    private final Page page;

    public BankReconciliationPage(Page page) {
        this.page = page;
    }

    private void navigateWithRetry(String url) {
        try { page.navigate(url); }
        catch (Exception e) { page.waitForTimeout(1500); page.navigate(url); }
    }

    public void open() {
        navigateWithRetry(ConfigReader.baseUrl() + "/accounting/bank-reconciliation");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        // The page can start with no accounts (no table), so anchor on the action button.
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                        .setName(Pattern.compile("New bank account", Pattern.CASE_INSENSITIVE)))
                .first().waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
    }

    public boolean hasImportCsv() {
        return page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("Import CSV", Pattern.CASE_INSENSITIVE))).count() > 0;
    }

    /** Create a bank account via the dialog (required: Reference name, Bank name). */
    public boolean createBankAccount(String refName, String bankName) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("New bank account", Pattern.CASE_INSENSITIVE))).first().click();

        Locator dlg = page.locator("[role='dialog']").first();
        dlg.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        page.waitForTimeout(500);

        dlg.locator("input[placeholder='Main Ops']").first().fill(refName);
        dlg.locator("input[placeholder='TD Canada Trust']").first().fill(bankName);

        dlg.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("Create account", Pattern.CASE_INSENSITIVE))).last().click();

        try {
            dlg.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(10000));
            page.waitForTimeout(1200);
            return true;
        } catch (Exception e) {
            java.util.List<String> errs = dlg.locator("[role='alert'], .text-destructive").allInnerTexts();
            System.out.println("[BankReconciliationPage] Create account blocked. Validation: " + errs);
            return false;
        }
    }

    /** True if the given text appears anywhere on the page (accounts render as cards, not a table). */
    public boolean pageContains(String text) {
        Locator match = page.getByText(text, new Page.GetByTextOptions().setExact(false));
        try {
            match.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            return match.first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }
}
