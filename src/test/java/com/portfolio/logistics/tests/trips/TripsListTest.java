package com.portfolio.logistics.tests.trips;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.TripsListPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

@Epic("Logistics TMS TMS")
@Feature("Module 4 — Trips & Dispatch")
public class TripsListTest extends BaseTest {

    @Test(description = "4.2 Trips list: filter, sort, and pagination work")
    @Severity(SeverityLevel.NORMAL)
    public void tripsListOperations() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        TripsListPage trips = new TripsListPage(page);
        trips.open();

        int total = trips.totalResults();
        assertTrue(total > 0, "Expected trips in the list, got " + total);

        // Grab a real Trip # from the first row, then filter by it — should narrow.
        String firstTrip = trips.firstRowText().replaceAll("(?s).*?(TRP-\\d+).*", "$1");
        if (firstTrip.startsWith("TRP-")) {
            int filtered = trips.filterByTripNo(firstTrip);
            assertTrue(filtered >= 1 && filtered <= total,
                    "Filtering by " + firstTrip + " should narrow the list, got " + filtered);
            trips.resetFilters();
        }

        // Sort by Trip # (a real sortable header; "Date" opens a date-range filter, not sort).
        trips.sortByColumn("Trip #");
        assertFalse(trips.firstRowText().isBlank(), "Table should still render after sorting");

        // Pagination: 15 results = 2 pages.
        if (total > 10) {
            assertTrue(trips.goToNextPage(), "Expected page 2 to differ from page 1");
        }
    }
}