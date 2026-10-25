package com.portfolio.logistics.tests.cpanel;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Truck;
import com.portfolio.logistics.pages.TrucksPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 2 — Master Data")
public class TruckTest extends BaseTest {

    @Test(description = "2.4 Trucks: create a truck and verify it appears in the list")
    @Severity(SeverityLevel.CRITICAL)
    public void createTruck() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        TrucksPage trucks = new TrucksPage(page);
        trucks.openTrucks();
        trucks.clickAddTruck();

        Truck t = Truck.sample();
        trucks.createTruck(t);

        assertTrue(trucks.isTruckPresent(t.getUnitNumber()),
                "Expected new truck '" + t.getUnitNumber() + "' to appear in the Trucks list");
    }
}