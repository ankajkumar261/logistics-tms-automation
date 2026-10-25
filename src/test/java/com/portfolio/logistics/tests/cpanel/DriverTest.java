package com.portfolio.logistics.tests.cpanel;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Driver;
import com.portfolio.logistics.pages.DriversPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 2 — Master Data")
public class DriverTest extends BaseTest {

    @Test(description = "2.3 Drivers: create a driver and verify it appears in the list")
    @Severity(SeverityLevel.CRITICAL)
    public void createDriver() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        DriversPage drivers = new DriversPage(page);
        drivers.openDrivers();
        drivers.clickAddDriver();

        Driver dr = Driver.sample();
        drivers.createDriver(dr);

        assertTrue(drivers.isDriverPresent(dr.getFullName()),
                "Expected new driver '" + dr.getFullName() + "' to appear in the Drivers list");
    }
}