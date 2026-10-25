package com.portfolio.logistics.tests.roles.dispatcher;

import com.portfolio.logistics.model.Order;
import com.portfolio.logistics.model.Role;
import com.portfolio.logistics.pages.OrdersListPage;
import com.portfolio.logistics.pages.OrdersPage;
import com.portfolio.logistics.tests.roles.RoleTestBase;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Roles — DISPATCHER")
public class DispatcherOrderTest extends RoleTestBase {

    @Test(description = "DISPATCHER can create an order and see it in the list")
    @Severity(SeverityLevel.CRITICAL)
    public void createOrder() {
        signIn(Role.DISPATCHER);
        OrdersPage orders = new OrdersPage(page);
        orders.startNewOrder();
        String orderNo = orders.createOrder(Order.sample());
        assertTrue(orders.isOrderPresent(orderNo),
                "DISPATCHER should be able to create order '" + orderNo + "' and see it listed.");
    }

    @Test(description = "DISPATCHER can use the Orders list (count, filter, reset)")
    @Severity(SeverityLevel.NORMAL)
    public void ordersListOperations() {
        signIn(Role.DISPATCHER);
        OrdersListPage orders = new OrdersListPage(page);
        orders.open();
        assertTrue(orders.totalResults() > 0, "DISPATCHER should see orders in the list.");
        orders.resetFilters();
    }
}
