package com.portfolio.logistics.tests.accounting;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.SettlementsPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 6 — Accounting: Settlements")
public class SettlementListTest extends BaseTest {

    @Test(description = "6 Settlements list: totals, filter by Settlement#, and search work")
    @Severity(SeverityLevel.NORMAL)
    public void settlementsList() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        SettlementsPage settlements = new SettlementsPage(page);
        settlements.open();

        int total = settlements.totalResults();
        assertTrue(total > 0, "Expected a positive settlement count, got " + total);

        String firstNo = settlements.firstSettlementNo();
        assertTrue(!firstNo.isBlank(), "Expected the first row to show a settlement number");

        int filtered = settlements.filterBySettlementNo(firstNo);
        assertTrue(filtered >= 1, "Filtering by " + firstNo + " should return at least 1 row, got " + filtered);
        assertTrue(settlements.tableContains(firstNo), "Filtered list should still contain " + firstNo);
        settlements.reset();
    }
}
