package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.model.Truck;
import com.portfolio.logistics.utils.ListSearch;

import java.util.List;
import java.util.regex.Pattern;

/**
 * C-Panel → Trucks. Field ids confirmed against the live Add New Truck dialog:
 * unitNumber, year, vin, make, model, licensePlate, odometer, registrationExpiry.
 * Registration Expiry ships pre-filled, and the dialog has no Status control.
 */
public class TrucksPage {

    private final Page page;

    public TrucksPage(Page page) {
        this.page = page;
    }

    private Locator addTruckButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add Truck"));
    }

    private Locator dialog() {
        return page.locator("div[role='dialog']");
    }

    public void openTrucks() {
        // Navigate straight to the list — clicking the C-Panel nav menu is fragile under parallel
        // load. Retry once on the SPA net::ERR_ABORTED redirect race.
        try {
            page.navigate(ConfigReader.baseUrl() + "/cpanel/trucks", new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        } catch (Exception first) {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
            page.navigate(ConfigReader.baseUrl() + "/cpanel/trucks", new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        }
        page.waitForURL("**/cpanel/trucks**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    public void clickAddTruck() {
        addTruckButton().first().click();
        // Wait on a field, not just the dialog box: the dialog element appears before its form mounts.
        dialog().locator("#unitNumber").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
    }

    public void createTruck(Truck t) {
        Locator d = dialog();

        fill(d, "#unitNumber",   t.getUnitNumber());
        fill(d, "#year",         t.getYear());
        fill(d, "#vin",          t.getVin());
        fill(d, "#make",         t.getMake());
        fill(d, "#model",        t.getModel());
        fill(d, "#licensePlate", t.getPlate());
        // Odometer (mi)* — added to the form; it submits as 0 when blank, so fill it with real data.
        fill(d, "#odometer",     t.getOdometer());

        d.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^Add Truck$", Pattern.CASE_INSENSITIVE))).last().click();

        try {
            d.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN).setTimeout(10000));
        } catch (TimeoutError e) {
            List<String> errors = d.locator("[id$='-form-item-message'], [role='alert'], .text-destructive")
                    .allInnerTexts();
            throw new AssertionError(
                    "Add Truck dialog didn't close after submit — the save was rejected. "
                            + "Validation messages: " + errors);
        }
    }

    /**
     * Fill a field that the form is expected to have. A missing field is a real finding (the form
     * changed), so it fails here rather than being logged and walked past.
     */
    private void fill(Locator scope, String selector, String value) {
        Locator field = scope.locator(selector);
        if (field.count() == 0) {
            throw new AssertionError("Add Truck dialog has no field '" + selector
                    + "' — the form has changed. Fields present: " + fieldIds(scope));
        }
        field.first().fill(value);
    }

    private String fieldIds(Locator scope) {
        return String.valueOf(scope.evaluate(
                "el => [...el.querySelectorAll('input,textarea,select')].map(f => f.id || f.name || f.placeholder)"));
    }

    public boolean isTruckPresent(String unitNumber) {
        return rowFor(unitNumber) != null;
    }

    /**
     * The Trucks list row for {unitNumber} as one line of text, or null if absent. The row carries the
     * Unit #, VIN, Make/Model, Year, License Plate, Status and Odometer columns, so a test can assert
     * that a submitted value actually persisted — not merely that the record exists.
     */
    public String rowFor(String unitNumber) {
        page.navigate(ConfigReader.baseUrl() + "/cpanel/trucks");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        return ListSearch.rowTextAcrossPages(page, unitNumber);
    }
}
