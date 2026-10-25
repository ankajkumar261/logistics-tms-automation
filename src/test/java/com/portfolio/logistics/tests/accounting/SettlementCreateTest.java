package com.portfolio.logistics.tests.accounting;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SelectOption;
import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.regex.Pattern;

import static org.testng.Assert.assertTrue;

/**
 * BUG-006 (fixed) — the app now has a Create Settlement form at /accounting/settlements/new.
 * This creates a driver settlement for a pay period and verifies it is saved and listed.
 */
@Epic("Logistics TMS TMS")
@Feature("Module 6 — Accounting: Settlements")
public class SettlementCreateTest extends BaseTest {

    @Test(description = "6.1 Generate settlement: create a driver settlement and verify it is saved")
    @Severity(SeverityLevel.CRITICAL)
    public void generateSettlement() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        page.navigate(ConfigReader.baseUrl() + "/accounting/settlements/new");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1200);

        // Driver (required) — first real option; Settlement # is auto-generated.
        page.locator("select").first().selectOption(new SelectOption().setIndex(1));
        String settlementNo = page.locator("input[name='settlementNumber']").inputValue();
        assertTrue(settlementNo != null && !settlementNo.isBlank(),
                "Settlement # should be auto-populated on the create form.");

        page.locator("input[name='periodStart']").fill("2026-07-01");
        page.locator("input[name='periodEnd']").fill("2026-07-15");
        page.locator("input[name='totalAmount']").fill("2500");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("Create Settlement", Pattern.CASE_INSENSITIVE))).first().click();

        // On success the app leaves /new (to the settlement detail).
        try {
            page.waitForURL(u -> u.contains("/accounting/settlements") && !u.endsWith("/new"),
                    new Page.WaitForURLOptions().setTimeout(12000));
        } catch (Exception e) {
            java.util.List<String> errs = page.locator("[role='alert'], .text-destructive").allInnerTexts();
            assertTrue(false, "Create Settlement did not complete for " + settlementNo + ". Validation: " + errs);
        }
        page.waitForTimeout(1000);

        // Verify it now appears in the settlements list.
        page.navigate(ConfigReader.baseUrl() + "/accounting/settlements");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1500);
        Locator match = page.getByText(settlementNo, new Page.GetByTextOptions().setExact(false));
        assertTrue(match.count() > 0,
                "Newly created settlement '" + settlementNo + "' should appear in the settlements list.");
    }
}
