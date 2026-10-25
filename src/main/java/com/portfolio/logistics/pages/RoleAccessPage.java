package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.portfolio.logistics.config.ConfigReader;

import java.util.regex.Pattern;

/**
 * Checks role-based access: the app gates forbidden pages with an "Access Restricted" screen,
 * so a route is "restricted" for the current user if that screen appears.
 */
public class RoleAccessPage {

    private final Page page;

    public RoleAccessPage(Page page) {
        this.page = page;
    }

    /** Navigate to {route} and return true if the app shows its "Access Restricted" gate. */
    public boolean isRestricted(String route) {
        page.navigate(ConfigReader.baseUrl() + route);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(800); // the gate is client-rendered
        Locator gate = page.getByText(Pattern.compile("Access Restricted", Pattern.CASE_INSENSITIVE));
        return gate.count() > 0 && gate.first().isVisible();
    }
}
