package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.util.regex.Pattern;

public class OrderEditPage {

    private final Page page;

    public OrderEditPage(Page page) {
        this.page = page;
    }

    /** Open the first order in the list, then click Edit to reach /orders/:id/edit. */
    public String openFirstOrderForEdit() {
        // Retry once on the transient SPA net::ERR_ABORTED redirect race (worse under parallel load).
        try {
            page.navigate(ConfigReader.baseUrl() + "/orders");
        } catch (Exception first) {
            page.waitForTimeout(1500);
            page.navigate(ConfigReader.baseUrl() + "/orders");
        }
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator firstRow = page.locator("table tbody tr").first();
        firstRow.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
        String orderNumber = firstRow.locator("td").first().innerText().trim();
        firstRow.click();
        page.waitForURL("**/orders/**");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Edit$"))).first().click();
        page.waitForURL("**/edit");
        page.locator("input[name='rate']").waitFor();
        return orderNumber;
    }

    /** Change the rate, satisfy any blank required selects, and save. Returns the new rate. */
    public String changeRate() {
        String newRate = String.valueOf(1000 + new java.util.Random().nextInt(9000));
        Locator rate = page.locator("input[name='rate']").first();
        rate.fill(newRate);

        // Weight is blank on orders created via the wizard; fill it so it can't block the save.
        Locator weight = page.locator("input[name='weight']").first();
        if (weight.count() > 0 && weight.inputValue().isBlank()) weight.fill("5000");

        // The edit form loads with Shipment Type and BOTH origin & destination
        // country/state/city/ZIP dropdowns reset to their "Select…" placeholders — all required.
        // Fill every placeholder combobox (they're dependent: country→state→city→ZIP, so re-query
        // and always take the first still-unset one).
        fillPlaceholderCombos();

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("Save Changes", Pattern.CASE_INSENSITIVE))).first().click();

        try {
            page.waitForURL(u -> !u.endsWith("/edit"),
                    new Page.WaitForURLOptions().setTimeout(12000));
        } catch (Exception e) {
            java.util.List<String> errs = page.locator("[id$='-form-item-message'], .text-destructive").allInnerTexts();
            System.out.println("[OrderEditPage] Save blocked. Validation: " + errs + " | URL=" + page.url());
        }
        page.waitForTimeout(1500);
        return newRate;
    }

    /** Fill every form combobox still showing a "Select…" placeholder by opening it and picking the
     *  first option. Re-queries each pass because the pickers are dependent (Shipment Type, then
     *  origin/destination country → state → city → ZIP become selectable in turn). */
    private void fillPlaceholderCombos() {
        for (int i = 0; i < 14; i++) {
            Locator combos = page.locator("form button[role='combobox']");
            int n = combos.count(), idx = -1;
            for (int j = 0; j < n; j++) {
                if (safeText(combos.nth(j)).toLowerCase().startsWith("select")) { idx = j; break; }
            }
            if (idx < 0) return; // nothing left on a placeholder

            Locator combo = combos.nth(idx);
            combo.scrollIntoViewIfNeeded();
            combo.click();
            page.waitForTimeout(500);
            Locator opt = page.locator("[cmdk-item][role='option'], [role='option']").first();
            try {
                opt.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(4000));
                opt.click();
                page.waitForTimeout(500);
            } catch (Exception e) {
                System.out.println("[OrderEditPage] No option for placeholder combo #" + idx);
                page.keyboard().press("Escape");
                return; // avoid spinning on an unfillable combo
            }
        }
    }

    private String safeText(Locator l) {
        try { return l.innerText().trim(); } catch (Exception e) { return ""; }
    }

    /** Verify the detail page shows the new rate. The detail renders it as "CA$8,733.00",
     *  so match the digits ignoring the thousands separator; poll while the page re-renders. */
    public boolean detailShowsRate(String rateValue) {
        for (int i = 0; i < 16; i++) {
            try {
                String body = (String) page.evaluate("() => document.body ? document.body.innerText : ''");
                if (body.replace(",", "").contains(rateValue)) return true;
            } catch (Exception ignored) { }
            page.waitForTimeout(500);
        }
        return false;
    }
}