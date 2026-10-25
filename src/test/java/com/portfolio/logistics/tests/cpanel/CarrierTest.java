package com.portfolio.logistics.tests.cpanel;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Carrier;
import com.portfolio.logistics.pages.CarriersPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 2 — Master Data")
public class CarrierTest extends BaseTest {

    @Test(description = "2.2 Carriers: create a carrier and verify it appears in the list")
    @Severity(SeverityLevel.CRITICAL)
    public void createCarrier() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        CarriersPage carriers = new CarriersPage(page);
        carriers.openCarriers();
        carriers.clickAddCarrier();

        Carrier c = Carrier.sample();
        carriers.createCarrier(c);

        assertTrue(carriers.isCarrierPresent(c.getCompanyName()),
                "Expected new carrier '" + c.getCompanyName() + "' to appear in the Carriers list");
    }
}