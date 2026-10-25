package com.portfolio.logistics.tests.bugfixes;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Employee;
import com.portfolio.logistics.pages.EmployeesPage;
import com.portfolio.logistics.pages.LoginPage;
import com.portfolio.logistics.utils.ApiWatcher;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * FIX-04 — Add Employee used to return HTTP 400. A valid employee (both names, a role, a department
 * chosen from the required select, phone and email) must now be accepted and appear in the list.
 * The API status is asserted directly so a 400 is reported as a 400, not as "the dialog stayed open".
 */
@Epic("Logistics TMS TMS")
@Feature("Bug fixes — Employees")
public class EmployeeCreationTest extends BaseTest {

    @Test(description = "FIX-04 Employee creation is accepted by the API and appears in the list")
    @Severity(SeverityLevel.CRITICAL)
    public void employeeCreationSucceeds() {
        new LoginPage(page).establishAdminSession();
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);

        EmployeesPage employees = new EmployeesPage(page);
        employees.openEmployees();
        employees.clickAddEmployee();

        Employee e = Employee.sample();
        employees.createEmployee(e);

        ApiWatcher.Call create = api.requireCall("/api/employees");
        assertTrue(create.ok(),
                "Creating employee '" + e.getFullName() + "' was rejected by the API.\n" + create);

        // The required Department select must actually reach the payload — a blank department was
        // one of the ways this call used to come back as a 400.
        assertTrue(create.sent("department", e.getDepartment()),
                "The Add Employee request did not carry the selected Department '" + e.getDepartment()
                        + "'.\n" + create);

        String row = employees.rowFor(e.getLastName());
        assertNotNull(row,
                "Employee '" + e.getFullName() + "' was accepted by the API but is not in the Employees list.");
        assertTrue(row.contains(e.getDepartment()),
                "The Employees list row for '" + e.getFullName() + "' does not show department '"
                        + e.getDepartment() + "'. Row read: " + row);
    }
}
