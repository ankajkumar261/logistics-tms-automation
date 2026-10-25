package com.portfolio.logistics.tests.accounting;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.FactoringPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 9 — Accounting: Factoring")
public class FactoringTest extends BaseTest {

    @Test(description = "9.1 Factoring companies: add a company and verify it is listed")
    @Severity(SeverityLevel.NORMAL)
    public void addFactoringCompany() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        FactoringPage factoring = new FactoringPage(page);
        factoring.open();

        String name = "QA-Factor-" + (System.currentTimeMillis() % 1_000_000L);
        boolean added = factoring.addCompany(name, "90", "3", "5");
        assertTrue(added, "Expected factoring company '" + name + "' to be added (check console for validation).");

        assertTrue(factoring.search(name) >= 1,
                "Expected factoring company '" + name + "' to appear in the list after adding.");
    }
}
