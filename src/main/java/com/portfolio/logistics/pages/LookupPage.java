package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.model.LookupEntry;

import java.util.List;

/** Shared logic for the simple C-Panel lookup pages (expense/insurance types). */
public abstract class LookupPage {

    protected final Page page;

    protected LookupPage(Page page) {
        this.page = page;
    }

    /** e.g. "expense-types" or "insurance-types". */
    protected abstract String route();

    /** e.g. "Add Expense Type" — used as a fallback button name. */
    protected abstract String addButtonName();

    private Locator dialog() {
        return page.locator("div[role='dialog']");
    }

    public void open() {
        navigateWithRetry(ConfigReader.baseUrl() + "/cpanel/" + route());
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    /** cpanel routes occasionally net::ERR_ABORT on the first hit (SPA redirect race) — retry once. */
    private void navigateWithRetry(String url) {
        try {
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        } catch (Exception first) {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        }
    }

    public void addEntry(LookupEntry e) {
        Locator addBtn = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(addButtonName()));
        if (addBtn.count() == 0) {
            addBtn = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                    .setName(java.util.regex.Pattern.compile("^Add$", java.util.regex.Pattern.CASE_INSENSITIVE)));
        }
        addBtn.first().click();

        Locator d = dialog();
        d.locator("input[name='name'], #name").first().waitFor();

        d.locator("input[name='name'], #name").first().fill(e.getName());
        Locator desc = d.locator("input[name='description'], #description");
        if (desc.count() > 0) desc.first().fill("QA automated entry");

        // Submit is the dialog's "Add" button (not Cancel/Close).
        d.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                        .setName(java.util.regex.Pattern.compile("^Add", java.util.regex.Pattern.CASE_INSENSITIVE)))
                .first().click();

        // Wait for the dialog to close — proof of save.
        try {
            d.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN).setTimeout(8000));
        } catch (TimeoutError ex) {
            List<String> errors = d.locator("[role='alert'], .text-destructive").allInnerTexts();
            throw new AssertionError("Add entry didn't complete on /" + route() + ". Validation: " + errors);
        }
    }

    public boolean isEntryPresent(String name) {
        // Reload so the new row is definitely in the list, then search if phealthcareble.
        navigateWithRetry(ConfigReader.baseUrl() + "/cpanel/" + route());
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator search = page.locator("input[type='search'], input[placeholder*='Search' i]");
        if (search.count() > 0) {
            search.first().fill(name);
            page.waitForTimeout(1200);
        }

        // No search box on these lists — page through (new rows land on later pages).
        return com.portfolio.logistics.utils.ListSearch.containsAcrossPages(page, name);
    }

    private void fillFirst(Locator scope, String value, String... selectors) {
        for (String sel : selectors) {
            Locator f = scope.locator(sel);
            if (f.count() > 0) { f.first().fill(value); return; }
        }
        System.out.println("[LookupPage /" + route() + "] No field matched among: " + String.join(", ", selectors));
    }

    private void clickSubmit(Locator scope) {
        Locator submit = scope.locator("button[type='submit']");
        if (submit.count() > 0) { submit.first().click(); return; }
        scope.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                        .setName(java.util.regex.Pattern.compile("Add|Save|Create", java.util.regex.Pattern.CASE_INSENSITIVE)))
                .first().click();
    }
}