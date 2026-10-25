package com.portfolio.logistics.tests.trips;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.TripTrackingPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 4 — Trips & Dispatch")
public class TripTrackingTest extends BaseTest {

    @Test(description = "4.3 Trip tracking: tracking page loads and shows active trips (map/list) or empty state")
    @Severity(SeverityLevel.NORMAL)
    public void tripTrackingLoads() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        TripTrackingPage tracking = new TripTrackingPage(page);
        tracking.open();

        assertTrue(tracking.pageLoaded(),
                "Trip tracking page should load at /trips/tracking, url=" + page.url());
        assertTrue(tracking.hasHeading("Asset Tracking"),
                "Expected 'Asset Tracking' heading");
        assertTrue(tracking.hasMap(),
                "Expected the tracking map to render");

        // Selecting an active trip must render its route/marker on the map. An empty list is a
        // failure, not a skip: /trips/tracking is expected to list the tenant's dispatched trips.
        int active = tracking.activeTripCount();
        System.out.println("[TripTracking] active trips = " + active);
        assertTrue(active > 0,
                "Asset Tracking listed no active trips, so the map selection could not be verified. "
                        + "Page reports: " + tracking.advertisedTripCount() + " active trips.");

        String tripLabel = tracking.clickFirstTrip();
        assertTrue(tracking.hasMapOverlay(),
                "Selecting " + tripLabel + " should render its route/marker on the map, but no overlay appeared.");
    }
}