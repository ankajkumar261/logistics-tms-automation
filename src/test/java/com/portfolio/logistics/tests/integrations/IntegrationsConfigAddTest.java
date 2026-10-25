package com.portfolio.logistics.tests.integrations;

import com.microsoft.playwright.Locator;
import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.DialogAdder;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 10 — Integrations (config adds)")
public class IntegrationsConfigAddTest extends BaseTest {

    private DialogAdder login() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);
        return new DialogAdder(page);
    }

    private long ts() { return System.currentTimeMillis() % 1_000_000L; }

    @Test(description = "10.1 Chart of Accounts: add an account")
    @Severity(SeverityLevel.NORMAL)
    public void addChartOfAccount() {
        DialogAdder a = login();
        a.open("/integrations/chart-of-accounts");
        String name = "QA-Acct-" + ts();
        Locator dlg = a.openDialog("Add Account");
        a.fill(dlg, "Toll Expense", name);   // Account Name (Account Type defaults to Expense)
        assertTrue(a.submit(dlg, "Add Account"), "Expected to add chart-of-accounts entry '" + name + "'.");
    }

    @Test(description = "10.1 Tax Codes: add a tax code")
    @Severity(SeverityLevel.NORMAL)
    public void addTaxCode() {
        DialogAdder a = login();
        a.open("/integrations/tax-codes");
        long t = ts();
        String code = "QA" + (t % 10000);
        Locator dlg = a.openDialog("Add Tax Code");
        a.fill(dlg, "HST-NB", code);              // Tax Code
        a.fill(dlg, "Harmonized", "QA Tax " + t); // Name
        a.fillNumber(dlg, "5");                   // Rate (%)
        a.fill(dlg, "New Brunswick", "Ontario, CA"); // Region (silently required)
        a.pickFirstOption(dlg, 0);                // Country (its default display isn't a real value)
        assertTrue(a.submit(dlg, "Add Tax Code"), "Expected to add tax code '" + code + "'.");
    }

    @Test(description = "10.1 Income Accounts: add an income source")
    @Severity(SeverityLevel.NORMAL)
    public void addIncomeSource() {
        DialogAdder a = login();
        a.open("/integrations/income-accounts");
        String name = "QA-Income-" + ts();
        Locator dlg = a.openDialog("Add Income Source");
        a.fill(dlg, "Oversize Permit", name);  // Income Source Name
        a.fillNumber(dlg, "1000");             // YTD Amount
        assertTrue(a.submit(dlg, "Add Source"), "Expected to add income source '" + name + "'.");
    }

    @Test(description = "10.1 Product/Service list: add an item")
    @Severity(SeverityLevel.NORMAL)
    public void addProductServiceItem() {
        DialogAdder a = login();
        a.open("/integrations/product-service-list");
        String name = "QA-Item-" + ts();
        Locator dlg = a.openDialog("Add Item");
        a.fill(dlg, "Stop-off Charge", name);  // Item Name (Type defaults to Service)
        a.fillNumber(dlg, "50");               // Default Rate
        assertTrue(a.submit(dlg, "Add Item"), "Expected to add product/service item '" + name + "'.");
    }

    @Test(description = "10.5 Carrier API: add a carrier integration")
    @Severity(SeverityLevel.NORMAL)
    public void addCarrierApi() {
        DialogAdder a = login();
        a.open("/integrations/carrier-api");
        long t = ts();
        String name = "QA-Carrier-" + t;
        Locator dlg = a.openDialog("Add Carrier");
        a.fill(dlg, "Saia LTL", name);                            // Carrier Name
        a.fill(dlg, "api.carrier.com", "https://api.example.test" + t + ".com/v1"); // API Endpoint
        assertTrue(a.submit(dlg, "Add Carrier"), "Expected to add carrier integration '" + name + "'.");
    }

    @Test(description = "10.4 EDI: add an EDI trading partner")
    @Severity(SeverityLevel.NORMAL)
    public void addEdiPartner() {
        DialogAdder a = login();
        a.open("/integrations/edi-config");
        long t = ts();
        String name = "QA-EDI-" + t;
        Locator dlg = a.openDialog("Add EDI Partner");
        a.fill(dlg, "Kroger", name);              // Partner Name
        a.fill(dlg, "ISA ID", "QAISA" + (t % 10000)); // ISA Sender ID
        assertTrue(a.submit(dlg, "Add Partner"), "Expected to add EDI partner '" + name + "'.");
    }

    @Test(description = "10.3 Device & GPS Tracking: add a telematics provider")
    @Severity(SeverityLevel.NORMAL)
    public void addDeviceProvider() {
        DialogAdder a = login();
        a.open("/integrations/device-tracking");
        long t = ts();
        String name = "QA-Provider-" + t;
        Locator dlg = a.openDialog("Add Provider");
        a.fill(dlg, "Omnitracs", name);      // Provider Name
        a.fill(dlg, "API Key", "QAKEY" + t); // API Key (password field)
        assertTrue(a.submit(dlg, "Add Provider"), "Expected to add device provider '" + name + "'.");
    }

    @Test(description = "10.5 Fuel Cards: add a card and verify it appears in the list")
    @Severity(SeverityLevel.NORMAL)
    public void addFuelCard() {
        DialogAdder a = login();
        a.open("/integrations/cards-detail");
        String last4 = String.format("%04d", ts() % 10000);
        Locator dlg = a.openDialog("Add Card");
        a.fill(dlg, "5510", last4);  // Card Number (last 4 digits)
        a.fillNumber(dlg, "5000");   // Credit Limit ($)
        a.submit(dlg, "Add Card");   // the dialog closing is NOT proof the card was saved

        // Verify the card actually shows up in the Fuel Cards list — reload first so
        // we read persisted state, not optimistic client state. If it's missing, the
        // add did not work and this test must fail (not silently pass on a closed dialog).
        a.open("/integrations/cards-detail");
        assertTrue(a.pageContains(last4),
                "Added fuel card ending '" + last4 + "' should appear in the Fuel Cards list, "
                        + "but it did not — the add did not persist.");
    }
}
