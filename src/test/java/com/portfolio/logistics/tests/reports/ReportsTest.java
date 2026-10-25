package com.portfolio.logistics.tests.reports;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.pages.AppNavigator;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 11 — Reports")
public class ReportsTest extends BaseTest {

    private AppNavigator login() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);
        return new AppNavigator(page);
    }

    @Test(description = "11.1 Reports hub renders and links to reports")
    @Severity(SeverityLevel.NORMAL)
    public void reportsHub() {
        AppNavigator nav = login();
        assertTrue(nav.renders("/reports", "Reports & Exports"), "Reports hub should render");
        assertTrue(page.locator("a[href*='/reports/']").count() > 0
                        || page.getByText(Pattern.compile("Sales|Profitability|Aging", Pattern.CASE_INSENSITIVE)).count() > 0,
                "Reports hub should list/link individual reports");
    }

    @Test(description = "11.2 Individual reports (sales, profitability, fuel, aging, outstanding) render with data")
    @Severity(SeverityLevel.NORMAL)
    public void individualReports() {
        List<String> f = login().firstFailures(
                "/reports/sales", "Sales Report",
                "/reports/profitability", "Trip Profitability",
                "/reports/fuel", "Fuel Economy Report",
                "/reports/aging", "Aging Invoices",
                "/reports/outstanding", "Outstanding Report");
        assertTrue(f.isEmpty(), "Reports that did not render: " + f);
    }

    @Test(description = "11.3 Exports: a report downloads as a file (CSV) from the Sales report")
    @Severity(SeverityLevel.NORMAL)
    public void exportReport() {
        login();
        page.navigate(ConfigReader.baseUrl() + "/reports/sales");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        Locator exportBtn = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("Export", Pattern.CASE_INSENSITIVE)));
        assertTrue(exportBtn.count() > 0, "Expected an Export control on the Sales report.");

        Download download = page.waitForDownload(new Page.WaitForDownloadOptions().setTimeout(20000),
                () -> exportBtn.first().click());
        String name = download.suggestedFilename();
        assertTrue(name != null && !name.isBlank(),
                "Expected a file to download when exporting the Sales report, got: " + name);
        download.saveAs(Paths.get("exports").resolve("sales-report-" + System.currentTimeMillis()
                + (name.contains(".") ? name.substring(name.lastIndexOf('.')) : ".csv")));
    }

    // ---------------------------------------------------------------------
    // 11.4  Report filters — each test applies a filter and asserts it has a
    //       real effect on the data. A filter that silently does nothing makes
    //       its test fail (that is the point: it flags a broken filter).
    // ---------------------------------------------------------------------

    @Test(description = "11.4 Sales report: the Start/End date range filter narrows the data")
    @Severity(SeverityLevel.NORMAL)
    public void salesDateRangeFilter() {
        login();
        openReport("/reports/sales");
        assertTrue(dataRowCount() > 0, "Sales report should show data before filtering.");

        Locator dates = page.locator("input[type='date']");
        assertTrue(dates.count() >= 2, "Sales report should expose a Start/End date filter.");
        // A window well before any order exists must exclude everything.
        dates.nth(0).fill("1990-01-01");
        dates.nth(1).fill("1990-01-02");
        applyWait();

        assertTrue(showsEmptyState(),
                "A 1990 date range should filter out all data on the Sales report, but rows remained "
                        + "(" + tableSignature() + ") — the date filter is not working.");
    }

    @Test(description = "11.4 Trip Profitability: the Driver filter shows only that driver's trips")
    @Severity(SeverityLevel.NORMAL)
    public void profitabilityDriverFilter() {
        login();
        openReport("/reports/profitability");
        assertTrue(dataRowCount() > 0, "Profitability report should show data before filtering.");

        String driver = pickComboOptionByIndex("All Drivers", 1); // first real driver
        applyWait();

        List<String> rows = page.locator("table tbody tr").allInnerTexts();
        assertFalse(rows.isEmpty(), "Filtering by driver '" + driver + "' returned no rows.");
        for (String row : rows) {
            assertTrue(row.contains(driver),
                    "Driver filter is not working — it returned a row not for '" + driver + "': " + row);
        }
    }

    @Test(description = "11.4 Trip Profitability: the Truck filter shows only that truck's trips")
    @Severity(SeverityLevel.NORMAL)
    public void profitabilityTruckFilter() {
        login();
        openReport("/reports/profitability");
        assertTrue(dataRowCount() > 0, "Profitability report should show data before filtering.");

        String truck = pickComboOptionByIndex("All Trucks", 1); // first real truck
        applyWait();

        List<String> rows = page.locator("table tbody tr").allInnerTexts();
        assertFalse(rows.isEmpty(), "Filtering by truck '" + truck + "' returned no rows.");
        for (String row : rows) {
            assertTrue(row.contains(truck),
                    "Truck filter is not working — it returned a row not for '" + truck + "': " + row);
        }
    }

    @Test(description = "11.4 Outstanding report: the Payable (AP) filter excludes Receivable invoices")
    @Severity(SeverityLevel.NORMAL)
    public void outstandingTypeFilter() {
        login();
        openReport("/reports/outstanding");
        assertTrue(dataRowCount() > 0, "Outstanding report should show data before filtering.");

        pickComboOptionByText(Pattern.compile("Outstanding|Receivable|Payable"), "Payable (AP)");
        applyWait();

        Locator rows = page.locator("table tbody tr");
        int n = rows.count();
        assertTrue(n > 0 && !showsEmptyState(), "Payable (AP) filter returned no rows.");
        boolean sawPayable = false;
        for (int i = 0; i < n; i++) {
            String type = rows.nth(i).locator("td").first().innerText().trim();
            assertNotEquals(type, "Invoice",
                    "Payable (AP) filter is not working — a Receivable (Invoice) row remained: " + type);
            if (type.toLowerCase().contains("bill")) sawPayable = true;
        }
        assertTrue(sawPayable, "Payable (AP) filter should show payable items (Vendor Bills), but none were listed.");
    }

    @Test(description = "11.4 Outstanding report: the reference search returns no rows for a non-existent term")
    @Severity(SeverityLevel.NORMAL)
    public void outstandingSearchFilter() {
        login();
        openReport("/reports/outstanding");
        assertTrue(dataRowCount() > 0, "Outstanding report should show data before searching.");

        Locator search = page.locator("input[placeholder*='Search by reference' i], input[placeholder*='reference or counterparty' i]").first();
        assertTrue(search.count() > 0, "Outstanding report should expose a reference search box.");
        search.fill("zzzz-no-such-ref-" + System.currentTimeMillis());
        applyWait();

        assertTrue(showsEmptyState(),
                "Searching a non-existent reference should return no results, but rows remained "
                        + "(" + tableSignature() + ") — the search filter is not working.");
    }

    // ---- filter helpers ----

    private void openReport(String route) {
        page.navigate(ConfigReader.baseUrl() + route);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1500);
    }

    private void applyWait() {
        try { page.waitForLoadState(LoadState.NETWORKIDLE); } catch (Exception ignored) { }
        page.waitForTimeout(1800); // filters auto-apply via a fetch; give it time
    }

    private int dataRowCount() {
        return page.locator("table tbody tr").count();
    }

    /** True when the table body is an empty-state message ("No ... data/found/results") rather than data. */
    private boolean showsEmptyState() {
        String body = (String) page.evaluate(
                "() => { const tb = document.querySelector('table tbody'); return tb ? tb.innerText.toLowerCase() : ''; }");
        return body.contains("no ") && (body.contains("data") || body.contains("found") || body.contains("result"));
    }

    private String tableSignature() {
        return (String) page.evaluate(
                "() => { const r = [...document.querySelectorAll('table tbody tr')];" +
                " return 'rows=' + r.length + ' :: ' + r.slice(0,3).map(x => x.innerText.replace(/\\s+/g,' ').trim().slice(0,50)).join(' | '); }");
    }

    /** Open the combobox whose current label contains {currentLabel} and pick its option at {index}. Returns the picked text. */
    private String pickComboOptionByIndex(String currentLabel, int index) {
        Locator combo = page.locator("button[role='combobox']")
                .filter(new Locator.FilterOptions().setHasText(currentLabel)).first();
        assertTrue(combo.count() > 0, "Expected a '" + currentLabel + "' filter control.");
        combo.click();
        page.waitForTimeout(700);
        Locator opts = page.locator("[role='option']");
        opts.first().waitFor(new Locator.WaitForOptions().setTimeout(6000));
        String text = opts.nth(index).innerText().trim();
        opts.nth(index).click();
        page.waitForTimeout(500);
        return text;
    }

    /** Open the combobox matching {currentLabel} and pick the option whose text equals {optionText}. */
    private void pickComboOptionByText(Pattern currentLabel, String optionText) {
        Locator combo = page.locator("button[role='combobox']")
                .filter(new Locator.FilterOptions().setHasText(currentLabel)).first();
        assertTrue(combo.count() > 0, "Expected a filter control matching " + currentLabel);
        combo.click();
        page.waitForTimeout(700);
        Locator opt = page.locator("[role='option']")
                .filter(new Locator.FilterOptions().setHasText(optionText)).first();
        opt.waitFor(new Locator.WaitForOptions().setTimeout(6000));
        opt.click();
        page.waitForTimeout(500);
    }
}
