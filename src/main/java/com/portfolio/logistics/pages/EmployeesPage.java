package com.portfolio.logistics.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.model.Employee;
import com.portfolio.logistics.utils.ListSearch;

import java.util.List;
import java.util.regex.Pattern;

/**
 * C-Panel → Employees. Field ids confirmed against the live Add New Employee dialog:
 * firstName, lastName, role (free text), phone, email, hireDate, plus two Radix selects —
 * Department* (no default, placeholder "Select department") and Status* (defaults to Active).
 */
public class EmployeesPage {

    private final Page page;

    public EmployeesPage(Page page) {
        this.page = page;
    }

    private Locator addEmployeeButton() {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("Add Employee", Pattern.CASE_INSENSITIVE)));
    }

    private Locator dialog() {
        return page.locator("div[role='dialog']");
    }

    public void openEmployees() {
        // Navigate straight to the list — clicking the C-Panel nav menu is fragile under parallel
        // load. Retry once on the SPA net::ERR_ABORTED redirect race.
        try {
            page.navigate(ConfigReader.baseUrl() + "/cpanel/employees", new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        } catch (Exception first) {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
            page.navigate(ConfigReader.baseUrl() + "/cpanel/employees", new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
        }
        page.waitForURL("**/cpanel/employees**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    public void clickAddEmployee() {
        addEmployeeButton().first().click();
        dialog().locator("#firstName").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
    }

    public void createEmployee(Employee e) {
        Locator d = dialog();

        fill(d, "#firstName", e.getFirstName());
        fill(d, "#lastName",  e.getLastName());
        fill(d, "#role",      e.getRole());
        fill(d, "#phone",     e.getPhone());
        fill(d, "#email",     e.getEmail());

        // Department* has no default — pick the model's department by its visible label rather than
        // by position, so a re-ordered option list can't silently change what the test submitted.
        selectDepartment(d, e.getDepartment());

        d.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions()
                .setName(Pattern.compile("^Add Employee$", Pattern.CASE_INSENSITIVE))).last().click();

        try {
            d.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN).setTimeout(10000));
        } catch (TimeoutError ex) {
            List<String> errors = d.locator("[id$='-form-item-message'], [role='alert'], .text-destructive")
                    .allInnerTexts();
            throw new AssertionError(
                    "Add Employee dialog didn't close after submit — the save was rejected. "
                            + "Validation messages: " + errors);
        }
    }

    /** Open the Department select and choose {department}; fails listing the real options if absent. */
    private void selectDepartment(Locator scope, String department) {
        Locator trigger = scope.locator("[role='combobox']")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("Select department|Operations|Finance|Human Resources|Maintenance|Compliance|IT")))
                .first();
        if (trigger.count() == 0) {
            trigger = scope.locator("[role='combobox']").first();
        }
        if (trigger.count() == 0) {
            throw new AssertionError("Add Employee dialog has no Department select — the form has changed.");
        }
        trigger.click();
        Locator option = page.getByRole(AriaRole.OPTION,
                new Page.GetByRoleOptions().setName(department).setExact(true));
        try {
            option.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(6000));
        } catch (Exception e) {
            throw new AssertionError("Department '" + department + "' is not offered by the Add Employee form. "
                    + "Options available: " + page.locator("[role='option']").allInnerTexts());
        }
        option.first().click();
        page.waitForTimeout(300);
    }

    private void fill(Locator scope, String selector, String value) {
        Locator field = scope.locator(selector);
        if (field.count() == 0) {
            throw new AssertionError("Add Employee dialog has no field '" + selector
                    + "' — the form has changed. Fields present: " + String.valueOf(scope.evaluate(
                    "el => [...el.querySelectorAll('input,textarea,select')].map(f => f.id || f.name)")));
        }
        field.first().fill(value);
    }

    public boolean isEmployeePresent(String needle) {
        return rowFor(needle) != null;
    }

    /** The Employees list row for {needle} as one line of text, or null if absent. */
    public String rowFor(String needle) {
        page.navigate(ConfigReader.baseUrl() + "/cpanel/employees");
        page.waitForLoadState(LoadState.NETWORKIDLE);

        // This list DOES have a search box — use it, then fall back to paging.
        Locator search = page.locator("input[type='search'], input[placeholder*='Search' i]");
        if (search.count() > 0) {
            search.first().fill(needle);
            page.waitForTimeout(1200);
        }
        return ListSearch.rowTextAcrossPages(page, needle);
    }
}
