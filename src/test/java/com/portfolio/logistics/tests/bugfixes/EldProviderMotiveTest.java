package com.portfolio.logistics.tests.bugfixes;

import com.microsoft.playwright.Locator;
import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.pages.EldPage;
import com.portfolio.logistics.pages.LoginPage;
import com.portfolio.logistics.utils.ApiWatcher;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertTrue;

/**
 * FIX-06 — the ELD provider value for Motive/KeepTruckin did not match between the picker and the
 * backend, so adding a Motive provider was rejected. Choosing "Motive (KeepTruckin)" in the form
 * must now produce a provider the backend accepts and lists.
 */
@Epic("Logistics TMS TMS")
@Feature("Bug fixes — ELD Integration")
public class EldProviderMotiveTest extends BaseTest {

    private static final String MOTIVE = "Motive (KeepTruckin)";

    @Test(description = "FIX-06 Adding a Motive (KeepTruckin) ELD provider is accepted by the backend")
    @Severity(SeverityLevel.CRITICAL)
    public void motiveProviderIsAccepted() {
        new LoginPage(page).establishAdminSession();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

        EldPage eld = new EldPage(page);
        eld.open();

        Locator dialog = eld.openAddProviderDialog();

        List<String> options = eld.providerTypeOptions(dialog);
        assertTrue(options.contains(MOTIVE),
                "The Add ELD Provider form should offer '" + MOTIVE + "'. Options: " + options);

        String name = "QA-Motive-" + (System.currentTimeMillis() % 1_000_000L);
        eld.fillProvider(dialog, name, MOTIVE, "QAKEY" + (System.currentTimeMillis() % 1_000_000L));
        boolean closed = eld.submitProvider(dialog);

        // The API answer is the evidence: a provider-value mismatch shows up as a rejected payload,
        // while the dialog only renders bare "*" markers that say nothing about the cause.
        ApiWatcher.Call create = api.requireCall("/api/eld/providers");
        assertTrue(create.ok(),
                "Selecting '" + MOTIVE + "' produced a providerType the backend rejects — the picker's "
                        + "value and the API's accepted values do not match.\n" + create
                        + "\nDialog closed: " + closed
                        + "\nDialog validation shown: " + eld.validationMessages(dialog));

        assertTrue(eld.providerListed(name),
                "ELD provider '" + name + "' was accepted by the API but is not listed under ELD Providers.");
    }
}
