package com.portfolio.logistics.tests.admin;

import com.microsoft.playwright.Locator;
import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.pages.DialogAdder;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 12 — Admin")
public class AdminManagementTest extends BaseTest {

    private DialogAdder login() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);
        return new DialogAdder(page);
    }

    private long ts() { return System.currentTimeMillis() % 1_000_000L; }

    @Test(description = "12.1 User Management: add a user")
    @Severity(SeverityLevel.CRITICAL)
    public void addUser() {
        DialogAdder a = login();
        a.open("/admin"); // /admin IS the User Management page
        long t = ts();
        String username = "qauser" + t;
        Locator dlg = a.openDialog("Add User");
        a.fill(dlg, "TIQ001", ConfigReader.corporateId()); // Corporate ID* (the existing tenant)
        a.fill(dlg, "John", "QAFirst");                    // First Name
        a.fill(dlg, "Smith", "QALast" + t);                // Last Name
        a.fill(dlg, "username", username);                 // Username
        a.fill(dlg, "user@example.test", username + "@qa.test"); // Email (optional)
        a.fill(dlg, "Min. 8", "QATest@1234");              // Password
        a.fill(dlg, "Repeat password", "QATest@1234");     // Confirm Password
        assertTrue(a.submit(dlg, "Create User"), "Expected to add user '" + username + "'.");
    }

    @Test(description = "12.2 Tenants: add a company")
    @Severity(SeverityLevel.NORMAL)
    public void addCompany() {
        DialogAdder a = login();
        a.open("/admin/tenants");
        long t = ts();
        String name = "QA Company " + t;
        Locator dlg = a.openDialog("Add Company");
        a.fill(dlg, "AFN", "AFN" + (t % 100000));                  // Corporate ID* (new, unique)
        a.fill(dlg, "ABC Freight", name);                          // Company Name
        a.fill(dlg, "acme", "qaco" + t);                           // Subdomain
        a.fill(dlg, "admin@example.test", "qaco" + t + "@qa.test"); // Admin Email
        assertTrue(a.submit(dlg, "Create Company"), "Expected to add company '" + name + "'.");
    }
}
