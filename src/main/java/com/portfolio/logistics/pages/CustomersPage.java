package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.model.Customer;

public class CustomersPage {

    private final Page page;

    public CustomersPage(Page page) {
        this.page = page;
    }

    // --- Navigation ---
    private Locator cPanelMenu() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("C-Panel"));
    }

    private Locator customersLink() {
        return page.locator("a[href='/cpanel/customers']");
    }

    private Locator addCustomerButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Add Customer"));
    }

    // --- Confirmed form fields (by id) ---
    private Locator companyName() { return page.locator("#companyName"); }
    private Locator invoiceCode() { return page.locator("#code"); }
    private Locator contactName() { return page.locator("#contactName"); }
    private Locator phone()       { return page.locator("#phone"); }
    private Locator email()       { return page.locator("#email"); }
    private Locator street()      { return page.locator("#street"); }
    private Locator creditLimit() { return page.locator("#creditLimit"); }

    public void openCustomers() {
        // Navigate straight to the list — clicking the C-Panel nav menu is fragile under parallel
        // load. Retry once on the SPA net::ERR_ABORTED redirect race.
        try {
            page.navigate(ConfigReader.baseUrl() + "/cpanel/customers", new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        } catch (Exception first) {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
            page.navigate(ConfigReader.baseUrl() + "/cpanel/customers", new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        }
        page.waitForURL("**/cpanel/customers**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    public void clickAddCustomer() {
        addCustomerButton().first().click();
        companyName().waitFor();
    }

    public void createCustomer(Customer c) {
        companyName().fill(c.getCompanyName());
        invoiceCode().fill(c.getInvoiceCode());
        contactName().fill(c.getContactName());
        phone().fill(c.getPhone());
        email().fill(c.getEmail());
        street().fill(c.getStreet());
        creditLimit().fill(c.getCreditLimit());

        selectFirstOption("Country");
        selectFirstOption("State");
        selectFirstOption("City");
        selectFirstOption("ZIP");

        addCustomerButton().last().click();
    }

    private void selectFirstOption(String label) {
        try {
            Locator trigger = page.getByRole(AriaRole.COMBOBOX,
                    new Page.GetByRoleOptions().setName(label));
            if (trigger.count() == 0) {
                trigger = page.locator("label:has-text('" + label + "')")
                        .locator("xpath=following::*[@role='combobox' or self::button][1]");
            }
            if (trigger.count() == 0) {
                System.out.println("[CustomersPage] No combobox found for: " + label);
                return;
            }
            trigger.first().click();

            Locator option = page.locator("[role='option'], [cmdk-item]").first();
            option.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
            option.click();
        } catch (Exception e) {
            System.out.println("[CustomersPage] Could not select " + label + ": " + e.getMessage());
        }
    }

    public boolean isCustomerPresent(String companyName) {
        page.navigate(ConfigReader.baseUrl() + "/cpanel/customers");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(700);
        // No search box — page through (new rows land on later pages).
        return com.portfolio.logistics.utils.ListSearch.containsAcrossPages(page, companyName);
    }
}