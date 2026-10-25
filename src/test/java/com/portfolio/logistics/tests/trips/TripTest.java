package com.portfolio.logistics.tests.trips;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.TripsPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 4 — Trips & Dispatch")
public class TripTest extends BaseTest {

    @Test(description = "4.1 Create a trip from an existing order and verify it's created")
    @Severity(SeverityLevel.CRITICAL)
    public void createTrip() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        TripsPage trips = new TripsPage(page);
        trips.startNewTrip();

        String tripNumber = trips.createTrip();

        assertTrue(tripNumber != null && trips.isTripCreated(tripNumber),
                "Expected a trip to be created and listed");
    }
}