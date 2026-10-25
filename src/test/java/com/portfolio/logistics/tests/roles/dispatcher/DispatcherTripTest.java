package com.portfolio.logistics.tests.roles.dispatcher;

import com.portfolio.logistics.model.Role;
import com.portfolio.logistics.pages.TripsListPage;
import com.portfolio.logistics.pages.TripsPage;
import com.portfolio.logistics.tests.roles.RoleTestBase;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Roles — DISPATCHER")
public class DispatcherTripTest extends RoleTestBase {

    @Test(description = "DISPATCHER can create a trip from an existing order")
    @Severity(SeverityLevel.CRITICAL)
    public void createTrip() {
        signIn(Role.DISPATCHER);
        TripsPage trips = new TripsPage(page);
        trips.startNewTrip();
        String tripNumber = trips.createTrip();
        assertTrue(tripNumber != null && trips.isTripCreated(tripNumber),
                "DISPATCHER should be able to create a trip and see it listed.");
    }

    @Test(description = "DISPATCHER can use the Trips list (count)")
    @Severity(SeverityLevel.NORMAL)
    public void tripsListOperations() {
        signIn(Role.DISPATCHER);
        TripsListPage trips = new TripsListPage(page);
        trips.open();
        assertTrue(trips.totalResults() > 0, "DISPATCHER should see trips in the list.");
    }
}
