package com.portfolio.logistics.utils;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Logistics TMS-specific UI helpers, ported from the demo-recorder work where these
 * selector problems were already solved. These are the reusable "quirk handlers"
 * the Logistics TMS frontend needs; page objects build on top of them.
 *
 * NOTE: unlike the demo recorder's Director (no assertions), these are plain
 * interaction helpers — tests do the asserting.
 */
public final class TmsHelpers {

    private final Page page;

    public TmsHelpers(Page page) {
        this.page = page;
    }

    /**
     * The sidebar collapses to a click-to-expand state; a collapsed group is
     * detected by the Tailwind grid class `grid-rows-[0fr]`. Call this before
     * clicking any nested sidebar link, otherwise the target isn't hittable.
     */
    public void openNav(String groupLabel) {
        Locator group = page.locator("button, a")
                .filter(new Locator.FilterOptions().setHasText(groupLabel))
                .first();
        // If the group's expandable region is collapsed, click the header to open it.
        Locator collapsed = page.locator("[class*='grid-rows-[0fr]']");
        if (collapsed.count() > 0 && group.count() > 0) {
            group.click();
            page.waitForTimeout(300);
        }
    }

    /**
     * Masked date inputs (inputmode="numeric") reject fill()/pressSequentially.
     * The reliable path is the calendar popover: open it, then click the day cell
     * addressed by data-day. Pass the ISO date (yyyy-MM-dd).
     *
     * @param triggerSelector selector that opens the date popover (the field/button)
     * @param isoDate         target date as yyyy-MM-dd
     */
    public void setDate(String triggerSelector, String isoDate) {
        page.locator(triggerSelector).first().click();
        Locator day = page.locator("td[data-day='" + isoDate + "'] button").first();
        day.waitFor();
        day.click();
    }

    /**
     * Radix Select that HAS a native <select> mirror — drive the mirror directly
     * by visible label. This is the fast, stable path when the mirror exists.
     */
    public void selectRadixOption(String nativeSelectSelector, String visibleLabel) {
        page.locator(nativeSelectSelector).first()
                .selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(visibleLabel));
    }

    /**
     * Radix trigger with NO native mirror (e.g. Customer pickers). Click the
     * trigger, then pick the option from the portal-rendered listbox by role.
     * Options render at document root, so target them globally by role.
     */
    public void selectByRoleOption(String triggerSelector, String optionText) {
        page.locator(triggerSelector).first().click();
        page.getByRole(AriaRole.OPTION,
                        new Page.GetByRoleOptions().setName(optionText).setExact(false))
                .first().click();
    }

    /**
     * Radix Popover + cmdk command dialog (aria-haspopup="dialog"), used by the
     * C-Panel address State/City/ZIP fields. Open the trigger, type into the
     * command input to filter, then click the matching item.
     */
    public void selectCmdkDialog(String triggerSelector, String typeText, String itemText) {
        page.locator(triggerSelector).first().click();
        Locator input = page.locator("[cmdk-input], input[role='combobox']").first();
        input.waitFor();
        input.fill(typeText);
        page.waitForTimeout(250);
        page.locator("[cmdk-item], [role='option']")
                .filter(new Locator.FilterOptions().setHasText(itemText))
                .first().click();
    }
}
