package com.portfolio.logistics.tests.Orders;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Order;
import com.portfolio.logistics.pages.LoginPage;
import com.portfolio.logistics.pages.OrderDetailPage;
import com.portfolio.logistics.pages.OrdersPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 3 — Orders")
public class OrderShipmentsTest extends BaseTest {

    @Test(description = "3.5 Order shipments: add a Pickup stop (Shipper Name) and a Delivery stop "
            + "(Consignee Name), confirm the order, reopen it, and verify both stops persist with their names")
    @Severity(SeverityLevel.CRITICAL)
    public void addPickupAndDeliveryStops() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        // Fresh order — lands on the detail page with the Additional Stops section.
        OrdersPage orders = new OrdersPage(page);
        orders.startNewOrder();
        String orderNumber = orders.createOrder(Order.sample());
        assertNotNull(orderNumber, "Order creation should return an ORD number");

        OrderDetailPage detail = new OrderDetailPage(page);
        long t = System.currentTimeMillis() % 100000;
        String shipper = "QA-Shipper-" + t;      // Pickup  -> "Shipper Name"
        String consignee = "QA-Consignee-" + t;  // Delivery -> "Consignee Name"

        // --- Add a Pickup stop (with shipper) and a Delivery stop (with consignee) ---
        assertTrue(detail.addStop("Pickup", shipper),
                "Should add a Pickup stop with shipper '" + shipper + "'");
        assertTrue(detail.addStop("Delivery", consignee),
                "Should add a Delivery stop with consignee '" + consignee + "'");
        assertEquals(detail.additionalStopsCount(), 2,
                "Order should have 2 additional stops after adding a pickup and a delivery");

        // --- Confirm the order ---
        assertTrue(detail.hasConfirmButton(), "A Pending order should offer the Confirm action");
        assertTrue(detail.clickConfirm(), "Confirm should succeed on order " + orderNumber);

        // --- Reopen the order and verify both stops persisted, with their names ---
        // The shipments data loads asynchronously after navigation, so poll for it to settle.
        detail.openOrder(orderNumber);
        assertEquals(detail.waitForAdditionalStops(2), 2,
                "Both stops should still be present after confirming and reopening order " + orderNumber);
        assertTrue(detail.hasStopWithName(shipper),
                "The Pickup stop's shipper name '" + shipper + "' should be visible after reopening");
        assertTrue(detail.hasStopWithName(consignee),
                "The Delivery stop's consignee name '" + consignee + "' should be visible after reopening");
    }
}
