package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SelectOption;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Module 5 — Accounting / Invoices (AR). Covers the list at /accounting/invoices
 * (search, filter, reset, pagination) and the create form at /accounting/invoices/new.
 *
 * The create form's required fields are Amount, Currency (default CAD), Due Date and
 * Status (default Draft). Customer / Linked Order / Factoring are optional native <select>s.
 */
public class InvoicesPage {

    private final Page page;

    /** The Customer that createDraftInvoice() linked (so tests can assert the exported PDF's "Billed To"). */
    private String linkedCustomer;

    public InvoicesPage(Page page) {
        this.page = page;
    }

    public String getLinkedCustomer() { return linkedCustomer; }

    /** Navigate, retrying once if the post-login SPA routing ERR_ABORTED the first attempt. */
    private void navigateWithRetry(String url) {
        try {
            page.navigate(url);
        } catch (Exception e) {
            page.waitForTimeout(1500);
            page.navigate(url);
        }
    }

    /** Open the invoices list. */
    public void open() {
        navigateWithRetry(ConfigReader.baseUrl() + "/accounting/invoices");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("table tbody tr").first().waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
    }

    public int rowCount() { return page.locator("table tbody tr").count(); }

    public String firstInvoiceNo() {
        return page.locator("table tbody tr").first().locator("td").first().innerText().trim();
    }

    /** Read the "Showing X to Y of N results" total, or -1. */
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
        Locator box = page.locator("input[placeholder='Search invoices...'], input[placeholder*='Search' i]").first();
        box.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        box.fill(term);
        page.waitForTimeout(1500);
        return rowCount();
    }

    public int filterByInvoiceNo(String invoiceNo) {
        page.locator("input[placeholder='Invoice No']").first().fill(invoiceNo);
        clickSearch();
        return rowCount();
    }

    public int filterByOrderNo(String orderNo) {
        page.locator("input[placeholder='Order No']").first().fill(orderNo);
        clickSearch();
        return rowCount();
    }

    private void clickSearch() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Search$"))).first().click();
        page.waitForTimeout(1500);
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

    /**
     * Create a Draft invoice via /accounting/invoices/new.
     * Fills the given invoice number, amount and due date; leaves Currency=CAD, Status=Draft.
     * Best-effort links the first available Customer. On success the app toasts
     * "Invoice created successfully" and returns to the LIST (not a detail page).
     * Returns true if the invoice was created.
     */
    public boolean createDraftInvoice(String invoiceNo, String amount, String dueDateYyyyMmDd) {
        navigateWithRetry(ConfigReader.baseUrl() + "/accounting/invoices/new");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("input[name='amount']").first().waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));

        page.locator("input[name='invoiceNumber']").first().fill(invoiceNo);
        page.locator("input[name='amount']").first().fill(amount);
        page.locator("input[name='dueDate']").first().fill(dueDateYyyyMmDd);

        // Best-effort: pick the first real Customer option (native <select>; index 0 is the placeholder).
        selectFirstRealOption("No customer");
        // Factoring Company is a required field on this form — select the first real option.
        selectFactoringCompany();

        if (submitInvoice()) return true;

        java.util.List<String> errs = page.locator("[role='alert'], .text-destructive, [data-sonner-toast]").allInnerTexts();
        System.out.println("[InvoicesPage] Create Invoice did not complete (still on the form). Messages: " + errs);
        return false;
    }

    /** Select the (required) Factoring Company combobox's first real option, when the field exists. */
    private void selectFactoringCompany() {
        Locator combo = page.locator("button[role='combobox']")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("factoring", Pattern.CASE_INSENSITIVE)))
                .first();
        if (combo.count() == 0) return; // not present on environments where it is not required
        combo.scrollIntoViewIfNeeded();
        combo.click();
        page.waitForTimeout(500);
        Locator opt = page.getByRole(AriaRole.OPTION).first();
        try {
            opt.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(6000));
            opt.click();
            page.waitForTimeout(300);
        } catch (Exception e) {
            try { page.keyboard().press("Escape"); } catch (Exception ignored) { }
            System.out.println("[InvoicesPage] Factoring Company combobox offered no option to select.");
        }
    }

    /** Click Create Invoice; true when the app leaves the /new form (the invoice was created). */
    private boolean submitInvoice() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Create Invoice$", Pattern.CASE_INSENSITIVE))).first().click();
        try {
            page.waitForURL(u -> u.contains("/accounting/invoices") && !u.endsWith("/new"),
                    new Page.WaitForURLOptions().setTimeout(15000));
            page.waitForTimeout(800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Select the first non-placeholder option of the native <select> whose placeholder text matches. */
    private void selectFirstRealOption(String placeholderText) {
        try {
            Locator sel = page.locator("select").filter(
                    new Locator.FilterOptions().setHasText(Pattern.compile(placeholderText, Pattern.CASE_INSENSITIVE))).first();
            if (sel.count() == 0) return;
            Object optCount = sel.evaluate("el => el.options.length");
            if (optCount instanceof Number && ((Number) optCount).intValue() > 1) {
                sel.selectOption(new SelectOption().setIndex(1));
                page.waitForTimeout(400);
                Object txt = sel.evaluate("el => el.options[el.selectedIndex] ? el.options[el.selectedIndex].text : ''");
                if (txt != null && !txt.toString().isBlank()) linkedCustomer = txt.toString().trim();
            }
        } catch (Exception e) {
            System.out.println("[InvoicesPage] Could not select a '" + placeholderText + "' option (optional): "
                    + e.getMessage().split("\n")[0]);
        }
    }
}
