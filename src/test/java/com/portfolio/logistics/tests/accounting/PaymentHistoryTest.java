package com.portfolio.logistics.tests.accounting;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.PaymentsPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 5 — Accounting: Invoices")
public class PaymentHistoryTest extends BaseTest {

    @Test(description = "5.5 Payment history: the payments list loads and is searchable")
    @Severity(SeverityLevel.NORMAL)
    public void paymentHistory() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000); // let the SPA settle post-login before deep-linking

        PaymentsPage payments = new PaymentsPage(page);
        payments.open();

        int total = payments.totalResults();
        assertTrue(total > 0, "Expected a positive payment count in history, got " + total);
        assertTrue(payments.rowCount() > 0, "Expected at least one payment row");

        // Search by the first row's reference token narrows to >= 1 row.
        String firstRef = page.locator("table tbody tr").first().locator("td").nth(2).innerText().trim();
        if (!firstRef.isBlank()) {
            int searched = payments.search(firstRef);
            assertTrue(searched >= 1, "Search for '" + firstRef + "' should return at least 1 row, got " + searched);
        }
    }
}
