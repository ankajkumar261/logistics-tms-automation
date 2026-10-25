package com.portfolio.logistics.tests.auth;

import com.microsoft.playwright.options.Cookie;
import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.drivers.BrowserManager;
import com.portfolio.logistics.pages.DashboardPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.*;

/**
 * MODULE 1 — AUTH & TENANT ONBOARDING (the subset we can drive with the
 * SuperAdmin credentials we have). Role-guard needs a non-admin user and is
 * skipped until those creds are supplied.
 */
@Epic("Logistics TMS TMS")
@Feature("Module 1 — Auth")
public class AuthTest extends BaseTest {

    private static final String AUTH_COOKIE = "tiq_token";

    private boolean authCookieSet() {
        List<Cookie> cookies = BrowserManager.context().cookies();
        return cookies.stream().anyMatch(c -> AUTH_COOKIE.equals(c.name) && c.value != null && !c.value.isBlank());
    }

    @Test(description = "1.2 Login (happy path): sets tiq_token, redirects to /dashboard")
    @Severity(SeverityLevel.BLOCKER)
    public void loginHappyPath() {
        LoginPage login = new LoginPage(page).open();
        login.loginAsSuperAdmin();

        page.waitForURL("**/dashboard**");
        DashboardPage dashboard = new DashboardPage(page);

        assertTrue(dashboard.isLoaded(), "Expected to land on /dashboard, url=" + page.url());
        assertTrue(authCookieSet(), "Expected auth cookie '" + AUTH_COOKIE + "' to be set after login");
    }

    @Test(description = "1.3 Login (bad credentials): inline error, no cookie, stays on /login")
    @Severity(SeverityLevel.CRITICAL)
    public void loginBadCredentials() {
        LoginPage login = new LoginPage(page).open();
        login.login(ConfigReader.corporateId(), ConfigReader.superAdminUsername(), "definitely-wrong-password");

        assertTrue(login.isErrorShown(), "Expected an inline auth error for bad credentials");
        assertTrue(login.isOnLoginPage(), "Should stay on /login after bad credentials, url=" + page.url());
        assertFalse(authCookieSet(), "No auth cookie should be set on failed login");
    }

    @Test(description = "1.7 Logout: clears cookie, redirects to /login, back-nav re-redirects")
    @Severity(SeverityLevel.CRITICAL)
    public void logout() {
        LoginPage login = new LoginPage(page).open();
        login.loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        new DashboardPage(page).logout();
        page.waitForURL("**/login**");

        assertTrue(page.url().contains("/login"), "Expected redirect to /login after logout");
        assertFalse(authCookieSet(), "Auth cookie should be cleared after logout");

        // Back-nav to a protected route must re-redirect to /login.
        navigateWithRetry(ConfigReader.baseUrl() + "/dashboard");
        page.waitForURL("**/login**");
        assertTrue(page.url().contains("/login"), "Protected route should re-redirect to /login after logout");
    }
}
