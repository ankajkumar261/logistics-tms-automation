package com.portfolio.logistics.tests.roles;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.model.Role;
import com.portfolio.logistics.pages.LoginPage;

/**
 * Shared base for per-role FUNCTIONAL tests. Signs in as a given role, then the test drives the
 * SAME page objects the SuperAdmin suite uses — so we exercise each role's real functionality
 * (not just page access) without duplicating or modifying the existing scripts.
 */
public abstract class RoleTestBase extends BaseTest {

    protected void signIn(Role role) {
        LoginPage login = new LoginPage(page);
        // /login navigation occasionally ERR_ABORTs on this shared env — retry once.
        try {
            login.open();
        } catch (Exception e) {
            page.waitForTimeout(1500);
            login.open();
        }
        login.loginAs(role);
        // Roles don't all land on /dashboard (scoped roles may land on their own section), so wait
        // for the app to LEAVE /login rather than a specific URL — then each test deep-links itself.
        try {
            page.waitForURL(u -> !u.contains("/login"),
                    new com.microsoft.playwright.Page.WaitForURLOptions().setTimeout(15000));
        } catch (Exception ignored) { }
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000); // let the SPA settle before deep-linking
    }

    /** Navigate straight to a route — avoids the flaky nav-menu dropdown paths in some page objects. */
    protected void goTo(String route) {
        page.navigate(ConfigReader.baseUrl() + route);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(700);
    }

    /**
     * True if {text} appears in a paginated list at {route}. These C-Panel lists have no search box
     * and page at 10/row, so a freshly-added item often lands on a LATER page — checking only page 1
     * (as some isXPresent() methods do) misses it. This walks every page via "Go to next page".
     */
    protected boolean listContainsAcrossPages(String route, String text) {
        goTo(route);
        for (int p = 0; p < 30; p++) {
            Locator match = page.getByText(text, new Page.GetByTextOptions().setExact(false));
            if (match.count() > 0 && match.first().isVisible()) return true;
            Locator next = page.locator("button[aria-label='Go to next page']").first();
            if (next.count() == 0 || !next.isEnabled()) return false; // last page reached
            next.click();
            page.waitForTimeout(700);
        }
        return false;
    }
}
