package com.portfolio.logistics.tests.bugfixes;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Driver;
import com.portfolio.logistics.pages.DriversPage;
import com.portfolio.logistics.pages.LoginPage;
import com.portfolio.logistics.utils.ApiWatcher;
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
 * FIX-03 — "Driver Creation: License Expiry validation mismatch".
 *
 * The defect was a disagreement between the Add Driver form and the backend: the form let a driver
 * be submitted without a License Expiry, then the backend rejected it (HTTP 400). The fix makes the
 * two agree. This test therefore verifies AGREEMENT rather than a fixed direction, so it stays
 * correct however the fix was resolved (make the field required in the form, or accept it as
 * optional on the backend):
 *
 *   - submit WITHOUT License Expiry — the form and backend must reach the SAME verdict:
 *       * the form blocks it client-side (no request fires, a "required" message shows), OR
 *       * the form submits it AND the backend accepts it (2xx).
 *     The one combination that fails is the original bug: the form submits but the backend 400s.
 *   - submit WITH License Expiry — must always succeed and persist the date.
 */
@Epic("Logistics TMS TMS")
@Feature("Bug fixes — Drivers")
public class DriverLicenseExpiryTest extends BaseTest {

    private DriversPage openAddDriver() {
        new LoginPage(page).establishAdminSession();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
        DriversPage drivers = new DriversPage(page);
        drivers.openDrivers();
        drivers.clickAddDriver();
        return drivers;
    }

    @Test(description = "FIX-03 Add Driver form and backend agree on License Expiry (no optional/required mismatch)")
    @Severity(SeverityLevel.CRITICAL)
    public void formAndBackendAgreeOnLicenseExpiry() {
        DriversPage drivers = openAddDriver();

        Driver dr = Driver.sampleWithoutLicenseExpiry();
        boolean dialogClosed = drivers.createDriverExpectingApiResult(dr);
        page.waitForTimeout(1500); // give a submitted request time to be recorded

        Optional<ApiWatcher.Call> maybeCall = api.lastCall("/api/drivers");
        List<String> validation = drivers.validationMessages();

        if (maybeCall.isPresent()) {
            // The form let it through — the backend must agree and accept it, or the mismatch persists.
            ApiWatcher.Call call = maybeCall.get();
            assertTrue(call.ok(),
                    "VALIDATION MISMATCH: the Add Driver form submitted a driver without a License "
                            + "Expiry, but the backend rejected it — form and backend disagree.\n" + call);
            assertNotNull(drivers.rowFor(dr.getFullName()),
                    "Driver '" + dr.getFullName() + "' was accepted by the backend but is not in the list.");
        } else {
            // The form blocked it client-side — that is a consistent state (the user can never reach a
            // backend rejection). Confirm it really blocked on License Expiry, not on something else.
            assertTrue(!dialogClosed,
                    "No create request fired, yet the Add Driver dialog closed — the save silently "
                            + "vanished. Validation shown: " + validation);
            assertTrue(mentionsExpiry(validation),
                    "The form blocked the save without a License Expiry but showed no matching "
                            + "'License expiry is required' message. Messages: " + validation);
            System.out.println("[FIX-03] License Expiry is consistently required (form blocks; backend never 400s).");
        }
    }

    @Test(description = "FIX-03 A driver WITH License Expiry saves and keeps the date")
    @Severity(SeverityLevel.CRITICAL)
    public void driverSavesWithLicenseExpiry() {
        DriversPage drivers = openAddDriver();

        Driver dr = Driver.sample();   // carries licenseExpiry 2030-01-15
        drivers.createDriver(dr);

        ApiWatcher.Call create = api.requireCall("/api/drivers");
        assertTrue(create.ok(), "Creating driver '" + dr.getFullName() + "' failed.\n" + create);
        assertTrue(create.sent("licenseExpiry", dr.getLicenseExpiry()),
                "The Add Driver request did not carry the License Expiry '" + dr.getLicenseExpiry()
                        + "' that was entered.\n" + create);
        assertTrue(create.responseBody.contains(dr.getLicenseExpiry()),
                "The created driver came back without License Expiry '" + dr.getLicenseExpiry() + "'.\n" + create);

        assertNotNull(drivers.rowFor(dr.getFullName()),
                "Driver '" + dr.getFullName() + "' did not appear in the Drivers list after creation.");
    }

    private static boolean mentionsExpiry(List<String> messages) {
        return messages.stream().anyMatch(m -> {
            String l = m.toLowerCase();
            return l.contains("expiry") || l.contains("expiration") || l.contains("license");
        });
    }
}
