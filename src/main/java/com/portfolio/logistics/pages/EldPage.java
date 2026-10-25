package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Integrations → ELD Management (/integrations/eld).
 *
 * The Add ELD Provider dialog is a plain form with one Radix select for Provider Type; its options
 * are display labels ("Motive (KeepTruckin)"), which the UI maps to the value it posts. The provider
 * table lists each configured provider with its type.
 */
public class EldPage {

    private final Page page;

    public EldPage(Page page) {
        this.page = page;
    }

    public void open() {
        try {
            page.navigate(ConfigReader.baseUrl() + "/integrations/eld");
        } catch (Exception e) {
            page.waitForTimeout(1500);
            page.navigate(ConfigReader.baseUrl() + "/integrations/eld");
        }
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1200);
    }

    public Locator openAddProviderDialog() {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Add Provider$", Pattern.CASE_INSENSITIVE))).first().click();
        Locator dialog = page.locator("[role='dialog']").first();
        dialog.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
        page.waitForTimeout(500);
        return dialog;
    }

    /** Every Provider Type option the form offers, as displayed. */
    public List<String> providerTypeOptions(Locator dialog) {
        dialog.locator("[role='combobox']").first().click();
        page.locator("[role='option']").first().waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(6000));
        List<String> options = page.locator("[role='option']").allInnerTexts();
        page.keyboard().press("Escape");
        page.waitForTimeout(300);
        return options;
    }

    /** Fill the Add ELD Provider form. {providerType} is the option label, e.g. "Motive (KeepTruckin)". */
    public void fillProvider(Locator dialog, String name, String providerType, String apiKey) {
        dialog.locator("input[placeholder*='Samsara - Main Fleet']").first().fill(name);

        dialog.locator("[role='combobox']").first().click();
        Locator option = page.getByRole(AriaRole.OPTION,
                new Page.GetByRoleOptions().setName(providerType).setExact(true));
        try {
            option.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(6000));
        } catch (Exception e) {
            throw new AssertionError("Provider type '" + providerType + "' is not offered by the "
                    + "Add ELD Provider form. Options: " + page.locator("[role='option']").allInnerTexts());
        }
        option.first().click();
        page.waitForTimeout(400);

        dialog.locator("input[placeholder*='Enter API key']").first().fill(apiKey);
    }

    /** Submit the dialog; true when it closed (the UI accepted the save). */
    public boolean submitProvider(Locator dialog) {
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^Add Provider$", Pattern.CASE_INSENSITIVE))).last().click();
        try {
            dialog.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN).setTimeout(10000));
            page.waitForTimeout(800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Validation messages currently shown inside the open dialog. */
    public List<String> validationMessages(Locator dialog) {
        return dialog.locator("[id$='-form-item-message'], [role='alert'], .text-destructive").allInnerTexts();
    }

    /**
     * True if a provider named {name} appears in the ELD Providers section after a reload. The
     * providers render as cards (not a &lt;table&gt;), so this matches on visible text rather than rows.
     */
    public boolean providerListed(String name) {
        open();
        Locator match = page.getByText(name, new Page.GetByTextOptions().setExact(false));
        try {
            match.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
