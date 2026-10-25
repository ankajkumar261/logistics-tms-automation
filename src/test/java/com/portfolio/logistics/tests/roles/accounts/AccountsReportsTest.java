package com.portfolio.logistics.tests.roles.accounts;

import com.portfolio.logistics.model.Role;
import com.portfolio.logistics.pages.AppNavigator;
import com.portfolio.logistics.tests.roles.RoleTestBase;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Roles — ACCOUNTS")
public class AccountsReportsTest extends RoleTestBase {

    @Test(description = "ACCOUNTS can open its reports (they render with data)")
    @Severity(SeverityLevel.NORMAL)
    public void reportsRender() {
        signIn(Role.ACCOUNTS);

        List<String> failures = new AppNavigator(page).firstFailures(
                "/reports/sales", "Sales Report",
                "/reports/aging", "Aging Invoices",
                "/reports/outstanding", "Outstanding Report");

        assertTrue(failures.isEmpty(), "Reports that did not render for ACCOUNTS: " + failures);
    }
}
