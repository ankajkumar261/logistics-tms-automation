package com.portfolio.logistics.tests.settings;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.pages.AppNavigator;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.regex.Pattern;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 13 — Settings")
public class SettingsTest extends BaseTest {

    private void login() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);
    }

    private Locator saveChangesBtn() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("Save Changes", Pattern.CASE_INSENSITIVE))).first();
    }

    @Test(description = "13.1 Profile: form loads with editable fields and 'Save Changes' enables on edit (not saved — shared admin account)")
    @Severity(SeverityLevel.NORMAL)
    public void profileFormEditable() {
        login();
        page.navigate(ConfigReader.baseUrl() + "/settings/profile");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        Locator phone = page.locator("input[name='phone']").first();
        phone.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));

        // Editable profile fields present.
        assertTrue(page.locator("input[name='firstName']").count() > 0
                        && page.locator("input[name='lastName']").count() > 0
                        && page.locator("input[name='email']").count() > 0,
                "Expected editable profile fields (first name, last name, email).");

        // Editing enables the save affordance (form is saveable). Field restored without saving.
        String original = phone.inputValue();
        phone.fill("+1 (555) 555-0100");
        Locator save = saveChangesBtn();
        save.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        boolean saveEnabled = save.isEnabled();
        phone.fill(original); // revert; nothing was saved

        assertTrue(saveEnabled, "Editing the profile should enable 'Save Changes' (13.1 form is editable/saveable).");
    }

    @Test(description = "13.2 Company profile: edit a field, Save Changes, then revert it back to the original value")
    @Severity(SeverityLevel.NORMAL)
    public void companyProfileEditSaveRevert() {
        login();
        page.navigate(ConfigReader.baseUrl() + "/settings/company");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1500);

        Locator companyName = page.locator("input[name='companyName']").first();
        companyName.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        // Wait for the form to populate its values from the API before editing.
        for (int i = 0; i < 40 && companyName.inputValue().isBlank(); i++) page.waitForTimeout(200);
        boolean formLoaded = !companyName.inputValue().isBlank();

        Locator website = page.locator("input[name='website']").first();
        String original = website.inputValue();
        String qa = "https://qa-" + (System.currentTimeMillis() % 100000) + ".example.com";

        // Edit + Save.
        website.fill(qa);
        Locator save = saveChangesBtn();
        save.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        boolean savedOk = false;
        if (save.isEnabled()) {
            save.click();
            page.waitForTimeout(2500);
            savedOk = page.getByText(Pattern.compile("saved|updated|success", Pattern.CASE_INSENSITIVE)).count() > 0;
        }

        // Revert to the original value and save again — leave the company profile unchanged.
        try {
            page.navigate(ConfigReader.baseUrl() + "/settings/company");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(1500);
            Locator cn = page.locator("input[name='companyName']").first();
            for (int i = 0; i < 25 && cn.inputValue().isBlank(); i++) page.waitForTimeout(200);
            page.locator("input[name='website']").first().fill(original);
            Locator save2 = saveChangesBtn();
            if (save2.count() > 0 && save2.isEnabled()) { save2.click(); page.waitForTimeout(2000); }
        } catch (Exception ignored) { }

        assertTrue(formLoaded && savedOk,
                "13.2 company profile edit -> save -> revert did not complete. Form pre-filled its fields=" + formLoaded
                + ", save confirmed=" + savedOk + ". Known app issue: /settings/company fetches /api/company (HTTP 200) "
                + "but does NOT populate the form inputs, so the required Company Name stays blank and 'Save Changes' "
                + "is silently blocked (no request fires). Not a test-automation defect.");
    }

    @Test(description = "13.3 Change password page renders (not exercised — would rotate the login credential)")
    @Severity(SeverityLevel.NORMAL)
    public void changePasswordPage() {
        login();
        AppNavigator nav = new AppNavigator(page);
        assertTrue(nav.renders("/settings/change-password", "Change Password"),
                "Change Password page should render");
    }
}
