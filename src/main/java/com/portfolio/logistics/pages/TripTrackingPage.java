package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trips → Asset Tracking (/trips/tracking).
 *
 * The left-hand list renders one button per active trip, each headed by a monospace reference. That
 * reference is NOT always a TRP- number — trips dispatched straight from an order are listed under
 * their ORD- number — so cards are located structurally (the button that carries the mono heading)
 * rather than by a TRP- text pattern, which silently missed ORD- cards.
 *
 * Trips whose truck has no live GPS fix still render, with the location resolved approximately from
 * the stop's city and labelled "(approx.)".
 */
public class TripTrackingPage {

    private static final String CARD = "button:has(span.font-mono)";

    private final Page page;

    public TripTrackingPage(Page page) {
        this.page = page;
    }

    public void open() {
        // /trips/tracking occasionally net::ERR_ABORTs from SPA routing — retry once.
        try {
            page.navigate(ConfigReader.baseUrl() + "/trips/tracking");
        } catch (Exception e) {
            page.waitForTimeout(1500);
            page.navigate(ConfigReader.baseUrl() + "/trips/tracking");
        }
        page.waitForLoadState(LoadState.NETWORKIDLE);
        // The trip list and the map hydrate after the first paint; wait for a card rather than a
        // fixed pause, so a slow environment doesn't read as "no trips".
        try {
            page.locator(CARD).first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        } catch (Exception noCards) {
            // Leave it to the test's assertions to report an empty list.
        }
        page.waitForTimeout(500);
    }

    public boolean pageLoaded() {
        return page.url().contains("/trips/tracking")
                && page.locator("main, body").first().isVisible();
    }

    /** True if a map container rendered (Leaflet). */
    public boolean hasMap() {
        Locator map = page.locator(".leaflet-container");
        return map.count() > 0 && map.first().isVisible();
    }

    public boolean hasHeading(String text) {
        Locator h = page.getByRole(AriaRole.HEADING,
                new Page.GetByRoleOptions().setName(Pattern.compile(text, Pattern.CASE_INSENSITIVE)));
        try {
            h.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            return h.first().isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    /** Number of trip cards in the left list, whatever reference prefix they carry. */
    public int activeTripCount() {
        return page.locator(CARD).count();
    }

    /** One line of text per trip card — reference, status, driver, truck and location. */
    public List<String> activeTripSummaries() {
        return page.locator(CARD).allInnerTexts().stream()
                .map(s -> s.replaceAll("\\s+", " ").trim())
                .toList();
    }

    /**
     * The count the page itself advertises ("N active trips"), or -1 if that banner is absent.
     * Comparing it against the rendered cards catches trips being dropped from the list.
     */
    public int advertisedTripCount() {
        Locator banner = page.getByText(Pattern.compile("\\d+ active trips?", Pattern.CASE_INSENSITIVE));
        if (banner.count() == 0) return -1;
        Matcher m = Pattern.compile("(\\d+) active trips?", Pattern.CASE_INSENSITIVE)
                .matcher(banner.first().innerText());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    /** Trip cards whose location was resolved approximately because no live GPS fix exists. */
    public int approximateLocationCount() {
        return (int) activeTripSummaries().stream().filter(s -> s.contains("(approx.)")).count();
    }

    /** Click the first trip card; returns its reference (e.g. "ORD-1098" or "TRP-2026-5103"). */
    public String clickFirstTrip() {
        return clickTrip(page.locator(CARD).first());
    }

    /**
     * Click the first trip whose location is approximate — i.e. one with no latitude/longitude —
     * returning its reference. Returns null when every listed trip has a live fix.
     */
    public String clickFirstApproximateTrip() {
        Locator card = page.locator(CARD)
                .filter(new Locator.FilterOptions().setHasText("(approx.)")).first();
        if (card.count() == 0) return null;
        return clickTrip(card);
    }

    private String clickTrip(Locator card) {
        card.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
        String reference = card.locator("span.font-mono").first().innerText().trim();
        card.click();
        page.waitForTimeout(1500); // let the map recenter / detail panel render
        return reference;
    }

    /** True if the map rendered a marker/route overlay for the selection. */
    public boolean hasMapOverlay() {
        Locator overlay = page.locator(
                ".leaflet-marker-icon, .leaflet-interactive, .leaflet-overlay-pane path");
        try {
            overlay.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            return overlay.count() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** True if the page shows a legitimate "no active trips" empty state rather than trip cards. */
    public boolean hasEmptyState() {
        return page.getByText(java.util.regex.Pattern.compile(
                "no active trips|no trips|no vehicles|no data", java.util.regex.Pattern.CASE_INSENSITIVE))
                .count() > 0;
    }

    /** The whole tracking panel's text — used to assert the selected trip's detail rendered. */
    public String panelText() {
        return page.locator("main").first().innerText().replaceAll("\\s+", " ").trim();
    }

    /** True if the page shows a crash/error boundary instead of the tracking UI. */
    public boolean showsErrorBoundary() {
        return page.getByText(Pattern.compile(
                        "Something went wrong|Application error|Unable to load|TypeError|Cannot read",
                        Pattern.CASE_INSENSITIVE)).count() > 0;
    }
}
