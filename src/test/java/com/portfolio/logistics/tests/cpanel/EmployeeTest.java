package com.portfolio.logistics.tests.cpanel;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Employee;
import com.portfolio.logistics.pages.EmployeesPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 2 — Master Data")
public class EmployeeTest extends BaseTest {

    @Test(description = "2.6 Employees: create an employee with a role and verify it appears in the list")
    @Severity(SeverityLevel.CRITICAL)
    public void createEmployee() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        EmployeesPage employees = new EmployeesPage(page);
        employees.openEmployees();
        employees.clickAddEmployee();

        Employee e = Employee.sample();
        employees.createEmployee(e);

        assertTrue(employees.isEmployeePresent(e.getLastName()),
                "Expected new employee '" + e.getLastName() + "' to appear in the Employees list");
    }
}