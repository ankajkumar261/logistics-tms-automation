package com.portfolio.logistics.tests.cpanel;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Incorporation;
import com.portfolio.logistics.pages.IncorporationsPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 2 — Master Data")
public class IncorporationTest extends BaseTest {

    @Test(description = "2.8 Incorporations: create a record and verify it appears in the list")
    @Severity(SeverityLevel.NORMAL)
    public void createIncorporation() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        IncorporationsPage inc = new IncorporationsPage(page);
        inc.openIncorporations();
        inc.clickAdd();

        Incorporation record = Incorporation.sample();
        inc.createIncorporation(record);

        assertTrue(inc.isPresent(record.getName()),
                "Expected incorporation '" + record.getName() + "' to appear in the list");
    }
}