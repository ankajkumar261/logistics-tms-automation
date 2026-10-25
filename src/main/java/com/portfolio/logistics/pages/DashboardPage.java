package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Post-login shell. Minimal for now — confirms we landed on /dashboard and
 * exposes logout. Grows as we cover more modules.
 */
public class DashboardPage {

    private final Page page;

    public DashboardPage(Page page) {
        this.page = page;
    }

    public boolean isLoaded() {
        return page.url().contains("/dashboard");
    }

    public void logout() {
        // Open user menu (three-dot button)
        page.locator("button[aria-label='User menu']").click();

        // Wait for the menu's Sign Out to render before clicking — clicking immediately after
        // opening the menu is racy (the dropdown animates in) and times out on slower page loads.
        Locator signOut = page.locator("button:has-text('Sign Out')").first();
        signOut.waitFor(new Locator.WaitForOptions()
                .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE).setTimeout(10000));
        signOut.click();
    }
}
