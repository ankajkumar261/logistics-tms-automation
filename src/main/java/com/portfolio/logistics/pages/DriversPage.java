package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.model.Driver;
import com.portfolio.logistics.utils.ListSearch;

import java.util.List;
import java.util.regex.Pattern;

/**
 * C-Panel → Drivers. Field ids confirmed against the live Add New Driver dialog:
 * firstName, lastName, licenseNumber, licenseExpiry, phone, email, hireDate, loginUsername,
 * loginPassword. Only First Name / Last Name / License Number carry a required marker.
 */
public class DriversPage {

    private final Page page;

    public DriversPage(Page page) {
        this.page = page;
    }

    private Locator addDriverButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add Driver"));
    }

    private Locator dialog() {
        return page.locator("div[role='dialog']");
    }

    public void openDrivers() {
        // Navigate straight to the list — clicking the C-Panel nav menu is fragile under parallel
        // load. Retry once on the SPA net::ERR_ABORTED redirect race.
        try {
            page.navigate(ConfigReader.baseUrl() + "/cpanel/drivers", new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        } catch (Exception first) {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
            page.navigate(ConfigReader.baseUrl() + "/cpanel/drivers", new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        }
        page.waitForURL("**/cpanel/drivers**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    public void clickAddDriver() {
        addDriverButton().first().click();
        dialog().locator("#firstName").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
    }

    /** Fill and submit the Add Driver dialog. License Expiry is filled only when the model carries one. */
    public void createDriver(Driver dr) {
        Locator d = dialog();

        fill(d, "#firstName",     dr.getFirstName());
        fill(d, "#lastName",      dr.getLastName());
        fill(d, "#licenseNumber", dr.getLicenseNumber());
        fill(d, "#phone",         dr.getPhone());
        fill(d, "#email",         dr.getEmail());
        // The form does not mark License Expiry required, so the model decides whether it is set.
        if (dr.hasLicenseExpiry()) {
            fill(d, "#licenseExpiry", dr.getLicenseExpiry());
        }
        // Hire Date and the optional driver login are left blank.

        submit(d);
    }

    /** Click "Add Driver" and wait for the dialog to close; throws with the validation text if it doesn't. */
    public void submit(Locator d) {
        d.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^Add Driver$", Pattern.CASE_INSENSITIVE))).last().click();
        try {
            d.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN).setTimeout(10000));
        } catch (TimeoutError e) {
            List<String> errors = d.locator("[id$='-form-item-message'], [role='alert'], .text-destructive")
                    .allInnerTexts();
            throw new AssertionError(
                    "Add Driver dialog didn't close after submit — the save was rejected. "
                            + "Validation messages: " + errors);
        }
    }

    /**
     * Fill and submit the Add Driver dialog WITHOUT asserting that it closed. Use this when the test
     * judges the outcome from the API response rather than from the dialog — so a rejected save
     * produces the API evidence instead of a generic "dialog didn't close" error.
     */
    public boolean createDriverExpectingApiResult(Driver dr) {
        Locator d = dialog();

        fill(d, "#firstName",     dr.getFirstName());
        fill(d, "#lastName",      dr.getLastName());
        fill(d, "#licenseNumber", dr.getLicenseNumber());
        fill(d, "#phone",         dr.getPhone());
        fill(d, "#email",         dr.getEmail());
        if (dr.hasLicenseExpiry()) {
            fill(d, "#licenseExpiry", dr.getLicenseExpiry());
        }

        return submitAndReportClosed();
    }

    /** Submit without asserting the outcome — for tests that inspect the API response themselves. */
    public boolean submitAndReportClosed() {
        Locator d = dialog();
        d.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^Add Driver$", Pattern.CASE_INSENSITIVE))).last().click();
        try {
            d.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN).setTimeout(10000));
            return true;
        } catch (TimeoutError e) {
            return false;
        }
    }

    /** Validation messages currently shown inside the open dialog. */
    public List<String> validationMessages() {
        return dialog().locator("[id$='-form-item-message'], [role='alert'], .text-destructive").allInnerTexts();
    }

    private void fill(Locator scope, String selector, String value) {
        Locator field = scope.locator(selector);
        if (field.count() == 0) {
            throw new AssertionError("Add Driver dialog has no field '" + selector
                    + "' — the form has changed. Fields present: " + String.valueOf(scope.evaluate(
                    "el => [...el.querySelectorAll('input,textarea,select')].map(f => f.id || f.name)")));
        }
        field.first().fill(value);
    }

    public boolean isDriverPresent(String fullName) {
        return rowFor(fullName) != null;
    }

    /** The Drivers list row for {needle} as one line of text, or null if absent. */
    public String rowFor(String needle) {
        page.navigate(ConfigReader.baseUrl() + "/cpanel/drivers");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        return ListSearch.rowTextAcrossPages(page, needle);
    }
}
