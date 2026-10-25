package com.portfolio.logistics.tests.Orders;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Order;
import com.portfolio.logistics.pages.OrderDetailPage;
import com.portfolio.logistics.pages.OrdersPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

@Epic("Logistics TMS TMS")
@Feature("Module 3 — Orders")
public class OrderStatusTest extends BaseTest {

    @Test(description = "3.4 Order status: valid transition (Pending->Confirmed) succeeds; "
            + "invalid re-transition is blocked (Confirm no longer offered)")
    @Severity(SeverityLevel.CRITICAL)
    public void orderStatusTransitions() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        // Fresh order — guaranteed Pending with a Confirm action.
        OrdersPage orders = new OrdersPage(page);
        orders.startNewOrder();
        Order o = Order.sample();
        String orderNumber = orders.createOrder(o);
        assertNotNull(orderNumber, "Order creation should return an ORD number");

        OrderDetailPage detail = new OrderDetailPage(page);

        // --- ShipmentsSection: add a pickup stop, verify the count increments ---
        // A Pickup stop requires a Shipper Name (the app rejects the save without it).
        String shipper = "QA-Shipper-" + (System.currentTimeMillis() % 100000);
        assertTrue(detail.addStop("Pickup", shipper),
                "Adding a pickup stop (with shipper '" + shipper + "') should succeed");
        assertTrue(detail.waitForAdditionalStops(1) >= 1,
                "Additional Stops count should be >= 1 after adding a pickup stop");

        // --- VALID transition: Pending -> Confirmed ---
        assertTrue(detail.hasConfirmButton(),
                "A Pending order should offer the Confirm action");
        assertTrue(detail.clickConfirm(),
                "Confirm should succeed on Pending order " + orderNumber);
        assertTrue(detail.listShowsStatus(orderNumber, "Confirmed"),
                "Order " + orderNumber + " should be Confirmed after confirming");

        // --- INVALID transition: cannot Confirm again ---
        detail.openOrder(orderNumber);
        assertFalse(detail.hasConfirmButton(),
                "An already-Confirmed order must NOT offer Confirm again");
    }
}