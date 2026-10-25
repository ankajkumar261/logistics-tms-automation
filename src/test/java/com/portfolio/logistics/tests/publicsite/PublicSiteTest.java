package com.portfolio.logistics.tests.publicsite;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.config.ConfigReader;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 14 — Public Marketing Site")
public class PublicSiteTest extends BaseTest {

    @Test(description = "14.1 Landing renders (nav + footer + CTA to signup/login), no console errors")
    @Severity(SeverityLevel.NORMAL)
    public void landingRenders() {
        // setUp() already navigated to base.url (the public landing) — inspect it anonymously.
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(800);

        assertTrue(page.locator("nav, header").first().isVisible(), "Expected a nav/header on the landing");
        assertTrue(page.locator("footer").first().isVisible(), "Expected a footer on the landing");
        assertTrue(page.locator("a[href*='/signup'], a[href*='/login'], a[href*='/register']").count() > 0,
                "Expected a CTA linking to signup/login on the landing.");

        List<String> errors = consoleErrors().stream().filter(e -> !e.contains("401")).toList();
        assertTrue(errors.isEmpty(), "Unexpected console errors on the landing: " + errors);
    }

    @Test(description = "14.2 Public blog renders without requiring login")
    @Severity(SeverityLevel.NORMAL)
    public void publicBlogRenders() {
        navigateWithRetry(ConfigReader.baseUrl() + "/blog");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        assertFalse(page.url().contains("/login"), "The public blog should not require login");
        assertTrue(page.getByText(Pattern.compile("Guides, product news|freight insights|Blog",
                        Pattern.CASE_INSENSITIVE)).count() > 0
                        || page.locator("article, a[href*='/blog/']").count() > 0,
                "Expected the public blog to render posts/content.");
    }
}
