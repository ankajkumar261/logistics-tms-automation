package com.portfolio.logistics.tests.admin;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.AppNavigator;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 12 — Admin")
public class AdminTest extends BaseTest {

    private AppNavigator login() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);
        return new AppNavigator(page);
    }

    @Test(description = "12.1/12.3/12.4 Admin panel, audit log and import pages render")
    @Severity(SeverityLevel.NORMAL)
    public void adminCorePages() {
        List<String> f = login().firstFailures(
                "/admin", "Admin Panel",
                "/admin/audit-log", "Audit Log",
                "/admin/import", "Import Data");
        assertTrue(f.isEmpty(), "Admin pages that did not render: " + f);
        // 12.3 the audit log must actually list entries — "|| true" here made this always pass.
        new AppNavigator(page).renders("/admin/audit-log", "Audit Log");
        assertTrue(page.locator("table tbody tr").count() > 0,
                "Expected the Audit Log to list at least one entry.");
    }

    @Test(description = "12.2 Tenants: Company Management lists tenants with status/enable-disable controls (not toggled)")
    @Severity(SeverityLevel.NORMAL)
    public void tenantsManagement() {
        AppNavigator nav = login();
        assertTrue(nav.renders("/admin/tenants", "Company Management"), "Tenants page should render");
        assertTrue(page.locator("table tbody tr").count() > 0, "Expected at least one tenant row");
        assertTrue(page.getByText(Pattern.compile("Enable|Disable|Active|Suspend|Status", Pattern.CASE_INSENSITIVE)).count() > 0,
                "Expected enable/disable/status controls on the tenants list.");
    }

    @Test(description = "12.5 Blog CMS: blog list, new-post form (Publish + Save draft) and categories render")
    @Severity(SeverityLevel.NORMAL)
    public void blogCms() {
        AppNavigator nav = login();
        assertTrue(nav.renders("/admin/blog", "Blog posts"), "Blog list should render");
        assertTrue(page.locator("table tbody tr").count() > 0, "Expected existing blog posts in the list");

        assertTrue(nav.renders("/admin/blog/new", "New post"), "New-post form should render");
        assertTrue(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                        .setName(Pattern.compile("Publish", Pattern.CASE_INSENSITIVE))).count() > 0
                        && page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                        .setName(Pattern.compile("Save draft", Pattern.CASE_INSENSITIVE))).count() > 0,
                "Expected Publish + Save draft controls on the new-post form.");

        assertTrue(nav.renders("/admin/blog/categories", "Post categories"), "Blog categories should render");
    }
}
