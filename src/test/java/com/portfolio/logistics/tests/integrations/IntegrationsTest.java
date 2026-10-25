package com.portfolio.logistics.tests.integrations;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.AppNavigator;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 10 — Integrations")
public class IntegrationsTest extends BaseTest {

    private AppNavigator login() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);
        return new AppNavigator(page);
    }

    @Test(description = "10.1 QuickBooks setup + sub-config pages (chart of accounts, tax codes, income accounts, product/service, error logs) render")
    @Severity(SeverityLevel.NORMAL)
    public void quickbooksPages() {
        List<String> f = login().firstFailures(
                "/integrations/quickbooks", "QuickBooks Setup",
                "/integrations/chart-of-accounts", "Chart of Accounts",
                "/integrations/tax-codes", "Tax Codes",
                "/integrations/income-accounts", "Income Accounts",
                "/integrations/product-service-list", "Products & Services",
                "/integrations/qb-error-logs", "QB Sync Error Logs");
        assertTrue(f.isEmpty(), "QuickBooks integration pages that did not render: " + f);
    }

    @Test(description = "10.2 IFTA pages (report, mileage, tax rates, filed) render")
    @Severity(SeverityLevel.NORMAL)
    public void iftaPages() {
        List<String> f = login().firstFailures(
                "/integrations/ifta", "IFTA Reporting",
                "/integrations/truck-mileage", "Truck Mileage by Jurisdiction",
                "/integrations/state-mileage", "State / Province Mileage",
                "/integrations/ifta-tax-rates", "IFTA Tax Rates",
                "/integrations/ifta-report", "IFTA Quarterly Report",
                "/integrations/filed-ifta", "Filed IFTA Reports");
        assertTrue(f.isEmpty(), "IFTA integration pages that did not render: " + f);
    }

    @Test(description = "10.3 ELD / telematics pages render")
    @Severity(SeverityLevel.NORMAL)
    public void eldPages() {
        List<String> f = login().firstFailures(
                "/integrations/eld", "ELD Management",
                "/integrations/device-tracking", "Device & GPS Tracking");
        assertTrue(f.isEmpty(), "ELD integration pages that did not render: " + f);
    }

    @Test(description = "10.4 EDI pages (config, api, exchange) render")
    @Severity(SeverityLevel.NORMAL)
    public void ediPages() {
        List<String> f = login().firstFailures(
                "/integrations/edi-config", "EDI Configuration",
                "/integrations/edi-api", "EDI API Endpoint Configuration",
                "/integrations/edi-exchange", "EDI Exchange");
        assertTrue(f.isEmpty(), "EDI integration pages that did not render: " + f);
    }

    @Test(description = "10.5 Carrier API / cards pages render")
    @Severity(SeverityLevel.NORMAL)
    public void carrierApiPages() {
        List<String> f = login().firstFailures(
                "/integrations/carrier-api", "Carrier API Integrations",
                "/integrations/avaal-bluecard", "TransIQ Blue Card",
                "/integrations/cards-detail", "Fuel Cards Detail");
        assertTrue(f.isEmpty(), "Carrier API / cards pages that did not render: " + f);
    }
}
