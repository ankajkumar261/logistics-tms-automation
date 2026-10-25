package com.portfolio.logistics.tests.cpanel;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Trailer;
import com.portfolio.logistics.pages.TrailersPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 2 — Master Data")
public class TrailerTest extends BaseTest {

    @Test(description = "2.5 Trailers: create a trailer and verify it appears in the list")
    @Severity(SeverityLevel.CRITICAL)
    public void createTrailer() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        TrailersPage trailers = new TrailersPage(page);
        trailers.openTrailers();
        trailers.clickAddTrailer();

        Trailer t = Trailer.sample();
        trailers.createTrailer(t);

        assertTrue(trailers.isTrailerPresent(t.getUnitNumber()),
                "Expected new trailer '" + t.getUnitNumber() + "' to appear in the Trailers list");
    }
}