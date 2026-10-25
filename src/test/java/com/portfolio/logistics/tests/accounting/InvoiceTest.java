package com.portfolio.logistics.tests.accounting;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.InvoiceDetailPage;
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
public class InvoiceTest extends BaseTest {

    private String freshInvoiceNo() {
        return "QA-INV-" + (System.currentTimeMillis() % 1_000_000L);
    }

    @Test(description = "5.1 Create invoice: a new Draft invoice is created and appears in the list")
    @Severity(SeverityLevel.CRITICAL)
    public void createInvoice() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000); // let the SPA settle post-login before deep-linking

        InvoicesPage invoices = new InvoicesPage(page);
        String invNo = freshInvoiceNo();
        boolean created = invoices.createDraftInvoice(invNo, "500.00", "2026-12-31");
        assertTrue(created, "Expected 'Create Invoice' to create " + invNo
                + " (check console for validation messages).");

        InvoiceDetailPage detail = new InvoiceDetailPage(page);
        assertTrue(detail.listShowsStatus(invNo, "Draft"),
                "Expected new invoice " + invNo + " to appear in the list with status Draft.");
    }

    @Test(description = "5.3 Finalize/send invoice: a Draft invoice is marked Sent and is downloadable as PDF")
    @Severity(SeverityLevel.NORMAL)
    public void finalizeAndSendInvoice() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000); // let the SPA settle post-login before deep-linking

        // Self-contained: create a fresh Draft, then finalize it.
        InvoicesPage invoices = new InvoicesPage(page);
        String invNo = freshInvoiceNo();
        assertTrue(invoices.createDraftInvoice(invNo, "750.00", "2026-12-31"),
                "Precondition: a Draft invoice must be created first for " + invNo + ".");

        // Create returns to the list, so open the invoice's detail page.
        InvoiceDetailPage detail = new InvoiceDetailPage(page);
        detail.open(invNo);
        assertTrue(detail.hasDownloadButton(),
                "Expected a Download (PDF) action on the invoice detail for " + invNo + ".");
        assertTrue(detail.markAsSent(),
                "Expected a 'Mark as Sent' action on the Draft invoice " + invNo + ".");
        assertTrue(detail.listShowsStatus(invNo, "Sent"),
                "Expected invoice " + invNo + " to show status Sent after finalizing.");
    }
}
