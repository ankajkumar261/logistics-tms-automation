package com.portfolio.logistics.tests.roles;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Role;
import com.portfolio.logistics.pages.LoginPage;
import com.portfolio.logistics.pages.RoleAccessPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Role-based access control. Verifies BOTH directions for each role:
 *   - allowed sections load,
 *   - forbidden sections show the app's "Access Restricted" gate.
 * The matrix was captured live (2026-07-16). SUPERADMIN/ADMIN see everything; the rest are scoped.
 * Every role is covered. REPORTS = Reports only (confirmed live 2026-07-17 once its login worked).
 */
@Epic("Logistics TMS TMS")
@Feature("Module 1 — Auth & Roles")
public class RoleAccessControlTest extends BaseTest {

    /** App sections, keyed to a representative route. */
    private enum Section {
        ORDERS("/orders"),
        TRIPS("/trips"),
        ACCOUNTING("/accounting/invoices"),
        CPANEL("/cpanel/drivers"),
        INTEGRATIONS("/integrations/chart-of-accounts"),
        REPORTS("/reports/sales"),
        ADMIN("/admin");

        final String route;
        Section(String route) { this.route = route; }
    }

    @Test(description = "1.4 SUPERADMIN can access every section")
    @Severity(SeverityLevel.CRITICAL)
    public void superAdminAccess() {
        checkAccess(Role.SUPERADMIN, EnumSet.allOf(Section.class));
    }

    @Test(description = "1.4 ADMIN can access every section")
    @Severity(SeverityLevel.CRITICAL)
    public void adminAccess() {
        checkAccess(Role.ADMIN, EnumSet.allOf(Section.class));
    }

    @Test(description = "1.4 DISPATCHER: Orders/Trips/C-Panel only; Accounting/Integrations/Reports/Admin blocked")
    @Severity(SeverityLevel.CRITICAL)
    public void dispatcherAccess() {
        checkAccess(Role.DISPATCHER, EnumSet.of(Section.ORDERS, Section.TRIPS, Section.CPANEL));
    }

    @Test(description = "1.4 DRIVER: Trips only; everything else blocked")
    @Severity(SeverityLevel.CRITICAL)
    public void driverAccess() {
        checkAccess(Role.DRIVER, EnumSet.of(Section.TRIPS));
    }

    @Test(description = "1.4 ACCOUNTS: Accounting/Reports only; Orders/Trips/C-Panel/Integrations/Admin blocked")
    @Severity(SeverityLevel.CRITICAL)
    public void accountsAccess() {
        checkAccess(Role.ACCOUNTS, EnumSet.of(Section.ACCOUNTING, Section.REPORTS));
    }

    @Test(description = "1.4 REPORTS: Reports only; Orders/Trips/Accounting/C-Panel/Integrations/Admin blocked")
    @Severity(SeverityLevel.CRITICAL)
    public void reportsAccess() {
        checkAccess(Role.REPORTS, EnumSet.of(Section.REPORTS));
    }

    /** Log in as {role} and assert each section is allowed/blocked exactly as {allowed} says. */
    private void checkAccess(Role role, EnumSet<Section> allowed) {
        LoginPage login = new LoginPage(page).open();
        login.loginAs(role);
        try {
            page.waitForURL(u -> !u.contains("/login"),
                    new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(15000));
        } catch (Exception ignored) { }
        // Guard: if the role can't sign in, fail with a clear reason instead of bogus access mismatches.
        assertFalse(login.isOnLoginPage(),
                role + " could not log in, so its access could not be verified — check its credentials.");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(800);

        RoleAccessPage access = new RoleAccessPage(page);
        List<String> mismatches = new ArrayList<>();
        for (Section s : Section.values()) {
            boolean restricted = access.isRestricted(s.route);
            boolean shouldAllow = allowed.contains(s);
            if (shouldAllow && restricted) {
                mismatches.add(s + " (" + s.route + ") should be ALLOWED but was blocked");
            } else if (!shouldAllow && !restricted) {
                mismatches.add(s + " (" + s.route + ") should be BLOCKED but was accessible");
            }
        }
        assertTrue(mismatches.isEmpty(),
                role + " access-control mismatches: " + mismatches);
    }
}
