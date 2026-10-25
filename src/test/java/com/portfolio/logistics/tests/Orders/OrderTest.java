package com.portfolio.logistics.tests.Orders;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Order;
import com.portfolio.logistics.pages.OrdersPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 3 — Orders")
public class OrderTest extends BaseTest {

    @Test(description = "3.1 Orders: complete the New Order wizard and verify it appears")
    @Severity(SeverityLevel.CRITICAL)
    public void createOrder() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        OrdersPage orders = new OrdersPage(page);
        orders.startNewOrder();

        Order o = Order.sample();
        String orderNo = orders.createOrder(o);

        // Origin and Destination must be two different saved locations, otherwise
        // the order is (nonsensically) shipped from-and-to the same place.
        String origin = orders.getOriginLocation();
        String destination = orders.getDestinationLocation();
        assertNotNull(origin, "Origin location should have been selected");
        assertNotNull(destination, "Destination location should have been selected");
        assertNotEquals(destination, origin,
                "Origin and Destination must be different saved locations (both were '" + origin + "')");

        assertTrue(orders.isOrderPresent(orderNo),
                "Expected order with Customer Order # '" + orderNo + "' to appear in the Orders tab");
    }
}
