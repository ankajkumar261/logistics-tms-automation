package com.portfolio.logistics.tests.cpanel;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.LookupEntry;
import com.portfolio.logistics.pages.ExpenseTypesPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 2 — Master Data")
public class ExpenseTypeTest extends BaseTest {

    @Test(description = "2.7a Expense Types: add an entry and verify it appears")
    @Severity(SeverityLevel.NORMAL)
    public void addExpenseType() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        ExpenseTypesPage expense = new ExpenseTypesPage(page);
        expense.open();

        LookupEntry e = LookupEntry.sample("QA-Expense");
        expense.addEntry(e);

        assertTrue(expense.isEntryPresent(e.getName()),
                "Expected expense type '" + e.getName() + "' to appear");
    }
}