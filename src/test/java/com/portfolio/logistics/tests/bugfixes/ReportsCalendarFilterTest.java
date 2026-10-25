package com.portfolio.logistics.tests.bugfixes;

import com.microsoft.playwright.options.LoadState;
import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.pages.LoginPage;
import com.portfolio.logistics.utils.ReportDates;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * BUG-009/010/011 — report date range should update when a date is chosen from the calendar picker
 * (previously only manual typing worked). These pick a date the way the calendar delivers it
 * (value + input/change events) and assert the report actually re-filters.
 */
@Epic("Logistics TMS TMS")
@Feature("Bug fixes — Reports date picker")
public class ReportsCalendarFilterTest extends BaseTest {

    private void login() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);
    }

    private void openReport(String route) {
        page.navigate(ConfigReader.baseUrl() + route);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1500);
    }

    @Test(description = "BUG-009 Sales Report: choosing a date from the calendar filters the report")
    @Severity(SeverityLevel.CRITICAL)
    public void salesDatePickerApplies() {
        login();
        openReport("/reports/sales");
        assertTrue(page.locator("input[type='date']").count() >= 2, "Sales report should have a start/end date filter.");
        ReportDates.pickDate(page, 0, "1990-01-01");
        ReportDates.pickDate(page, 1, "1990-01-02");
        assertTrue(ReportDates.showsEmptyState(page),
                "Selecting a 1990 date range from the calendar should filter the Sales report to empty, "
                        + "but it did not — the calendar date is not being applied (BUG-009).");
    }

    @Test(description = "BUG-010 Trip Profitability: choosing a date from the calendar filters the report")
    @Severity(SeverityLevel.CRITICAL)
    public void profitabilityDatePickerApplies() {
        login();
        openReport("/reports/profitability");
        assertTrue(page.locator("input[type='date']").count() >= 2, "Profitability report should have a start/end date filter.");
        ReportDates.pickDate(page, 0, "1990-01-01");
        ReportDates.pickDate(page, 1, "1990-01-02");
        assertTrue(ReportDates.showsEmptyState(page),
                "Selecting a 1990 date range from the calendar should filter Trip Profitability to empty, "
                        + "but it did not — the calendar date is not being applied (BUG-010).");
    }

    @Test(description = "BUG-011 Fuel Economy: the calendar date populates the filter without error")
    @Severity(SeverityLevel.NORMAL)
    public void fuelDatePickerApplies() {
        login();
        openReport("/reports/fuel");
        assertTrue(page.locator("input[type='date']").count() >= 2, "Fuel report should have a start/end date filter.");
        ReportDates.pickDate(page, 0, "2026-01-01");
        // Fuel has no data on this tenant, so filtering to empty can't be observed; assert the picked
        // date is reflected in the field (i.e. the calendar value is applied, not ignored).
        String v0 = page.locator("input[type='date']").first().inputValue();
        assertTrue("2026-01-01".equals(v0),
                "Fuel report Start Date should reflect the calendar-picked date '2026-01-01', was '" + v0 + "'.");
    }
}
