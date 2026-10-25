package com.portfolio.logistics.tests.Orders;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.OrdersListPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

@Epic("Logistics TMS TMS")
@Feature("Module 3 — Orders")
public class OrdersListTest extends BaseTest {

    @Test(description = "3.2 Orders list: search, filter, sort, and pagination work")
    @Severity(SeverityLevel.NORMAL)
    public void ordersListOperations() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        OrdersListPage orders = new OrdersListPage(page);
        orders.open();

        int total = orders.totalResults();
        assertTrue(total > 0, "Expected a positive total order count, got " + total);

        // Use an order number that actually exists in THIS environment (the first row's), so the
        // test works against any dataset (live, staging, etc.) instead of a hard-coded ORD-####.
        String firstCell = orders.firstOrderCellText();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("ORD-\\d+").matcher(firstCell);
        String firstOrderNo = m.find() ? m.group() : firstCell.trim();
        assertFalse(firstOrderNo.isBlank(), "Expected the first row to show an order number");

        int filtered = orders.filterByOrderNo(firstOrderNo);
        assertTrue(filtered >= 1,
                "Filtering by " + firstOrderNo + " should return at least 1 row, got " + filtered);
        orders.resetFilters();

        int searched = orders.search(firstOrderNo);
        assertTrue(searched >= 1, "Search for " + firstOrderNo + " should return at least 1 row, got " + searched);
        orders.clearSearch();

        String beforeSort = orders.firstOrderCellText();
        orders.sortByColumn("Order #");
        String afterSort = orders.firstOrderCellText();
        assertFalse(afterSort.isBlank(), "Table should still render a first row after sorting");

        if (total > 10) {
            assertTrue(orders.goToNextPage(), "Expected page 2 to show different rows than page 1");
        }
    }
}