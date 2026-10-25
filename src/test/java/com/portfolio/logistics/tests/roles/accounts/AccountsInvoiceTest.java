package com.portfolio.logistics.tests.roles.accounts;

import com.portfolio.logistics.model.Role;
import com.portfolio.logistics.pages.InvoiceDetailPage;
import com.portfolio.logistics.pages.InvoicesPage;
import com.portfolio.logistics.tests.roles.RoleTestBase;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Roles — ACCOUNTS")
public class AccountsInvoiceTest extends RoleTestBase {

    @Test(description = "ACCOUNTS can create a Draft invoice and see it as Draft")
    @Severity(SeverityLevel.CRITICAL)
    public void createInvoice() {
        signIn(Role.ACCOUNTS);
        InvoicesPage invoices = new InvoicesPage(page);
        String invNo = "QA-INV-" + (System.currentTimeMillis() % 1_000_000L);
        assertTrue(invoices.createDraftInvoice(invNo, "500.00", "2026-12-31"),
                "ACCOUNTS should be able to create invoice " + invNo + ".");
        assertTrue(new InvoiceDetailPage(page).listShowsStatus(invNo, "Draft"),
                "ACCOUNTS's new invoice " + invNo + " should appear as Draft.");
    }

    @Test(description = "ACCOUNTS can use the Invoices list (count, filter, search, reset)")
    @Severity(SeverityLevel.NORMAL)
    public void invoiceListOperations() {
        signIn(Role.ACCOUNTS);
        InvoicesPage invoices = new InvoicesPage(page);
        invoices.open();
        assertTrue(invoices.totalResults() > 0, "ACCOUNTS should see invoices in the list.");
        String firstNo = invoices.firstInvoiceNo();
        assertTrue(!firstNo.isBlank(), "The first invoice row should show an invoice number.");
        assertTrue(invoices.filterByInvoiceNo(firstNo) >= 1 && invoices.tableContains(firstNo),
                "Filtering by " + firstNo + " should keep it in the list.");
        invoices.reset();
    }
}
