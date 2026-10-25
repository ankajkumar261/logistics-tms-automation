package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.model.Incorporation;

import java.util.List;

public class IncorporationsPage {

    private final Page page;

    public IncorporationsPage(Page page) {
        this.page = page;
    }

    private Locator newButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(java.util.regex.Pattern.compile("^(New|Add Incorporation|Add)$",
                        java.util.regex.Pattern.CASE_INSENSITIVE)));
    }

    /** The incorporation modal specifically — NOT the generic dialog (cmdk pickers
     *  are also role=dialog and would clash). Scope by the modal's heading. */
    private Locator modal() {
        return page.locator("div[role='dialog']").filter(
                new Locator.FilterOptions().setHas(
                        page.getByRole(AriaRole.HEADING,
                                new Page.GetByRoleOptions().setName("Create Incorporation"))));
    }

    public void openIncorporations() {
        // /cpanel/incorporations occasionally net::ERR_ABORTs from SPA routing — retry once
        // (same guard InvoicesPage/TripsPage/DialogAdder already use).
        try {
            page.navigate(ConfigReader.baseUrl() + "/cpanel/incorporations");
        } catch (Exception e) {
            page.waitForTimeout(1500);
            page.navigate(ConfigReader.baseUrl() + "/cpanel/incorporations");
        }
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    public void clickAdd() {
        newButton().first().click();
        modal().locator("#name").waitFor();
    }

    public void createIncorporation(Incorporation inc) {
        Locator m = modal();

        m.locator("#name").fill(inc.getName());
        m.locator("#addressLine1").fill("100 Queen St W");

        // Country already defaults to Canada. State/City/ZIP are required cmdk
        // dialogs with no default — drive each: open, then pick first portal item.
        pickCmdk("inc-state");
        pickCmdk("inc-city");
        pickCmdk("inc-zip");

        m.locator("button[type='submit']").click(); // "Save"

        try {
            m.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN).setTimeout(8000));
        } catch (TimeoutError e) {
            List<String> errors = m.locator("[id$='-form-item-message'], .text-destructive").allInnerTexts();
            throw new AssertionError(
                    "Create Incorporation didn't close after Save — save likely blocked. "
                            + "Validation messages: " + errors);
        }
    }

    /**
     * Open a cmdk combobox (State/City/ZIP) by trigger id and pick the first item.
     * The opened popup is a separate role=dialog; we look for its cmdk list at the
     * page level, excluding the incorporation modal.
     */
    private void pickCmdk(String triggerId) {
        try {
            page.locator("#" + triggerId).click();

            // The cmdk search input appears in the popup.
            Locator input = page.locator("[cmdk-input], input[placeholder*='Search' i]").last();
            input.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(6000));
            page.waitForTimeout(700); // let the list populate

            Locator item = page.locator("[cmdk-item]:visible, [role='option']:visible").first();
            item.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(6000));
            item.click();
            page.waitForTimeout(300);
        } catch (Exception e) {
            System.out.println("[IncorporationsPage] Could not pick from " + triggerId + ": " + e.getMessage());
        }
    }

    public boolean isPresent(String needle) {
        page.navigate(ConfigReader.baseUrl() + "/cpanel/incorporations");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator search = page.locator("input[type='search'], input[placeholder*='Search' i]");
        if (search.count() > 0) {
            search.first().fill(needle);
            page.waitForTimeout(1200);
        }

        // No search box — page through (new rows land on later pages).
        return com.portfolio.logistics.utils.ListSearch.containsAcrossPages(page, needle);
    }
}