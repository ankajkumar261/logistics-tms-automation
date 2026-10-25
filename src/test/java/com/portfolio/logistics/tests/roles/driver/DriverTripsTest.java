package com.portfolio.logistics.tests.roles.driver;

import com.portfolio.logistics.model.Role;
import com.portfolio.logistics.pages.AppNavigator;
import com.portfolio.logistics.pages.TripTrackingPage;
import com.portfolio.logistics.tests.roles.RoleTestBase;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * DRIVER is scoped to Trips (its nav only exposes /trips and /trips/tracking). We verify it can
 * VIEW those, and that Asset Tracking renders a selected trip's route on the map.
 */
@Epic("Logistics TMS TMS")
@Feature("Roles — DRIVER")
public class DriverTripsTest extends RoleTestBase {

    @Test(description = "DRIVER can open the Trips list")
    @Severity(SeverityLevel.CRITICAL)
    public void tripsListRenders() {
        signIn(Role.DRIVER);
        assertTrue(new AppNavigator(page).renders("/trips", "Trips"),
                "DRIVER should be able to view the Trips list.");
    }

    @Test(description = "DRIVER can open Asset Tracking (page loads with map and active trips)")
    @Severity(SeverityLevel.NORMAL)
    public void trackingPageLoads() {
        signIn(Role.DRIVER);
        TripTrackingPage tracking = new TripTrackingPage(page);
        tracking.open();
        assertTrue(tracking.pageLoaded(), "Asset Tracking should load for DRIVER, url=" + page.url());
        assertTrue(tracking.hasHeading("Asset Tracking"), "Expected the 'Asset Tracking' heading.");
        assertTrue(tracking.hasMap(), "Expected the tracking map to render.");
    }

    @Test(description = "DRIVER: selecting an active trip renders its position/route on the map")
    @Severity(SeverityLevel.CRITICAL)
    public void trackingShowsSelectedTripOnMap() {
        signIn(Role.DRIVER);
        TripTrackingPage tracking = new TripTrackingPage(page);
        tracking.open();
        assertTrue(tracking.activeTripCount() > 0,
                "Asset Tracking listed no active trips for DRIVER, so the map selection could not be "
                        + "verified. Page reports: " + tracking.advertisedTripCount() + " active trips.");
        String tripLabel = tracking.clickFirstTrip();
        assertTrue(tracking.hasMapOverlay(),
                "Selecting " + tripLabel + " should render its route/marker on the map.");
    }
}
