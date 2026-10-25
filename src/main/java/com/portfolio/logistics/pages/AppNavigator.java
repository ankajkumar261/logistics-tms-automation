package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.util.regex.Pattern;

/**
 * Lightweight navigation/render checker for the display-heavy modules (10 Integrations,
 * 11 Reports, 12 Admin, 14 Public). A route "renders" when: it doesn't bounce to /login,
 * shows no error/denied boundary, and its expected heading is visible.
 */
public class AppNavigator {

    private final Page page;

    public AppNavigator(Page page) {
        this.page = page;
    }

    private void navigateWithRetry(String url) {
        try { page.navigate(url); }
        catch (Exception e) { page.waitForTimeout(1500); page.navigate(url); }
    }

    /** True if {route} loads (authenticated) and shows {headingText}. */
    public boolean renders(String route, String headingText) {
        navigateWithRetry(ConfigReader.baseUrl() + route);
        try { page.waitForLoadState(LoadState.NETWORKIDLE); } catch (Exception ignored) { }
        page.waitForTimeout(700);

        if (page.url().contains("/login")) {
            System.out.println("[AppNavigator] " + route + " -> redirected to /login");
            return false;
        }
        if (page.getByText(Pattern.compile("Something went wrong|Application error|Access denied|Page not found",
                Pattern.CASE_INSENSITIVE)).count() > 0) {
            System.out.println("[AppNavigator] " + route + " -> error/denied page");
            return false;
        }
        Locator h = page.getByText(headingText, new Page.GetByTextOptions().setExact(false));
        try {
            h.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(6000));
            return true;
        } catch (Exception e) {
            System.out.println("[AppNavigator] " + route + " -> heading '" + headingText
                    + "' not found. Headings on page: " + page.locator("h1, h2").allInnerTexts());
            return false;
        }
    }

    /** Assert-style helper: returns a list of "route (expected 'heading')" that failed to render. */
    public java.util.List<String> firstFailures(String... routeThenHeadingPairs) {
        java.util.List<String> failures = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < routeThenHeadingPairs.length; i += 2) {
            String route = routeThenHeadingPairs[i];
            String heading = routeThenHeadingPairs[i + 1];
            if (!renders(route, heading)) failures.add(route + " (expected '" + heading + "')");
        }
        return failures;
    }
}
