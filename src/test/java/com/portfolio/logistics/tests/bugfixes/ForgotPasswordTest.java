package com.portfolio.logistics.tests.bugfixes;

import com.microsoft.playwright.options.LoadState;
import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.config.ConfigReader;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.regex.Pattern;

import static org.testng.Assert.assertTrue;

/**
 * BUG-008 — the Forgot Password page should tell the user HOW the reset is delivered. It now asks for
 * Corporate ID + Email Address and states the reset goes to that email (previously only Corporate ID +
 * Username with no delivery info).
 */
@Epic("Logistics TMS TMS")
@Feature("Bug fixes — Auth")
public class ForgotPasswordTest extends BaseTest {

    @Test(description = "BUG-008 Forgot Password page indicates how the reset is delivered")
    @Severity(SeverityLevel.NORMAL)
    public void forgotPasswordShowsDeliveryMethod() {
        page.navigate(ConfigReader.baseUrl() + "/forgot-password"); // logged out
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        assertTrue(page.getByText(Pattern.compile("Forgot Password", Pattern.CASE_INSENSITIVE)).count() > 0,
                "Should be on the Forgot Password page.");
        // Delivery method must be indicated — an Email field and/or instructive text.
        boolean emailField = page.locator("input[name='email'], input[type='email']").count() > 0;
        boolean deliveryText = page.getByText(Pattern.compile("email|sent to|reset your password|instructions",
                Pattern.CASE_INSENSITIVE)).count() > 0;
        assertTrue(emailField || deliveryText,
                "Forgot Password page should indicate how the reset is delivered (email field / instructions), "
                        + "but it does not (BUG-008).");
    }
}
