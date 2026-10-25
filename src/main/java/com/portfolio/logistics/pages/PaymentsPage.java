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
 * Module 5.5 — Payment history (/accounting/payments) + the Record Payment dialog (5.4).
 * Columns: Date, Type, Reference, Counterparty, Related To, Method, Amount.
 * Dialog required fields: Payment Number, Amount, Counterparty, Payment Date
 * (Type=Received(AR), Method=EFT, Currency=CAD default). Invoice ID links the payment to an invoice.
 */
public class PaymentsPage {

    private final Page page;

    public PaymentsPage(Page page) {
        this.page = page;
    }

    /** Navigate, retrying once if post-login SPA routing ERR_ABORTED the first attempt. */
    private void navigateWithRetry(String url) {
        try {
            page.navigate(url);
        } catch (Exception e) {
            page.waitForTimeout(1500);
            page.navigate(url);
        }
    }

    public void open() {
        navigateWithRetry(ConfigReader.baseUrl() + "/accounting/payments");
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

    public int search(String term) {
        Locator box = page.locator("input[placeholder='Search payments...'], input[placeholder*='Search' i]").first();
        box.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        box.fill(term);
        page.waitForTimeout(1500);
        return rowCount();
    }

    public void reset() {
        Locator reset = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^Reset$")));
        if (reset.count() > 0) { reset.first().click(); page.waitForTimeout(1200); }
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
     * Record a payment via the dialog. Fills the required fields (Type/Method/Currency keep their
     * defaults Received-AR / EFT / CAD); optionally links an invoice by its internal id.
     * Returns true if the dialog closed (payment saved).
     */
    public boolean recordPayment(String paymentNo, String amount, String counterparty,
                                 String dateYyyyMmDd, String invoiceIdOrNull) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("Record Payment", Pattern.CASE_INSENSITIVE))).first().click();

        Locator dlg = page.locator("[role='dialog']").first();
        dlg.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        page.waitForTimeout(500);

        dlg.locator("input[name='paymentNumber']").first().fill(paymentNo);
        dlg.locator("input[name='amount']").first().fill(amount);
        dlg.locator("input[name='counterparty']").first().fill(counterparty);
        dlg.locator("input[name='paymentDate']").first().fill(dateYyyyMmDd);
        if (invoiceIdOrNull != null && !invoiceIdOrNull.isBlank()) {
            Locator inv = dlg.locator("input[name='invoiceId']");
            if (inv.count() > 0) inv.first().fill(invoiceIdOrNull);
        }

        dlg.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^Record Payment$", Pattern.CASE_INSENSITIVE))).last().click();

        try {
            dlg.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(10000));
            page.waitForTimeout(1200);
            return true;
        } catch (Exception e) {
            java.util.List<String> errs = dlg.locator("[role='alert'], .text-destructive").allInnerTexts();
            System.out.println("[PaymentsPage] Record Payment blocked. Validation: " + errs);
            return false;
        }
    }
}
