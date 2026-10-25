package com.portfolio.logistics.tests.roles;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Role;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;

@Epic("Logistics TMS TMS")
@Feature("Module 1 — Auth & Roles")
public class RoleLoginTest extends BaseTest {

    @DataProvider(name = "roles")
    public Object[][] roles() {
        return new Object[][]{
                {Role.SUPERADMIN}, {Role.ADMIN}, {Role.DISPATCHER},
                {Role.DRIVER}, {Role.ACCOUNTS}, {Role.REPORTS}
        };
    }

    @Test(dataProvider = "roles",
            description = "1.3 Each role can sign in with its configured credentials")
    @Severity(SeverityLevel.CRITICAL)
    public void roleCanLogIn(Role role) {
        LoginPage login = new LoginPage(page).open();
        login.loginAs(role);

        // Success == the app leaves the /login page.
        try {
            page.waitForURL(u -> !u.contains("/login"),
                    new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(15000));
        } catch (Exception ignored) {
            // stayed on /login -> assertion below fails with a clear message
        }

        assertFalse(login.isOnLoginPage(),
                role + " should sign in with the configured credentials, but stayed on /login "
                        + "(no error shown). Verify the username/password for this role.");
    }
}
