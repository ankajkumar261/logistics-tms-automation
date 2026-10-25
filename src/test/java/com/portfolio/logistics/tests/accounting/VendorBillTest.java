package com.portfolio.logistics.tests.accounting;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.VendorBillsPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 7 — Accounting: Vendor Bills")
public class VendorBillTest extends BaseTest {

    @Test(description = "7.1 Create vendor bill: a bill is created and appears in the list")
    @Severity(SeverityLevel.CRITICAL)
    public void createVendorBill() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        long ts = System.currentTimeMillis() % 1_000_000L;
        VendorBillsPage bills = new VendorBillsPage(page);
        String billNo = "QA-VB-" + ts;
        boolean created = bills.createBill(billNo, "QA-Vendor-" + ts, "1250.00", "2026-12-31", "2027-01-30");
        assertTrue(created, "Expected vendor bill " + billNo + " to be created (check console for validation).");

        bills.open();
        int filtered = bills.filterByBillNo(billNo);
        assertTrue(filtered >= 1 && bills.tableContains(billNo),
                "Expected vendor bill " + billNo + " to appear in the bills list.");
    }
}
