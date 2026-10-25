package com.portfolio.logistics.tests.bugfixes;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Truck;
import com.portfolio.logistics.pages.LoginPage;
import com.portfolio.logistics.pages.TrucksPage;
import com.portfolio.logistics.utils.ApiWatcher;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * FIX-02 — the Add Truck form used to drop the License Plate on its way to the backend. The value
 * typed into the form must now reach POST /api/fleet/trucks, come back on the created record, and
 * show in the Trucks list's License Plate column.
 *
 * Checking the list alone would not catch a regression here: a truck saved with a blank plate still
 * appears in the list, so the payload itself has to be asserted.
 */
@Epic("Logistics TMS TMS")
@Feature("Bug fixes — Trucks")
public class TruckLicensePlateTest extends BaseTest {

    @Test(description = "FIX-02 Truck creation sends the License Plate to the backend and persists it")
    @Severity(SeverityLevel.CRITICAL)
    public void licensePlateReachesBackendAndPersists() {
        new LoginPage(page).establishAdminSession();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

        TrucksPage trucks = new TrucksPage(page);
        trucks.openTrucks();
        trucks.clickAddTruck();

        Truck t = Truck.sample();
        trucks.createTruck(t);

        // 1. The UI must actually put the plate in the request it sends.
        ApiWatcher.Call create = api.requireCall("/api/fleet/trucks");
        assertTrue(create.sent("licensePlate", t.getPlate()),
                "The Add Truck request did not carry the License Plate '" + t.getPlate()
                        + "' that was typed into the form.\n" + create);

        // 2. The backend must accept it.
        assertTrue(create.ok(),
                "Creating truck '" + t.getUnitNumber() + "' failed.\n" + create);

        // 3. The created record must come back with the plate, not a blank/dropped value.
        assertTrue(create.responseBody.contains(t.getPlate()),
                "The created truck came back without License Plate '" + t.getPlate() + "'.\n" + create);

        // 4. And it must be what the Trucks list shows in its License Plate column.
        String row = trucks.rowFor(t.getUnitNumber());
        assertNotNull(row, "Truck '" + t.getUnitNumber() + "' did not appear in the Trucks list after creation.");
        assertTrue(row.contains(t.getPlate()),
                "The Trucks list row for '" + t.getUnitNumber() + "' does not show License Plate '"
                        + t.getPlate() + "'. Row read: " + row);
    }
}
