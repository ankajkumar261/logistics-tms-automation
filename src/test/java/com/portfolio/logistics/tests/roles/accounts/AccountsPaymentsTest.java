package com.portfolio.logistics.tests.roles.accounts;

import com.portfolio.logistics.model.Role;
import com.portfolio.logistics.pages.InvoiceDetailPage;
import com.portfolio.logistics.pages.InvoicesPage;
import com.portfolio.logistics.pages.PaymentsPage;
import com.portfolio.logistics.tests.roles.RoleTestBase;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Roles — ACCOUNTS")
public class AccountsPaymentsTest extends RoleTestBase {

    @Test(description = "ACCOUNTS can view Payment History (count, search)")
    @Severity(SeverityLevel.NORMAL)
    public void paymentHistory() {
        signIn(Role.ACCOUNTS);
        PaymentsPage payments = new PaymentsPage(page);
        payments.open();
        assertTrue(payments.totalResults() > 0, "ACCOUNTS should see payments in the history.");
        assertTrue(payments.rowCount() > 0, "ACCOUNTS should see at least one payment row.");
    }

    @Test(description = "ACCOUNTS can record a payment against a Sent invoice")
    @Severity(SeverityLevel.CRITICAL)
    public void recordPayment() {
        signIn(Role.ACCOUNTS);
        long ts = System.currentTimeMillis() % 1_000_000L;
        String invNo = "QA-INV-" + ts;
        String payNo = "QA-PAY-" + ts;
        String payer = "QA-Payer-" + ts;

        InvoicesPage invoices = new InvoicesPage(page);
        assertTrue(invoices.createDraftInvoice(invNo, "1200.00", "2026-12-31"),
                "Precondition: ACCOUNTS creates invoice " + invNo + ".");

        InvoiceDetailPage detail = new InvoiceDetailPage(page);
        detail.open(invNo);
        String invoiceId = detail.invoiceId();
        detail.markAsSent(); // payable once sent

        PaymentsPage payments = new PaymentsPage(page);
        payments.open();
        assertTrue(payments.recordPayment(payNo, "1200.00", payer, "2026-12-31", invoiceId),
                "ACCOUNTS should be able to record payment " + payNo + ".");
        payments.open();
        assertTrue(payments.tableContains(payer) || payments.tableContains(payNo),
                "Recorded payment " + payNo + " should appear in Payment History.");
    }
}
