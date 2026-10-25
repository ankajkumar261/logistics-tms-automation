package com.portfolio.logistics.tests.accounting;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.InvoicesPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 5 — Accounting: Invoices")
public class InvoiceListTest extends BaseTest {

    @Test(description = "5.2 Invoice list: totals, filter by Invoice#, and search work")
    @Severity(SeverityLevel.NORMAL)
    public void invoiceListOperations() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        InvoicesPage invoices = new InvoicesPage(page);
        invoices.open();

        int total = invoices.totalResults();
        assertTrue(total > 0, "Expected a positive invoice count, got " + total);

        String firstNo = invoices.firstInvoiceNo();
        assertTrue(!firstNo.isBlank(), "Expected the first row to show an invoice number");

        int filtered = invoices.filterByInvoiceNo(firstNo);
        assertTrue(filtered >= 1, "Filtering by " + firstNo + " should return at least 1 row, got " + filtered);
        assertTrue(invoices.tableContains(firstNo),
                "Filtered list should still contain " + firstNo);
        invoices.reset();

        int searched = invoices.search(firstNo);
        assertTrue(searched >= 1, "Search for " + firstNo + " should return at least 1 row, got " + searched);
    }
}
