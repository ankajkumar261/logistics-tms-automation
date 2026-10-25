package com.portfolio.logistics.tests.smoke;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.config.ConfigReader;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * MODULE 0 — SMOKE / ENVIRONMENT.
 * setUp() already navigates to base.url, so 0.1 just inspects the landing.
 */
@Epic("Logistics TMS TMS")
@Feature("Module 0 — Smoke / Environment")
public class SmokeTest extends BaseTest {

    @Test(description = "0.1 App loads: landing renders with nav + footer, no console errors")
    @Severity(SeverityLevel.BLOCKER)
    public void appLoads() {
        // Landing should render a nav and a footer. Wait for them — the marketing page is a SPA
        // that hydrates after load, so asserting immediately races the render under parallel load.
        page.locator("nav, header").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(15000));
        page.locator("footer").first().waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(15000));
        assertTrue(page.locator("nav, header").first().isVisible(),
                "Expected a nav/header on the landing page");
        assertTrue(page.locator("footer").first().isVisible(),
                "Expected a footer on the landing page");

        List<String> errors = consoleErrors().stream()
                .filter(e -> !e.contains("401"))
                .toList();

        assertTrue(errors.isEmpty(),
                "Unexpected console errors: " + errors);
    }

    @Test(description = "0.3 Unauthed guard: /dashboard while logged out redirects to /login")
    @Severity(SeverityLevel.CRITICAL)
    public void unauthedGuardRedirects() {
        navigateWithRetry(ConfigReader.baseUrl() + "/dashboard");
        // Protected route should bounce an unauthenticated user to /login.
        page.waitForURL("**/login**");
        assertTrue(page.url().contains("/login"),
                "Expected redirect to /login, but landed on: " + page.url());
        assertFalse(page.url().contains("/dashboard"),
                "Should not remain on /dashboard while logged out");
    }
}
