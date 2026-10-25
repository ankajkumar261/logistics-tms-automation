package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;

import java.util.regex.Pattern;

/**
 * Reusable "open an Add dialog, fill it, submit it" helper for the many CRUD dialogs across the app
 * (integration configs, tenants, users, etc). Dialog inputs mostly lack name attributes, so fields are
 * located by a distinctive placeholder substring, or by input type for the single numeric field.
 */
public class DialogAdder {

    private final Page page;

    public DialogAdder(Page page) {
        this.page = page;
    }

    private void navigateWithRetry(String url) {
        try { page.navigate(url); }
        catch (Exception e) { page.waitForTimeout(1500); page.navigate(url); }
    }

    public void open(String route) {
        navigateWithRetry(ConfigReader.baseUrl() + route);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1200);
    }

    /** Click a button (by name) to open a dialog and return the dialog locator. */
    public Locator openDialog(String openLabel) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile(openLabel, Pattern.CASE_INSENSITIVE))).first().click();
        Locator dlg = page.locator("[role='dialog']").first();
        dlg.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
        page.waitForTimeout(500);
        return dlg;
    }

    /** Fill a text/textarea input inside {scope} whose placeholder contains {placeholderSubstr}.
     *  A field that is missing or not editable is a real change in the form, so it fails here
     *  instead of being logged and skipped — a skipped field turns into a bogus pass downstream. */
    public void fill(Locator scope, String placeholderSubstr, String value) {
        String q = "'" + placeholderSubstr.replace("'", "\\'") + "'";
        Locator inp = scope.locator("input[placeholder*=" + q + "], textarea[placeholder*=" + q + "]");
        fillOrFail(scope, inp, "placeholder containing '" + placeholderSubstr + "'", value);
    }

    /** Fill the (single) numeric input inside {scope}. */
    public void fillNumber(Locator scope, String value) {
        fillOrFail(scope, scope.locator("input[type='number']"), "type='number'", value);
    }

    private void fillOrFail(Locator scope, Locator inp, String label, String value) {
        if (inp.count() == 0) {
            throw new AssertionError("Dialog has no input with " + label
                    + " — the form has changed. Inputs present: " + inputSignature(scope));
        }
        if (!inp.first().isEditable()) {
            throw new AssertionError("Dialog input with " + label + " is not editable, so '" + value
                    + "' could not be entered. Inputs present: " + inputSignature(scope));
        }
        inp.first().fill(value, new Locator.FillOptions().setTimeout(8000));
    }

    private String inputSignature(Locator scope) {
        return String.valueOf(scope.evaluate(
                "el => [...el.querySelectorAll('input,textarea')].map(f => f.placeholder || f.id || f.name || f.type)"));
    }

    /** Open the Nth combobox in {scope} and pick its first option — for forms whose "default"
     *  display is really a placeholder and must be actively selected to satisfy validation. */
    public void pickFirstOption(Locator scope, int comboIndex) {
        Locator combo = scope.locator("[role='combobox']").nth(comboIndex);
        if (combo.count() == 0) {
            throw new AssertionError("Dialog has no combobox #" + comboIndex + " — the form has changed. "
                    + "Comboboxes present: " + scope.locator("[role='combobox']").count());
        }
        combo.click();
        page.waitForTimeout(500);
        Locator opt = page.getByRole(AriaRole.OPTION).first();
        try {
            opt.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
        } catch (Exception e) {
            try { page.keyboard().press("Escape"); } catch (Exception ignored) { }
            throw new AssertionError("Combobox #" + comboIndex + " offered no options to select.");
        }
        opt.click();
        page.waitForTimeout(400);
    }

    /** Submit the dialog by clicking {submitLabel}; true if the dialog closed (saved). */
    public boolean submit(Locator dlg, String submitLabel) {
        dlg.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile(submitLabel, Pattern.CASE_INSENSITIVE))).last().click();
        try {
            dlg.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(12000));
            page.waitForTimeout(1000);
            return true;
        } catch (Exception e) {
            System.out.println("[DialogAdder] '" + submitLabel + "' did not close the dialog. Validation: "
                    + dlg.locator("[role='alert'], .text-destructive").allInnerTexts());
            return false;
        }
    }

    /** True if {text} appears anywhere on the page (to confirm a new row/value showed up). */
    public boolean pageContains(String text) {
        Locator m = page.getByText(text, new Page.GetByTextOptions().setExact(false));
        try {
            m.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(6000));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
