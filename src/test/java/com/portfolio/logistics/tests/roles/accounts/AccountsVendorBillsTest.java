package com.portfolio.logistics.tests.roles.accounts;

import com.portfolio.logistics.model.Role;
import com.portfolio.logistics.pages.VendorBillsPage;
import com.portfolio.logistics.tests.roles.RoleTestBase;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Roles — ACCOUNTS")
public class AccountsVendorBillsTest extends RoleTestBase {

    @Test(description = "ACCOUNTS can create a Vendor Bill and see it in the list")
    @Severity(SeverityLevel.CRITICAL)
    public void createVendorBill() {
        signIn(Role.ACCOUNTS);
        long ts = System.currentTimeMillis() % 1_000_000L;
        String billNo = "QA-VB-" + ts;
        VendorBillsPage bills = new VendorBillsPage(page);
        assertTrue(bills.createBill(billNo, "QA-Vendor-" + ts, "1250.00", "2026-12-31", "2027-01-30"),
                "ACCOUNTS should be able to create vendor bill " + billNo + ".");
        bills.open();
        assertTrue(bills.filterByBillNo(billNo) >= 1 && bills.tableContains(billNo),
                "Vendor bill " + billNo + " should appear in the list.");
    }

    @Test(description = "ACCOUNTS can use the Vendor Bills list (count, filter, reset)")
    @Severity(SeverityLevel.NORMAL)
    public void vendorBillsList() {
        signIn(Role.ACCOUNTS);
        VendorBillsPage bills = new VendorBillsPage(page);
        bills.open();
        assertTrue(bills.totalResults() > 0, "ACCOUNTS should see vendor bills in the list.");
        String firstNo = bills.firstBillNo();
        assertTrue(!firstNo.isBlank(), "The first vendor-bill row should show a bill number.");
        assertTrue(bills.filterByBillNo(firstNo) >= 1 && bills.tableContains(firstNo),
                "Filtering by " + firstNo + " should keep it in the list.");
        bills.reset();
    }
}
