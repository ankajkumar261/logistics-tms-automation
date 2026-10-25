package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TripsPage {

    private final Page page;

    public TripsPage(Page page) {
        this.page = page;
    }

    public void startNewTrip() {
        navigateWithRetry(ConfigReader.baseUrl() + "/trips/new");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.locator("form").first().waitFor(
                new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(30000));
    }

    /** Navigate, retrying once on transient ERR_ABORTED (SPA redirect race). */
    private void navigateWithRetry(String url) {
        try {
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        } catch (Exception first) {
            System.out.println("[TripsPage] Nav aborted, retrying: " + first.getMessage());
            try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        }
    }
    /** Open an optional equipment combobox by its label and pick the first real option. */
    private void pickEquipment(String labelText) {
        try {
            Locator trigger = page.locator(
                    "xpath=//label[contains(normalize-space(.), '" + labelText + "')]"
                            + "/following::*[@role='combobox'][1]");
            if (trigger.count() == 0) {
                System.out.println("[TripsPage] No combobox for: " + labelText);
                return;
            }
            trigger.first().scrollIntoViewIfNeeded();
            trigger.first().click();
            page.waitForTimeout(600);

            Locator options = page.locator("[role='option'], [cmdk-item]");
            options.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
            // index 1 = first real option (index 0 is often "No driver"/placeholder); fall back to 0.
            (options.count() > 1 ? options.nth(1) : options.first()).click();
            page.waitForTimeout(400);
            page.keyboard().press("Escape"); // make sure it closes before the next field
            page.waitForTimeout(200);
        } catch (Exception e) {
            System.out.println("[TripsPage] Could not fill " + labelText + " (skipped): " + e.getMessage());
            try { page.keyboard().press("Escape"); } catch (Exception ignored) { }
        }
    }
    /** Create a trip from the first available order. Returns the generated Trip # from the list. */
    public String createTrip() {
        // Select Order (required) — cmdk dialog picker.
        Locator orderTrigger = page.locator("form button[role='combobox'][aria-haspopup='dialog']").first();
        orderTrigger.scrollIntoViewIfNeeded();
        orderTrigger.click();
        page.waitForTimeout(800);

        // Pick the first order in the list.
        Locator orderOption = page.locator("[cmdk-item][role='option'], [role='option']").first();
        orderOption.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        orderOption.click();
        page.waitForTimeout(800);
        // --- Driver & Equipment (all optional) — fill each, closing after ---
        pickEquipment("Assign Driver");
        pickEquipment("Assign Truck");
        pickEquipment("Assign Trailer");
        pickEquipment("Assign Carrier");
        page.keyboard().press("Escape");   // ensure nothing overlays the date fields
        page.waitForTimeout(300);

        // Required schedule: Planned Departure / Planned Arrival. Each is a masked date input whose
        // TYPED value does not commit to the form (the mask reverts on blur) — the value only sticks
        // when chosen through the calendar popover. The times default to 06:00 AM / 06:00 PM, so we
        // only set the two dates. The app disables a lead-time window (~20 days), so pick beyond it.
        LocalDate departure = LocalDate.now().plusDays(25);
        LocalDate arrival = departure.plusDays(3);
        for (int attempt = 0; attempt < 3; attempt++) {
            pickPlannedDate(0, departure); // Planned Departure calendar
            pickPlannedDate(1, arrival);   // Planned Arrival calendar
            // Confirm the form registered both dates before submitting; the calendar occasionally
            // doesn't commit under load, so re-pick rather than submit an incomplete form.
            boolean stillMissing = page.locator("[id$='-form-item-message'], .text-destructive")
                    .filter(new Locator.FilterOptions().setHasText(
                            Pattern.compile("required", Pattern.CASE_INSENSITIVE))).count() > 0
                    || page.locator("input[placeholder='MM/DD/YYYY']").first().inputValue().replaceAll("[^0-9]", "").length() < 8;
            if (!stillMissing) break;
            page.waitForTimeout(300);
        }

        // Create the trip.
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                        .setName(Pattern.compile("Create Trip|Create|Save|Submit", Pattern.CASE_INSENSITIVE)))
                .first().click();

        // After creation the app redirects to the trips list (leaving /trips/new).
        try {
            page.waitForURL(u -> !u.contains("/trips/new"),
                    new Page.WaitForURLOptions().setTimeout(15000));
        } catch (Exception e) {
            java.util.List<String> errs = page.locator("[id$='-form-item-message'], .text-destructive").allInnerTexts();
            System.out.println("[TripsPage] Did not leave /trips/new. Validation: " + errs + " | URL=" + page.url());
            return null;
        }
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1500);

        // Grab the newest TRP-#### from the top of the trips list.
        String tripNumber = null;
        Locator trpCell = page.getByText(Pattern.compile("TRP-\\d+")).first();
        try {
            trpCell.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            Matcher m = Pattern.compile("TRP-\\d+").matcher(trpCell.innerText());
            if (m.find()) tripNumber = m.group();
        } catch (Exception ignored) { }

        System.out.println("[TripsPage] Created trip = " + tripNumber + " | URL=" + page.url());
        return tripNumber;
    }
    /** Pick a planned date through the field's calendar popover (index 0 = departure, 1 = arrival).
     *  Typing into the masked input does not commit to react-hook-form (the mask reverts on blur), so
     *  the date must be selected from the calendar. Advances the month view until the target day is a
     *  selectable in-month cell (the app disables past dates and a lead-time window). */
    /** Set a planned date by filling its masked MM/DD/YYYY input. Playwright's fill() commits the
     *  value to react-hook-form reliably (the calendar popover is flaky under parallel load, and
     *  keystroke/JS-setter approaches don't register). index 0 = Departure, 1 = Arrival. */
    private boolean pickPlannedDate(int fieldIndex, LocalDate target) {
        String mmddyyyy = String.format("%02d/%02d/%04d",
                target.getMonthValue(), target.getDayOfMonth(), target.getYear());
        Locator input = page.locator("input[placeholder='MM/DD/YYYY']").nth(fieldIndex);
        try {
            input.scrollIntoViewIfNeeded();
            input.fill(mmddyyyy);
            page.waitForTimeout(200);
            if (input.inputValue().replaceAll("[^0-9]", "").length() >= 8) {
                System.out.println("[TripsPage] Set planned date '" + mmddyyyy + "' (field " + fieldIndex + ")");
                return true;
            }
        } catch (Exception e) {
            System.out.println("[TripsPage] Could not set planned date '" + mmddyyyy + "': " + e.getMessage().split("\n")[0]);
        }
        return false;
    }

    private String extractTripNumber() {
        Pattern p = Pattern.compile("(TRP|TRIP)-\\d+", Pattern.CASE_INSENSITIVE);
        try {
            String h = page.locator("h1").first().innerText();
            Matcher m = p.matcher(h);
            if (m.find()) return m.group();
        } catch (Exception ignored) { }
        Matcher m = p.matcher(page.url());
        return m.find() ? m.group() : null;
    }

    /** Verify by searching the trips list; falls back to "we reached a trip detail page". */
    public boolean isTripCreated(String tripNumber) {
        page.navigate(ConfigReader.baseUrl() + "/trips");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        if (tripNumber != null) {
            Locator search = page.locator("input[type='search'], input[placeholder*='Search' i]").first();
            if (search.count() > 0) {
                search.fill(tripNumber);
                page.waitForTimeout(1200);
            }
            Locator match = page.getByText(tripNumber, new Page.GetByTextOptions().setExact(false));
            try {
                match.first().waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
                return match.first().isVisible();
            } catch (Exception e) {
                return false;
            }
        }
        // No trip number captured but we left /trips/new onto a detail page = created.
        return page.url().matches(".*/trips/[^/]+$") && !page.url().endsWith("/new");
    }
}