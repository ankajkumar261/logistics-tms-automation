package com.portfolio.logistics.tests.roles.reports;

import com.microsoft.playwright.options.LoadState;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.model.Role;
import com.portfolio.logistics.tests.roles.RoleTestBase;
import com.portfolio.logistics.utils.ReportDates;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * BUG-021/022 — as the REPORTS user, choosing a date from the calendar picker should filter the
 * Trip Profitability and Fuel Economy reports (previously only manual typing worked).
 */
@Epic("Logistics TMS TMS")
@Feature("Roles — REPORTS")
public class ReportsRoleDateFilterTest extends RoleTestBase {

    private void openReport(String route) {
        page.navigate(ConfigReader.baseUrl() + route);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1500);
    }

    @Test(description = "BUG-021 REPORTS user: calendar date filters the Trip Profitability report")
    @Severity(SeverityLevel.NORMAL)
    public void profitabilityDatePickerApplies() {
        signIn(Role.REPORTS);
        openReport("/reports/profitability");
        assertTrue(page.locator("input[type='date']").count() >= 2, "Profitability report should expose a date filter.");
        ReportDates.pickDate(page, 0, "1990-01-01");
        ReportDates.pickDate(page, 1, "1990-01-02");
        assertTrue(ReportDates.showsEmptyState(page),
                "REPORTS user: a 1990 calendar date range should filter Trip Profitability to empty, "
                        + "but it did not — calendar date not applied (BUG-021).");
    }

    @Test(description = "BUG-022 REPORTS user: the calendar date populates the Fuel Economy filter")
    @Severity(SeverityLevel.NORMAL)
    public void fuelDatePickerApplies() {
        signIn(Role.REPORTS);
        openReport("/reports/fuel");
        assertTrue(page.locator("input[type='date']").count() >= 2, "Fuel report should expose a date filter.");
        ReportDates.pickDate(page, 0, "2026-01-01");
        String v0 = page.locator("input[type='date']").first().inputValue();
        assertTrue("2026-01-01".equals(v0),
                "REPORTS user: Fuel report Start Date should reflect the calendar-picked date, was '" + v0 + "' (BUG-022).");
    }
}
