package com.portfolio.logistics.tests.roles.accounts;

import com.portfolio.logistics.model.Role;
import com.portfolio.logistics.pages.SettlementsPage;
import com.portfolio.logistics.tests.roles.RoleTestBase;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * ACCOUNTS — Driver Settlements. The app has no "generate settlement" affordance (a known gap the
 * SuperAdmin SettlementCreateTest already flags), so this covers what IS available for the role:
 * the settlements list and a settlement's detail/actions.
 */
@Epic("Logistics TMS TMS")
@Feature("Roles — ACCOUNTS")
public class AccountsSettlementsTest extends RoleTestBase {

    @Test(description = "ACCOUNTS can use the Driver Settlements list (count, filter, reset)")
    @Severity(SeverityLevel.NORMAL)
    public void settlementsList() {
        signIn(Role.ACCOUNTS);
        SettlementsPage settlements = new SettlementsPage(page);
        settlements.open();
        assertTrue(settlements.totalResults() > 0, "ACCOUNTS should see settlements in the list.");
        String firstNo = settlements.firstSettlementNo();
        assertTrue(!firstNo.isBlank(), "The first settlement row should show a settlement number.");
        assertTrue(settlements.filterBySettlementNo(firstNo) >= 1 && settlements.tableContains(firstNo),
                "Filtering by " + firstNo + " should keep it in the list.");
        settlements.reset();
    }

    @Test(description = "ACCOUNTS can open a settlement's detail with summary/net and actions")
    @Severity(SeverityLevel.NORMAL)
    public void settlementDetail() {
        signIn(Role.ACCOUNTS);
        SettlementsPage settlements = new SettlementsPage(page);
        settlements.open();
        settlements.openFirstSettlement();

        // The detail's summary + action buttons render asynchronously; the page object's checks are
        // instant count()s, so poll until the detail settles before asserting (avoids a flaky miss).
        for (int i = 0; i < 20; i++) {
            if (settlements.detailShowsSummaryAndNet() && settlements.detailHasAdjustAndFinalizeActions()) break;
            page.waitForTimeout(500);
        }
        assertTrue(settlements.detailShowsSummaryAndNet(),
                "ACCOUNTS should see the settlement's summary and net on its detail.");
        assertTrue(settlements.detailHasAdjustAndFinalizeActions(),
                "ACCOUNTS should see Adjust/Finalize actions on a settlement.");
    }
}
