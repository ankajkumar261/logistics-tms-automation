package com.portfolio.logistics.tests.bugfixes;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.LoginPage;
import com.portfolio.logistics.pages.TripTrackingPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * FIX-01 — "Asset Tracking failed when latitude/longitude was missing".
 *
 * The page must render without crashing whether or not trips have live GPS coordinates. The check
 * adapts to the environment's data instead of skipping:
 *   - it always asserts the page loads, shows the heading, does not hit an error boundary, and
 *     resolves to a definite state (trip cards + map, OR a clean "No active trips" empty state);
 *   - when trips ARE present, every advertised trip must be rendered (none dropped for lack of
 *     coordinates) and a trip whose location is approximate (no GPS fix) must be selectable and
 *     place a marker on the map without raising a coordinate error.
 * A stuck/blank/error page fails; a genuinely empty tenant is a valid state and passes.
 */
@Epic("Logistics TMS TMS")
@Feature("Bug fixes — Asset Tracking")
public class AssetTrackingMissingCoordinatesTest extends BaseTest {

    private TripTrackingPage openTracking() {
        new LoginPage(page).establishAdminSession();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        TripTrackingPage tracking = new TripTrackingPage(page);
        tracking.open();
        return tracking;
    }

    @Test(description = "FIX-01 Asset Tracking renders active trips (or a clean empty state) without failing on missing coordinates")
    @Severity(SeverityLevel.CRITICAL)
    public void trackingRendersWithoutFailingOnMissingCoordinates() {
        TripTrackingPage tracking = openTracking();

        assertTrue(tracking.pageLoaded(), "Asset Tracking should load at /trips/tracking, url=" + page.url());
        assertTrue(tracking.hasHeading("Asset Tracking"), "Expected the 'Asset Tracking' heading.");
        assertFalse(tracking.showsErrorBoundary(),
                "Asset Tracking rendered an error state instead of the tracking UI: " + tracking.panelText());

        List<String> trips = tracking.activeTripSummaries();
        if (trips.isEmpty()) {
            // No active trips in this tenant — the page must still resolve to a clean empty state,
            // which is exactly the missing-coordinates path not crashing the whole page.
            assertTrue(tracking.hasEmptyState(),
                    "Asset Tracking has no trips but did not show a recognizable empty state either — "
                            + "it may be stuck. Panel: " + tracking.panelText());
            System.out.println("[FIX-01] Tenant has no active trips; verified the empty state renders cleanly.");
            return;
        }

        // Trips present: the map must render, and every advertised trip must be listed (a trip
        // dropped because it lacked coordinates would show up here as a shortfall).
        assertTrue(tracking.hasMap(), "Trips are listed but the tracking map did not render.");
        int advertised = tracking.advertisedTripCount();
        if (advertised >= 0) {
            assertEquals(trips.size(), advertised,
                    "Asset Tracking says " + advertised + " active trips but rendered " + trips.size()
                            + " cards — trips are being dropped from the list. Rendered: " + trips);
        }
        System.out.println("[FIX-01] " + trips.size() + " active trips, "
                + tracking.approximateLocationCount() + " without a live GPS fix.");
    }

    @Test(description = "FIX-01 Selecting a trip with no GPS fix places it on the map without a coordinate error")
    @Severity(SeverityLevel.CRITICAL)
    public void selectingTripWithoutCoordinatesRendersMap() {
        TripTrackingPage tracking = openTracking();

        if (tracking.activeTripCount() == 0) {
            // Nothing to select in this tenant — assert the empty state is clean rather than pretend
            // the selection path was verified. (On a data-rich tenant this branch does the real work.)
            assertFalse(tracking.showsErrorBoundary(),
                    "Asset Tracking is empty AND showing an error state: " + tracking.panelText());
            assertTrue(tracking.hasEmptyState(),
                    "Asset Tracking has no trips and no empty-state message — it may be stuck loading. "
                            + "Panel: " + tracking.panelText());
            System.out.println("[FIX-01] No active trips to select in this tenant; verified clean empty state. "
                    + "The no-GPS selection path is exercised on a data-populated tenant (e.g. QA).");
            return;
        }

        // Prefer a trip whose location is approximate (no GPS) — that is the exact regression surface.
        String reference = tracking.clickFirstApproximateTrip();
        if (reference == null) {
            reference = tracking.clickFirstTrip();
            System.out.println("[FIX-01] No approximate-location trip available; verifying selection with "
                    + reference + " (has a live fix).");
        }

        assertFalse(tracking.showsErrorBoundary(),
                "Selecting trip " + reference + " put the page into an error state: " + tracking.panelText());
        assertTrue(tracking.hasMapOverlay(),
                "Selecting trip " + reference + " should place it on the map, but no marker/route overlay rendered.");
        assertTrue(tracking.panelText().contains(reference),
                "The detail panel should show the selected trip " + reference + ", but read: " + tracking.panelText());

        List<String> crashes = consoleErrors().stream()
                .filter(e -> e.contains("TypeError") || e.contains("Cannot read") || e.contains("undefined is not"))
                .toList();
        assertTrue(crashes.isEmpty(),
                "Selecting a trip raised JavaScript errors (a missing-coordinate crash would show here): " + crashes);
    }
}
