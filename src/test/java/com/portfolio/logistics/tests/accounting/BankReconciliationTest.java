package com.portfolio.logistics.tests.accounting;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.BankReconciliationPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 8 — Accounting: Bank Reconciliation")
public class BankReconciliationTest extends BaseTest {

    @Test(description = "8.1 Bank reconciliation: create a bank account (statement CSV import + matching is data-driven, not exercised)")
    @Severity(SeverityLevel.NORMAL)
    public void createBankAccount() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        BankReconciliationPage bank = new BankReconciliationPage(page);
        bank.open();

        // 8.1 statement loading affordance is present.
        assertTrue(bank.hasImportCsv(), "Expected an 'Import CSV' affordance for loading a bank statement (8.1).");

        long ts = System.currentTimeMillis() % 1_000_000L;
        String ref = "QA-Bank-" + ts;
        boolean created = bank.createBankAccount(ref, "QA Test Bank");
        assertTrue(created, "Expected bank account '" + ref + "' to be created (check console for validation).");

        assertTrue(bank.pageContains(ref) || bank.pageContains("QA Test Bank"),
                "Expected the new bank account '" + ref + "' to appear after creation.");
    }
}
