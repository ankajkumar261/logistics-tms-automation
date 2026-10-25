package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrderDetailPage {

    private final Page page;

    public OrderDetailPage(Page page) {
        this.page = page;
    }


    /** True if a Confirm action is currently available on the detail page. */
    public boolean hasConfirmButton() {
        Locator confirm = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("^Confirm$", Pattern.CASE_INSENSITIVE)));
        page.waitForTimeout(800); // let action buttons render
        return confirm.count() > 0 && confirm.first().isVisible();
    }

    /** Open a specific order's detail via the list (filter by Order#, click the row). */
    public void openOrder(String orderNumber) {
        page.navigate(ConfigReader.baseUrl() + "/orders");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator orderFilter = page.locator("input[placeholder='Order No']").first();
        if (orderFilter.count() > 0) {
            orderFilter.fill(orderNumber);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                    .setName(Pattern.compile("^Search$"))).first().click();
            page.waitForTimeout(1500);
        }
        Locator row = page.locator("table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(orderNumber)).first();
        row.click();
        page.waitForURL("**/orders/**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(800);
    }

    /** Read the "Additional Stops (N)" count from the Shipments section header. */
    public int additionalStopsCount() {
        Locator header = page.getByText(Pattern.compile("Additional Stops \\(\\d+\\)"));
        try {
            String text = header.first().innerText();
            Matcher m = Pattern.compile("\\((\\d+)\\)").matcher(text);
            if (m.find()) return Integer.parseInt(m.group(1).replace(",", ""));
        } catch (Exception ignored) { }
        return -1;
    }

    /** Poll until the Additional Stops count reaches {expected} (the shipments data loads
     *  asynchronously after the detail page navigates). Returns the last count seen. */
    public int waitForAdditionalStops(int expected) {
        int c = additionalStopsCount();
        for (int i = 0; i < 24 && c != expected; i++) {
            page.waitForTimeout(500);
            c = additionalStopsCount();
        }
        return c;
    }

    /** True if a saved stop showing {name} (its Shipper/Consignee name) is visible on the detail page. */
    public boolean hasStopWithName(String name) {
        try {
            Locator match = page.getByText(name, new Page.GetByTextOptions().setExact(false));
            match.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            return match.first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean addStop(String type) {
        return addStop(type, null);
    }

    /** Add a Pickup or Delivery stop to the "Additional Stops" section.
     *  The name field is labeled "Shipper Name" for a Pickup and "Consignee Name" for a
     *  Delivery, but both are the same input (name="name"); pass it via {name}. */
    public boolean addStop(String type, String name) {
        Locator addBtn = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("Add " + type, Pattern.CASE_INSENSITIVE)));
        if (addBtn.count() == 0) {
            System.out.println("[OrderDetailPage] No 'Add " + type + "' button found.");
            return false;
        }
        addBtn.first().scrollIntoViewIfNeeded();
        addBtn.first().click();

        Locator dialog = page.locator("div[role='dialog']");
        dialog.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        page.waitForTimeout(500);

        // Shipper / Consignee name (input[name='name'], placeholder "Company or location name").
        if (name != null && !name.isBlank()) {
            Locator nameField = dialog.locator("input[name='name']").first();
            if (nameField.count() > 0) {
                nameField.fill(name);
            } else {
                System.out.println("[OrderDetailPage] No Shipper/Consignee name field for " + type + ".");
            }
        }

        // Fill the required Address text directly.
        Locator street = dialog.locator("input[name='address'], input[placeholder*='Street address' i]").first();
        if (street.count() > 0 && street.inputValue().isBlank()) {
            street.fill("400 McDonald Street");
        }

        // Country defaults to Canada. Fill State -> City -> ZIP cmdk pickers if on placeholder.
        pickCmdkInDialog(dialog, "State");     // "Select"
        pickCmdkInDialog(dialog, "City");      // "Select city"
        pickCmdkInDialog(dialog, "ZIP");       // "Select ZIP / postal"

        // Scheduled date (native).
        Locator dateField = dialog.locator("input[type='date']").first();
        if (dateField.count() > 0) {
            try { dateField.fill("2026-12-31"); dateField.blur(); } catch (Exception ignored) { }
        }

        // Save.
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                        .setName(Pattern.compile("Add Shipment|Add Stop|Save|^Add$", Pattern.CASE_INSENSITIVE)))
                .last().click();

        try {
            dialog.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN).setTimeout(8000));
            return true;
        } catch (Exception e) {
            java.util.List<String> errs = dialog.locator("[role='alert'], .text-destructive").allInnerTexts();
            System.out.println("[OrderDetailPage] Add Shipment blocked. Validation: " + errs);
            return false;
        }
    }

    /** Open a cmdk picker in the dialog by its field label and click the first item. */
    private void pickCmdkInDialog(Locator dialog, String label) {
        // Trigger button whose nearby label matches; these show placeholder text like "Select".
        Locator trigger = dialog.locator(
                "xpath=.//label[contains(normalize-space(.), '" + label + "')]"
                        + "/following::button[@role='combobox'][1]");
        if (trigger.count() == 0) {
            System.out.println("[OrderDetailPage] No cmdk trigger for " + label);
            return;
        }
        String txt;
        try { txt = trigger.first().innerText().toLowerCase(); } catch (Exception e) { txt = ""; }
        if (!txt.contains("select")) return; // already has a value

        trigger.first().click();
        page.waitForTimeout(600);
        Locator item = page.locator("[cmdk-item][role='option'], [role='option']").first();
        try {
            item.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(6000));
            item.click();
            page.waitForTimeout(500); // State may filter City, City may filter ZIP
        } catch (Exception e) {
            System.out.println("[OrderDetailPage] No item for " + label + ": " + e.getMessage());
        }
    }

    /** Open orders one by one until we find one with a visible "Confirm" button.
     *  Returns that order's number, or null if none in the first page qualify. */
    /** Filter to Pending orders, open the first, confirm it has a Confirm button. */
    public String openConfirmableOrder() {
        page.navigate(ConfigReader.baseUrl() + "/orders");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Filter Status = Pending via the "All Statuses" dropdown.
        Locator statusFilter = page.locator("button[role='combobox']:has-text('All Statuses'), select").first();
        try {
            if (statusFilter.count() > 0) {
                statusFilter.click();
                page.waitForTimeout(400);
                Locator pendingOpt = page.getByRole(AriaRole.OPTION,
                        new Page.GetByRoleOptions().setName(Pattern.compile("^Pending$", Pattern.CASE_INSENSITIVE)));
                if (pendingOpt.count() > 0) { pendingOpt.first().click(); page.waitForTimeout(1500); }
            }
        } catch (Exception e) {
            System.out.println("[OrderDetailPage] Could not apply Pending filter: " + e.getMessage());
        }

        Locator rows = page.locator("table tbody tr");
        if (rows.count() == 0) {
            System.out.println("[OrderDetailPage] No orders after Pending filter.");
            return null;
        }

        int rowCount = rows.count();
        for (int i = 0; i < rowCount; i++) {
            Locator row = page.locator("table tbody tr").nth(i);
            String orderNumber = row.locator("td").first().innerText().trim();

            row.click();
            page.waitForURL("**/orders/**");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(800);

            Locator confirm = page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(Pattern.compile("^Confirm$", Pattern.CASE_INSENSITIVE)));
            if (confirm.count() > 0 && confirm.first().isVisible()) {
                System.out.println("[OrderDetailPage] Confirmable order: " + orderNumber);
                return orderNumber;
            }
            System.out.println("[OrderDetailPage] " + orderNumber + " buttons: " + page.locator("button").allInnerTexts());

            page.goBack();
            page.waitForLoadState(LoadState.NETWORKIDLE);
        }
        System.out.println("[OrderDetailPage] No confirmable order found.");
        return null;
    }
    /** Click the "Confirm" button on the order detail. */
    public boolean clickConfirm() {
        Locator confirm = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("^Confirm$", Pattern.CASE_INSENSITIVE)));
        if (confirm.count() == 0) {
            System.out.println("[OrderDetailPage] No 'Confirm' button on this order.");
            return false;
        }
        confirm.first().click();
        page.waitForTimeout(1500);

        // Accept a confirmation dialog if one appears.
        Locator dialogConfirm = page.locator("div[role='dialog']").getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(Pattern.compile("Confirm|Yes|OK", Pattern.CASE_INSENSITIVE)));
        if (dialogConfirm.count() > 0) {
            try { dialogConfirm.first().click(); page.waitForTimeout(1500); } catch (Exception ignored) { }
        }
        page.waitForTimeout(1000);
        return true;
    }

    /** Go to the list, search the order #, and check its Status cell shows "Confirmed". */
    public boolean listShowsStatus(String orderNumber, String expectedStatus) {
        page.navigate(ConfigReader.baseUrl() + "/orders");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // Filter by Order# so only our row remains.
        Locator orderFilter = page.locator("input[placeholder='Order No']").first();
        if (orderFilter.count() > 0) {
            orderFilter.fill(orderNumber);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                    .setName(Pattern.compile("^Search$"))).first().click();
            page.waitForTimeout(1500);
        }

        // The matching row's status cell should read the expected status.
        Locator row = page.locator("table tbody tr")
                .filter(new Locator.FilterOptions().setHasText(orderNumber)).first();
        try {
            Locator statusPill = row.getByText(expectedStatus,
                    new Locator.GetByTextOptions().setExact(false));
            statusPill.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            return statusPill.first().isVisible();
        } catch (Exception e) {
            return false;
        }


    }
}