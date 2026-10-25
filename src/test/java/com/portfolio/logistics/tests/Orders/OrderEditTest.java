package com.portfolio.logistics.tests.Orders;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.OrderEditPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 3 — Orders")
public class OrderEditTest extends BaseTest {

    @Test(description = "3.3 Edit order: change the rate and verify the detail reflects it")
    @Severity(SeverityLevel.CRITICAL)
    public void editOrderRate() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        OrderEditPage edit = new OrderEditPage(page);
        String orderNumber = edit.openFirstOrderForEdit();

        String newRate = edit.changeRate();

        assertTrue(edit.detailShowsRate(newRate),
                "Expected order " + orderNumber + " detail to show updated rate " + newRate);
    }
}