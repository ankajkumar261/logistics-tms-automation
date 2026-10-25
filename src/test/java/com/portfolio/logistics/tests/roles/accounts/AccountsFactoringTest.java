package com.portfolio.logistics.tests.roles.accounts;

import com.portfolio.logistics.model.Role;
import com.portfolio.logistics.pages.FactoringPage;
import com.portfolio.logistics.tests.roles.RoleTestBase;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Roles — ACCOUNTS")
public class AccountsFactoringTest extends RoleTestBase {

    @Test(description = "ACCOUNTS can add a Factoring Company and find it")
    @Severity(SeverityLevel.NORMAL)
    public void addFactoringCompany() {
        signIn(Role.ACCOUNTS);
        FactoringPage factoring = new FactoringPage(page);
        factoring.open();
        String name = "QA-Factor-" + (System.currentTimeMillis() % 1_000_000L);
        assertTrue(factoring.addCompany(name, "90", "3", "5"),
                "ACCOUNTS should be able to add factoring company '" + name + "'.");
        assertTrue(factoring.search(name) >= 1,
                "Factoring company '" + name + "' should be findable after adding.");
    }
}
