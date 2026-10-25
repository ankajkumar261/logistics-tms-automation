package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.model.Role;

/**
 * Page object for the Logistics TMS /login screen.
 *
 * Locators are layered (type-based, which is the most stable for auth forms).
 * If the first live run shows a mismatch, paste the login DOM and we tighten
 * these — exactly how we iterated the E-commerce login page.
 */
public class LoginPage {

    private final Page page;

    public LoginPage(Page page) {
        this.page = page;
    }

    private Locator corporateIdField() {
        return page.locator("input[name='corporateId']").first();
    }

    private Locator usernameField() {
        return page.locator("input[name='username']").first();
    }

    private Locator passwordField() {
        return page.locator("input[name='password']").first();
    }

    private Locator submitButton() {
        return page.locator("button[type='submit']").first();
    }

    private Locator errorMessage() {
        return page.locator("text=Invalid credentials").first();
    }

    /** Navigate to the login page. */
    public LoginPage open() {
        page.navigate(ConfigReader.baseUrl() + "/login");
        corporateIdField().waitFor();
        return this;
    }

    /** Fill corporateId + username + password and submit. */
    public void login(String corporateId, String username, String password) {
        corporateIdField().fill(corporateId);
        usernameField().fill(username);
        passwordField().fill(password);
        submitButton().click();
    }

    /** Convenience: login as the configured SuperAdmin via the login FORM (for auth tests). */
    public void loginAsSuperAdmin() {
        login(ConfigReader.corporateId(),
              ConfigReader.superAdminUsername(),
              ConfigReader.superAdminPass());
    }

    /**
     * Login as the configured full-access admin. This is SuperAdmin by default, but resolves to
     * whatever account config marks as full-access for the current environment (admin.* keys) —
     * on LIVE, where SuperAdmin is disabled, that is a full-access ADMIN account. Tests that only
     * need "a user who can reach everything" should use this so they run on QA and LIVE alike.
     */
    public void loginAsAdmin() {
        login(ConfigReader.adminCorporateId(),
              ConfigReader.adminUsername(),
              ConfigReader.adminPass());
    }

    /**
     * Establish a full-access session WITHOUT the login form, via the reliable backend auth API
     * (see {@link com.portfolio.logistics.utils.ApiSession}). Use this in tests that merely need to be signed
     * in to reach the feature under test — it removes the LIVE login UI's intermittent no-op as a
     * source of false failures. Tests whose subject IS the login page use loginAsAdmin/SuperAdmin.
     * Lands on /dashboard, matching what the form-based logins leave for callers.
     */
    public void establishAdminSession() {
        com.portfolio.logistics.utils.ApiSession.establish(page,
                ConfigReader.adminCorporateId(),
                ConfigReader.adminUsername(),
                ConfigReader.adminPass());
    }

    /** Login as any configured role (credentials + optional per-role corporateId from config.properties). */
    public void loginAs(Role role) {
        login(role.corporateId(), role.username(), role.password());
    }

    public boolean isErrorShown() {
        try {
            errorMessage().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(5000));
            return errorMessage().isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isOnLoginPage() {
        return page.url().contains("/login");
    }
}
