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
public class SettlementTest extends BaseTest {

    @Test(description = "6.1/6.2 Settlement detail shows line items + net and exposes adjust + finalize actions")
    @Severity(SeverityLevel.CRITICAL)
    public void settlementDetailAndActions() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        SettlementsPage settlements = new SettlementsPage(page);
        settlements.open();
        String no = settlements.openFirstSettlement();

        // 6.1 EXP: settlement shows line items + net.
        assertTrue(settlements.detailShowsSummaryAndNet(),
                "Expected settlement " + no + " detail to show Settlement Summary, Adjustments and Net.");

        // 6.2: adjust + finalize actions are available (not executed — that would finalize a real settlement).
        assertTrue(settlements.detailHasAdjustAndFinalizeActions(),
                "Expected settlement " + no + " detail to expose 'Raise adjustment' and a finalize action "
                + "(Approve Settlement / Mark as Paid).");
    }
}
