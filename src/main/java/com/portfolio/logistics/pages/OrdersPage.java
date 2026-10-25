package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SelectOption;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.model.Order;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OrdersPage {

    private final Page page;

    /** Saved locations chosen during the wizard — exposed so tests can assert
     *  Origin and Destination are not the same place. */
    private String originLocation;
    private String destinationLocation;

    public OrdersPage(Page page) {
        this.page = page;
    }

    public String getOriginLocation()      { return originLocation; }
    public String getDestinationLocation() { return destinationLocation; }

    public void startNewOrder() {
        // /orders/new occasionally net::ERR_ABORTs on the first hit (SPA redirect race, worse under
        // parallel load) — retry once before letting it surface.
        try {
            page.navigate(ConfigReader.baseUrl() + "/orders/new", new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        } catch (Exception first) {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
            page.navigate(ConfigReader.baseUrl() + "/orders/new", new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        }
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("form").first().waitFor();
    }

    private Locator nextButton() {
        return page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("^Next$")));
    }

    /** Fill a field only if it exists — guards optional-field name guesses. */
    private void fillIfPresent(String selector, String value) {
        Locator f = page.locator(selector);
        if (f.count() > 0) {
            f.first().fill(value);
        } else {
            System.out.println("[OrdersPage] Optional field not found (skipped): " + selector);
        }
    }

    /** Full 4-step wizard. Returns the generated Order # (ORD-####) for verification. */
    public String createOrder(Order o) {
        String orderNo = o.getReference();

        // ---- STEP 1: Basic Info ----
        // Shipment Type: Radix with native <select> mirror -> drive the mirror.
        Locator shipmentMirror = page.locator("select").first();
        try { shipmentMirror.selectOption(new SelectOption().setValue("ftl")); } catch (Exception ignored) { }

        // Customer: the picker INSIDE the form (not the header org-switcher).
        Locator customerTrigger = page.locator("form button[role='combobox'][aria-haspopup='dialog']").first();
        customerTrigger.scrollIntoViewIfNeeded();
        customerTrigger.click();
        page.waitForTimeout(800);

        Locator searchInput = page.locator("input[placeholder*='Search customer' i]").first();
        searchInput.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));

        // Pick the FIRST available customer — works on any environment/dataset (don't assume a
        // specific customer like "QA-Customer" exists here).
        Locator custOption = page.locator("[cmdk-item][role='option'], [role='option']");
        custOption.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        custOption.first().click();
        page.waitForTimeout(500);

        // Customer Order # (required, unique).
        page.locator("input[name='referenceNumber']").fill(orderNo);

        // Optional fields — filled for full coverage (treated as mandatory in QA).
        fillIfPresent("input[name='internalReference']", "REF-" + orderNo);
        fillIfPresent("input[name='pickupNumber']", "PU-" + orderNo);
        fillIfPresent("input[name='deliveryNumber']", "DL-" + orderNo);
        fillIfPresent("input[name='rate']", "2500");

        clickNextAndWait("Origin");

        // ---- STEP 2: Origin ----
        originLocation = pickSavedLocation(0, null);
        setMaskedDateIfEmpty("Pickup date", "8/10/2026");
        clickNextAndWait("Destination");

        // ---- STEP 3: Destination ----
        // Pick a DIFFERENT saved location than Origin — otherwise the order is
        // shipped from-and-to the same place (the bug this fixes).
        destinationLocation = pickSavedLocation(1, originLocation);
        setMaskedDateIfEmpty("Delivery date", "12/31/2026");
        clickNextAndWait("Cargo");

        // ---- STEP 4: Cargo ----
        page.locator("input[name='commodity']").waitFor();
        // Weight* became a required field on the Cargo step — fill it (Weight Unit defaults to lbs).
        page.locator("input[name='weight']").first().fill("5000");
        page.locator("input[name='commodity']").fill("Auto Parts");

        // Submit Order (type=submit).
        page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName(Pattern.compile("Submit Order")))
                .click();

        // On success we land on the order detail page (URL leaves /orders/new).
        try {
            page.waitForURL(u -> !u.contains("/orders/new"),
                    new Page.WaitForURLOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("[OrdersPage] Did not leave /orders/new after submit. URL=" + page.url());
            return null;
        }
        page.waitForTimeout(1000);

        // Capture the generated Order # (e.g. ORD-1020) from the detail heading, or the URL.
        String orderNumber = null;
        try {
            Locator heading = page.locator("h1").filter(
                    new Locator.FilterOptions().setHasText(Pattern.compile("ORD-\\d+")));
            String text = heading.first().innerText().trim();
            Matcher m = Pattern.compile("ORD-\\d+").matcher(text);
            if (m.find()) orderNumber = m.group();
        } catch (Exception ignored) { }
        if (orderNumber == null) {
            Matcher m = Pattern.compile("ORD-\\d+").matcher(page.url());
            if (m.find()) orderNumber = m.group();
        }
        System.out.println("[OrdersPage] Created order = " + orderNumber + " (Cust Order # " + orderNo + ")");
        return orderNumber;
    }

    /** Open the saved-location picker (pickup on Origin, delivery on Destination) and choose an
     *  entry, which auto-fills Street + State + City + ZIP. Returns the chosen option's label so
     *  callers can guarantee Origin and Destination differ.
     *
     *  @param preferredIndex which saved location to pick (clamped to the list size)
     *  @param avoid          if the preferred entry equals this label, pick a different one instead */
    private String pickSavedLocation(int preferredIndex, String avoid) {
        Locator locTrigger = page.locator("button[role='combobox']:has-text('saved')").first();
        if (locTrigger.count() == 0) {
            locTrigger = page.locator("form button[role='combobox'][aria-haspopup='dialog']").first();
        }
        locTrigger.scrollIntoViewIfNeeded();
        locTrigger.click();
        page.waitForTimeout(800);

        Locator options = page.locator("[cmdk-item][role='option'], [role='option']");
        options.first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));

        int count = options.count();
        int chosen = Math.max(0, Math.min(preferredIndex, count - 1));
        String label = optionLabel(options.nth(chosen));

        // Never reuse the Origin location for the Destination (or vice-versa).
        if (avoid != null && label.equalsIgnoreCase(avoid) && count > 1) {
            for (int i = 0; i < count; i++) {
                String l = optionLabel(options.nth(i));
                if (!l.equalsIgnoreCase(avoid)) { chosen = i; label = l; break; }
            }
        }

        options.nth(chosen).click();
        page.waitForTimeout(1200); // let address fields auto-populate
        return label;
    }

    private String optionLabel(Locator option) {
        try {
            return option.innerText().trim().replaceAll("\\s+", " ");
        } catch (Exception e) {
            return "";
        }
    }

    /** Masked date input (inputmode=numeric): fill only if empty. Target by aria-label. */
    private void setMaskedDateIfEmpty(String ariaLabel, String mmddyyyy) {
        Locator date = page.locator("input[aria-label='" + ariaLabel + "']").first();
        if (date.count() == 0) return;
        try {
            if (date.inputValue().isBlank()) {
                date.click();
                date.pressSequentially(mmddyyyy, new Locator.PressSequentiallyOptions().setDelay(60));
            }
        } catch (Exception e) {
            System.out.println("[OrdersPage] Could not set " + ariaLabel + ": " + e.getMessage());
        }
    }

    private void clickNextAndWait(String nextStepHint) {
        nextButton().first().click();
        page.waitForTimeout(1200); // let the next step render
    }

    /** Verify by searching the Orders tab for the generated Order # (ORD-####). */
    public boolean isOrderPresent(String orderNumber) {
        if (orderNumber == null) return false;

        page.navigate(ConfigReader.baseUrl() + "/orders");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        Locator ordersTab = page.getByRole(AriaRole.TAB,
                new Page.GetByRoleOptions().setName(Pattern.compile("^Orders$")));
        if (ordersTab.count() > 0) {
            ordersTab.first().click();
            page.waitForTimeout(600);
        }

        // The plain "Search..." box above the table.
        Locator search = page.locator("input[placeholder='Search...'], input[placeholder*='Search' i]").first();
        search.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        search.fill(orderNumber);
        page.waitForTimeout(1500);

        Locator match = page.getByText(orderNumber, new Page.GetByTextOptions().setExact(false));
        try {
            match.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            return match.first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }
}