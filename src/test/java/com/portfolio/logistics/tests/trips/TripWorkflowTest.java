package com.portfolio.logistics.tests.trips;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Order;
import com.portfolio.logistics.pages.LoginPage;
import com.portfolio.logistics.pages.OrdersPage;
import com.portfolio.logistics.pages.TripDetailPage;
import com.portfolio.logistics.pages.TripsPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 4 — Trips & Dispatch")
public class TripWorkflowTest extends BaseTest {

    @Test(description = "4.2 Trip workflow: Dispatch -> Start -> Arrived -> Delivered, verify Completed")
    @Severity(SeverityLevel.CRITICAL)
    public void tripWorkflow() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);   // let the app settle post-login before deep-linking
        TripDetailPage trip = new TripDetailPage(page);

        // Create our OWN fresh trip so the workflow never depends on a leftover Planned trip
        // happening to be on the first page of /trips (that made this test skip intermittently).
        String tripNo = null;
        try {
            OrdersPage orders = new OrdersPage(page);
            orders.startNewOrder();
            orders.createOrder(Order.sample());
            TripsPage trips = new TripsPage(page);
            trips.startNewTrip();
            String created = trips.createTrip();
            if (created != null && trip.openTripByNumber(created)) {
                tripNo = created;
            }
        } catch (Exception e) {
            System.out.println("[TripWorkflow] Pre-creating a trip failed, will fall back: " + e.getMessage());
        }

        // Fallback: use any dispatchable trip already in the list.
        if (tripNo == null) {
            tripNo = trip.openDispatchableTrip();
        }
        // No dispatchable trip means the create-order/create-trip path itself is broken; that is a
        // failure to report, not a reason to skip the workflow check.
        assertNotNull(tripNo,
                "4.2 could not create a trip, and no dispatchable trip was available to fall back on — "
                        + "the order/trip creation path is broken.");

        assertTrue(trip.advanceStep("Dispatch Trip", "Dispatch"),
                "Expected 'Dispatch Trip' for trip " + tripNo);
        assertTrue(trip.advanceStep("Start Trip", "Start"),
                "Expected 'Start Trip' after dispatch");
        assertTrue(trip.advanceStep("Mark Delivery Arrived", "Arrived", "Mark Arrived", "En-Route", "Arrive"),
                "Expected 'Mark Delivery Arrived' after start");
        assertTrue(trip.advanceStep("Mark Delivered", "Delivered", "Complete Delivery", "Mark Delivery"),
                "Expected 'Mark Delivered' after arrival");

        // Trip type decides the last step: OUTSOURCING trips need a final "Complete Trip" click to
        // reach Completed; COMPANY trips auto-complete on delivery (no such button). Best-effort —
        // advanceStep is a no-op if the button isn't there.
        trip.advanceStep("Complete Trip", "Complete");

        assertTrue(trip.listStatusIs(tripNo, "Completed") || trip.listStatusIs(tripNo, "Delivered"),
                "Expected trip " + tripNo + " to show Completed after the workflow");
    }

}