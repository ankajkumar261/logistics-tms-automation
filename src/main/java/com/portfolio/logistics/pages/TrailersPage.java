package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.model.Trailer;

import java.util.List;

public class TrailersPage {

    private final Page page;

    public TrailersPage(Page page) {
        this.page = page;
    }

    // --- Navigation ---
    private Locator cPanelMenu() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("C-Panel"));
    }

    private Locator trailersLink() {
        return page.locator("a[href='/cpanel/trailers']");
    }

    private Locator addTrailerButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add Trailer"));
    }

    private Locator dialog() {
        return page.locator("div[role='dialog']");
    }

    public void openTrailers() {
        // Navigate straight to the list — clicking the C-Panel nav menu is fragile under parallel
        // load. Retry once on the SPA net::ERR_ABORTED redirect race.
        try {
            page.navigate(ConfigReader.baseUrl() + "/cpanel/trailers", new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        } catch (Exception first) {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
            page.navigate(ConfigReader.baseUrl() + "/cpanel/trailers", new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        }
        page.waitForURL("**/cpanel/trailers**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    public void clickAddTrailer() {
        addTrailerButton().first().click();
        dialog().waitFor();
        page.waitForTimeout(400);
    }

    public void createTrailer(Trailer t) {
        Locator d = dialog();

        fillFirst(d, t.getUnitNumber(),
                "#unitNumber", "input[name='unitNumber']", "#trailerNumber", "input[name='trailerNumber']", "#unit", "input[name='unit']");
        fillFirst(d, t.getMake(),  "#make",  "input[name='make']");
        fillFirst(d, t.getYear(),  "#year",  "input[name='year']");
        fillFirst(d, t.getVin(),   "#vin",   "input[name='vin']");
        fillFirst(d, t.getPlate(),
                "#licensePlate", "input[name='licensePlate']", "#plate", "input[name='plate']", "#plateNumber", "input[name='plateNumber']");

        // Trailer Type may be a Radix select or a text field — handle both.
        if (!selectRadixByLabel(d, "Type", t.getType())) {
            fillFirst(d, t.getType(), "#type", "input[name='type']", "#trailerType", "input[name='trailerType']");
        }
        selectRadixByLabel(d, "Status", "Active");

        clickSubmit(d);

        try {
            d.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN).setTimeout(8000));
        } catch (TimeoutError e) {
            List<String> errors = d.locator("[id$='-form-item-message'], [role='alert'], .text-destructive")
                    .allInnerTexts();
            throw new AssertionError(
                    "Add Trailer dialog didn't close after submit — save likely blocked. "
                            + "Validation messages: " + errors);
        }
    }

    private void fillFirst(Locator scope, String value, String... selectors) {
        for (String sel : selectors) {
            Locator f = scope.locator(sel);
            if (f.count() > 0) { f.first().fill(value); return; }
        }
        System.out.println("[TrailersPage] No field matched for '" + value + "' among: " + String.join(", ", selectors));
    }

    private void clickSubmit(Locator d) {
        Locator submit = d.locator("button[type='submit']");
        if (submit.count() > 0) { submit.first().click(); return; }
        d.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Add Trailer")).click();
    }

    /** Returns true if a select for this label existed and was used. */
    private boolean selectRadixByLabel(Locator scope, String label, String optionText) {
        Locator trigger = scope.locator(
                "xpath=.//label[contains(normalize-space(.), '" + label + "')]"
                        + "/following::button[@role='combobox'][1]");
        if (trigger.count() == 0) return false;
        trigger.first().click();
        Locator option = page.getByRole(AriaRole.OPTION,
                new Page.GetByRoleOptions().setName(optionText).setExact(false));
        if (option.count() == 0) {          // requested value not in list -> pick first
            option = page.locator("[role='option']");
        }
        option.first().click();
        return true;
    }

    public boolean isTrailerPresent(String unitNumber) {
        page.navigate(ConfigReader.baseUrl() + "/cpanel/trailers");
        page.waitForLoadState();

        Locator search = page.locator("input[type='search'], input[placeholder*='Search' i]");
        if (search.count() > 0) {
            search.first().fill(unitNumber);
            page.waitForTimeout(1000);
        }

        // No search box — page through (new rows land on later pages).
        return com.portfolio.logistics.utils.ListSearch.containsAcrossPages(page, unitNumber);
    }
}