package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.model.Carrier;

public class CarriersPage {

    private final Page page;

    public CarriersPage(Page page) {
        this.page = page;
    }

    // --- Navigation ---
    private Locator cPanelMenu() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("C-Panel"));
    }

    private Locator carriersLink() {
        return page.locator("a[href='/cpanel/carriers']");
    }

    private Locator addCarrierButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add Carrier"));
    }

    /** The Add Carrier modal — scope form lookups here to avoid list-page clashes. */
    private Locator dialog() {
        return page.locator("div[role='dialog']");
    }

    public void openCarriers() {
        // Navigate straight to the list. Clicking the C-Panel nav menu is fragile — the menu button
        // can render slowly / be absent under parallel load, timing out. Retry once on the SPA
        // net::ERR_ABORTED redirect race.
        try {
            page.navigate(ConfigReader.baseUrl() + "/cpanel/carriers", new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        } catch (Exception first) {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
            page.navigate(ConfigReader.baseUrl() + "/cpanel/carriers", new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        }
        page.waitForURL("**/cpanel/carriers**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    public void clickAddCarrier() {
        addCarrierButton().first().click();
        dialog().locator("input[name='companyName']").waitFor();
    }

    public void createCarrier(Carrier c) {
        Locator d = dialog();

        // Company Name is the only truly required text field.
        d.locator("input[name='companyName']").fill(c.getCompanyName());

        // The rest are optional — filled for coverage, guarded just in case.
        fillIfPresent(d, "input[name='mcNumber']",    c.getMcNumber());
        fillIfPresent(d, "input[name='dotNumber']",   c.getDotNumber());
        fillIfPresent(d, "input[name='contactName']", c.getContactName());
        fillIfPresent(d, "input[name='phone']",       c.getPhone());
        fillIfPresent(d, "input[name='email']",       c.getEmail());
        fillIfPresent(d, "input[name='address']",     c.getAddress());

        // Country defaults to Canada; Payment Terms / Currency / Status default
        // to Net 30 / CAD / Active. State / City / ZIP are optional -> skipped.

        d.locator("button[type='submit']").click();
    }

    private void fillIfPresent(Locator scope, String selector, String value) {
        Locator field = scope.locator(selector);
        if (field.count() > 0) {
            field.first().fill(value);
        } else {
            System.out.println("[CarriersPage] Field not found (skipped): " + selector);
        }
    }

    public boolean isCarrierPresent(String companyName) {
        page.navigate(ConfigReader.baseUrl() + "/cpanel/carriers");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(700);
        // No search box — page through (new rows land on later pages).
        return com.portfolio.logistics.utils.ListSearch.containsAcrossPages(page, companyName);
    }
}