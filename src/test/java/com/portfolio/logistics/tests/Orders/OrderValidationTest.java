package com.portfolio.logistics.tests.Orders;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.LoginPage;
import com.portfolio.logistics.pages.OrdersPage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.regex.Pattern;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 3 — Orders")
public class OrderValidationTest extends BaseTest {

    @Test(description = "3.8 Validation: New Order with empty required fields does not advance past Basic Info")
    @Severity(SeverityLevel.NORMAL)
    public void emptyRequiredFieldsBlockAdvance() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        OrdersPage orders = new OrdersPage(page);
        orders.startNewOrder();

        // Click Next without filling anything required.
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Next$"))).first().click();

        // PRIMARY (deterministic) assertion: still on Basic Info — Origin heading never appears.
        // If the wizard had advanced, an "Origin / Pickup Location" heading would show.
        boolean advancedToOrigin = waitForVisible(
                page.getByText(Pattern.compile("Origin / Pickup Location", Pattern.CASE_INSENSITIVE)), 3000);
        assertTrue(!advancedToOrigin,
                "Wizard should NOT advance to Origin when required fields are empty");

        // Also confirm we can still see the step-1 Customer Order # field.
        assertTrue(page.locator("input[name='referenceNumber']").isVisible(),
                "Should remain on Basic Info step (Customer Order # field still visible)");

        // SECONDARY: a required error should show (wait for it, don't assume instant).
        boolean errorShown = waitForVisible(
                page.getByText(Pattern.compile("required", Pattern.CASE_INSENSITIVE)), 5000);
        assertTrue(errorShown,
                "Expected a 'required' validation message when submitting empty required fields");
    }

    @Test(description = "3.8 Validation: partial data (no Customer) still does not advance")
    @Severity(SeverityLevel.NORMAL)
    public void partialDataBlocksAdvance() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        OrdersPage orders = new OrdersPage(page);
        orders.startNewOrder();

        page.locator("input[name='referenceNumber']").fill("QA-VALIDATION-TEST");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Next$"))).first().click();

        boolean advancedToOrigin = waitForVisible(
                page.getByText(Pattern.compile("Origin / Pickup Location", Pattern.CASE_INSENSITIVE)), 3000);
        assertTrue(!advancedToOrigin,
                "Wizard should NOT advance when the required Customer is not selected");

        boolean customerError = waitForVisible(
                page.getByText(Pattern.compile("Customer is required", Pattern.CASE_INSENSITIVE)), 5000);
        assertTrue(customerError,
                "Expected 'Customer is required' when advancing without a customer");
    }

    /** Wait up to timeoutMs for a locator to become visible; return false instead of throwing. */
    private boolean waitForVisible(Locator locator, int timeoutMs) {
        try {
            locator.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}