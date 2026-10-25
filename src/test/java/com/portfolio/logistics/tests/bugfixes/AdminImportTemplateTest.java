package com.portfolio.logistics.tests.bugfixes;

import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
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
 * BUG-012 — Admin Panel → Import Data → Generic CSV → Customers → Download Template should download a
 * file (previously failed with "Site wasn't available"). The template is served from
 * /api/import/templates/customer.
 */
@Epic("Logistics TMS TMS")
@Feature("Bug fixes — Admin Import")
public class AdminImportTemplateTest extends BaseTest {

    @Test(description = "BUG-012 Admin Import: the Customer CSV template downloads")
    @Severity(SeverityLevel.NORMAL)
    public void customerTemplateDownloads() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        page.navigate(ConfigReader.baseUrl() + "/admin/import");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1500);

        // Pick the "Generic CSV (one entity at a time)" source; Customers is the default entity.
        page.getByText(Pattern.compile("Generic CSV", Pattern.CASE_INSENSITIVE)).first().click();
        page.waitForTimeout(1000);

        Locator download = page.locator("a:has-text('Download template'), button:has-text('Download template')").first();
        assertTrue(download.count() > 0, "Expected a 'Download template' control on the Import page.");

        Download file = page.waitForDownload(new Page.WaitForDownloadOptions().setTimeout(15000),
                () -> download.click());
        String name = file.suggestedFilename();
        assertTrue(name != null && !name.isBlank(),
                "Customer CSV template should download, but no file was produced (BUG-012). Got: " + name);
    }
}
