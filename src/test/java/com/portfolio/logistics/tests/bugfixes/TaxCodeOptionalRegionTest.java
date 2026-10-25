package com.portfolio.logistics.tests.bugfixes;

import com.microsoft.playwright.Locator;
import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.pages.DialogAdder;
import com.portfolio.logistics.pages.LoginPage;
import com.portfolio.logistics.utils.ApiWatcher;
import com.portfolio.logistics.utils.ListSearch;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * FIX-05 — "Tax Code: the optional Region field is incorrectly required".
 *
 * The defect was a disagreement between the Add Tax Code form and the backend over the Region /
 * Province / State field, so a tax code that looked submittable was rejected (HTTP 400). The fix
 * makes the two agree. Like FIX-03, this verifies AGREEMENT rather than a fixed direction:
 *
 *   - submit WITHOUT Region — the form and backend must reach the SAME verdict (form blocks with a
 *     "required" message and no request fires, OR the form submits and the backend accepts 2xx).
 *     The failing combination is the original bug: the form submits but the backend 400s.
 *   - submit WITH Region — must always succeed and persist the Region.
 */
@Epic("Logistics TMS TMS")
@Feature("Bug fixes — Tax Codes")
public class TaxCodeOptionalRegionTest extends BaseTest {

    private DialogAdder login() {
        new LoginPage(page).establishAdminSession();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);
        return new DialogAdder(page);
    }

    private long ts() { return System.currentTimeMillis() % 100_000L; }

    @Test(description = "FIX-05 Add Tax Code form and backend agree on Region (no optional/required mismatch)")
    @Severity(SeverityLevel.CRITICAL)
    public void formAndBackendAgreeOnRegion() {
        DialogAdder a = login();
        a.open("/integrations/tax-codes");

        long t = ts();
        String code = "QA" + t;
        Locator dlg = a.openDialog("Add Tax Code");
        a.fill(dlg, "HST-NB", code);                 // Tax Code*
        a.fill(dlg, "Harmonized", "QA Tax " + t);    // Name*
        a.fillNumber(dlg, "7");                      // Rate (%)*
        // Region deliberately left blank.
        boolean dialogClosed = a.submit(dlg, "Add Tax Code");
        page.waitForTimeout(1500);

        Optional<ApiWatcher.Call> maybeCall = api.lastCall("/api/qb/tax-codes");
        List<String> validation = dlg.locator("[id$='-form-item-message'], [role='alert'], .text-destructive").allInnerTexts();

        if (maybeCall.isPresent()) {
            ApiWatcher.Call call = maybeCall.get();
            assertTrue(call.ok(),
                    "VALIDATION MISMATCH: the Add Tax Code form submitted a tax code without a Region, "
                            + "but the backend rejected it — form and backend disagree.\n" + call);
            assertNotNull(taxCodeRow(code),
                    "Tax code '" + code + "' was accepted by the backend but is not in the Tax Codes list.");
        } else {
            assertTrue(!dialogClosed,
                    "No create request fired, yet the Add Tax Code dialog closed — the save silently "
                            + "vanished. Validation shown: " + validation);
            assertTrue(mentionsRegion(validation),
                    "The form blocked the save without a Region but showed no matching 'Region is "
                            + "required' message. Messages: " + validation);
            System.out.println("[FIX-05] Region is consistently required (form blocks; backend never 400s).");
        }
    }

    @Test(description = "FIX-05 A tax code with a Region saves and keeps the Region")
    @Severity(SeverityLevel.NORMAL)
    public void taxCodeSavesWithRegion() {
        DialogAdder a = login();
        a.open("/integrations/tax-codes");

        long t = ts();
        String code = "QR" + t;
        String region = "Ontario, CA";
        Locator dlg = a.openDialog("Add Tax Code");
        a.fill(dlg, "HST-NB", code);
        a.fill(dlg, "Harmonized", "QA Tax " + t);
        a.fillNumber(dlg, "7");
        a.fill(dlg, "New Brunswick", region);        // Region / Province / State
        a.submit(dlg, "Add Tax Code");

        ApiWatcher.Call create = api.requireCall("/api/qb/tax-codes");
        assertTrue(create.ok(), "Creating tax code '" + code + "' failed.\n" + create);
        assertTrue(create.sent("region", region),
                "The Add Tax Code request did not carry the Region '" + region + "'.\n" + create);

        String row = taxCodeRow(code);
        assertNotNull(row, "Tax code '" + code + "' did not appear in the Tax Codes list after creation.");
        assertTrue(row.contains(region),
                "The Tax Codes list row for '" + code + "' does not show Region '" + region
                        + "'. Row read: " + row);
    }

    private String taxCodeRow(String code) {
        page.navigate(ConfigReader.baseUrl() + "/integrations/tax-codes");
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        return ListSearch.rowTextAcrossPages(page, code);
    }

    private static boolean mentionsRegion(List<String> messages) {
        return messages.stream().anyMatch(m -> {
            String l = m.toLowerCase();
            return l.contains("region") || l.contains("province") || l.contains("state");
        });
    }
}
