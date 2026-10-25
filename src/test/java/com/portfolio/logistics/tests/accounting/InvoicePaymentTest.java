package com.portfolio.logistics.tests.accounting;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.InvoiceDetailPage;
import com.portfolio.logistics.pages.InvoicesPage;
import com.portfolio.logistics.pages.LoginPage;
import com.portfolio.logistics.pages.PaymentsPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 5 — Accounting: Invoices")
public class InvoicePaymentTest extends BaseTest {

    @Test(description = "5.4 Record payment: a payment is recorded and appears in payment history")
    @Severity(SeverityLevel.CRITICAL)
    public void recordPayment() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000); // let the SPA settle post-login before deep-linking

        long ts = System.currentTimeMillis() % 1_000_000L;

        // Self-contained: create + finalize an invoice to pay against.
        InvoicesPage invoices = new InvoicesPage(page);
        String invNo = "QA-INV-" + ts;
        assertTrue(invoices.createDraftInvoice(invNo, "1200.00", "2026-12-31"),
                "Precondition: an invoice must be created first for " + invNo + ".");

        // Open the created invoice's detail, note its internal id, and finalize it.
        InvoiceDetailPage detail = new InvoiceDetailPage(page);
        detail.open(invNo);
        String invoiceId = detail.invoiceId();
        detail.markAsSent(); // an invoice is normally payable once sent

        // Record a payment (links to the invoice by its internal id).
        PaymentsPage payments = new PaymentsPage(page);
        payments.open();
        String payNo = "QA-PMT-" + ts;
        String payer = "QA-Payer-" + ts;
        boolean recorded = payments.recordPayment(payNo, "1200.00", payer, "2026-12-31", invoiceId);
        assertTrue(recorded, "Expected payment " + payNo + " to be recorded (check console for validation).");

        // It shows up in payment history.
        payments.open();
        assertTrue(payments.tableContains(payer) || payments.tableContains(payNo),
                "Expected recorded payment (" + payNo + " / " + payer + ") to appear in payment history.");
    }
}
