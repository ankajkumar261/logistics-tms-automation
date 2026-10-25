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
public class VendorBillListTest extends BaseTest {

    @Test(description = "7.2 Vendor bills list: totals, filter by Bill#, and search work")
    @Severity(SeverityLevel.NORMAL)
    public void vendorBillsList() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        VendorBillsPage bills = new VendorBillsPage(page);
        bills.open();

        int total = bills.totalResults();
        assertTrue(total > 0, "Expected a positive vendor-bill count, got " + total);

        String firstNo = bills.firstBillNo();
        assertTrue(!firstNo.isBlank(), "Expected the first row to show a bill number");

        int filtered = bills.filterByBillNo(firstNo);
        assertTrue(filtered >= 1, "Filtering by " + firstNo + " should return at least 1 row, got " + filtered);
        assertTrue(bills.tableContains(firstNo), "Filtered list should still contain " + firstNo);
        bills.reset();
    }
}
