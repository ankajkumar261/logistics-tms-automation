package com.portfolio.logistics.tests.roles.dispatcher;

import com.portfolio.logistics.model.*;
import com.portfolio.logistics.pages.*;
import com.portfolio.logistics.tests.roles.RoleTestBase;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * DISPATCHER — full C-Panel (master data) coverage. Each entity is created via its existing page
 * object; the flaky nav-menu pages are reached with a direct navigation. Verification uses
 * {@link #listContainsAcrossPages} because these lists have no search box and paginate at 10/row,
 * so a just-added record usually lands on a LATER page (the page-1-only isXPresent() checks miss it).
 */
@Epic("Logistics TMS TMS")
@Feature("Roles — DISPATCHER")
public class DispatcherMasterDataTest extends RoleTestBase {

    @Test(description = "DISPATCHER can add a Driver")
    @Severity(SeverityLevel.NORMAL)
    public void createDriver() {
        signIn(Role.DISPATCHER);
        goTo("/cpanel/drivers");
        DriversPage drivers = new DriversPage(page);
        drivers.clickAddDriver();
        Driver dr = Driver.sample();
        drivers.createDriver(dr);
        assertTrue(listContainsAcrossPages("/cpanel/drivers", dr.getFullName()),
                "DISPATCHER should be able to add driver '" + dr.getFullName() + "'.");
    }

    @Test(description = "DISPATCHER can add a Customer")
    @Severity(SeverityLevel.NORMAL)
    public void createCustomer() {
        signIn(Role.DISPATCHER);
        goTo("/cpanel/customers");
        CustomersPage customers = new CustomersPage(page);
        customers.clickAddCustomer();
        Customer c = Customer.sample();
        customers.createCustomer(c);
        assertTrue(listContainsAcrossPages("/cpanel/customers", c.getCompanyName()),
                "DISPATCHER should be able to add customer '" + c.getCompanyName() + "'.");
    }

    @Test(description = "DISPATCHER can add a Carrier")
    @Severity(SeverityLevel.NORMAL)
    public void createCarrier() {
        signIn(Role.DISPATCHER);
        goTo("/cpanel/carriers");
        CarriersPage carriers = new CarriersPage(page);
        carriers.clickAddCarrier();
        Carrier c = Carrier.sample();
        carriers.createCarrier(c);
        assertTrue(listContainsAcrossPages("/cpanel/carriers", c.getCompanyName()),
                "DISPATCHER should be able to add carrier '" + c.getCompanyName() + "'.");
    }

    @Test(description = "DISPATCHER can add a Truck")
    @Severity(SeverityLevel.NORMAL)
    public void createTruck() {
        signIn(Role.DISPATCHER);
        goTo("/cpanel/trucks");
        TrucksPage trucks = new TrucksPage(page);
        trucks.clickAddTruck();
        Truck t = Truck.sample();
        trucks.createTruck(t);
        assertTrue(listContainsAcrossPages("/cpanel/trucks", t.getUnitNumber()),
                "DISPATCHER should be able to add truck '" + t.getUnitNumber() + "'.");
    }

    @Test(description = "DISPATCHER can add a Trailer")
    @Severity(SeverityLevel.NORMAL)
    public void createTrailer() {
        signIn(Role.DISPATCHER);
        goTo("/cpanel/trailers");
        TrailersPage trailers = new TrailersPage(page);
        trailers.clickAddTrailer();
        Trailer t = Trailer.sample();
        trailers.createTrailer(t);
        assertTrue(listContainsAcrossPages("/cpanel/trailers", t.getUnitNumber()),
                "DISPATCHER should be able to add trailer '" + t.getUnitNumber() + "'.");
    }

    @Test(description = "DISPATCHER can add an Employee")
    @Severity(SeverityLevel.NORMAL)
    public void createEmployee() {
        signIn(Role.DISPATCHER);
        goTo("/cpanel/employees");
        EmployeesPage employees = new EmployeesPage(page);
        employees.clickAddEmployee();
        Employee e = Employee.sample();
        employees.createEmployee(e);
        assertTrue(listContainsAcrossPages("/cpanel/employees", e.getLastName()),
                "DISPATCHER should be able to add employee '" + e.getLastName() + "'.");
    }

    @Test(description = "DISPATCHER can add an Incorporation")
    @Severity(SeverityLevel.NORMAL)
    public void createIncorporation() {
        signIn(Role.DISPATCHER);
        IncorporationsPage inc = new IncorporationsPage(page);
        inc.openIncorporations();
        inc.clickAdd();
        Incorporation record = Incorporation.sample();
        inc.createIncorporation(record);
        assertTrue(listContainsAcrossPages("/cpanel/incorporations", record.getName()),
                "DISPATCHER should be able to add incorporation '" + record.getName() + "'.");
    }

    @Test(description = "DISPATCHER can add an Expense Type")
    @Severity(SeverityLevel.NORMAL)
    public void addExpenseType() {
        signIn(Role.DISPATCHER);
        ExpenseTypesPage expense = new ExpenseTypesPage(page);
        expense.open();
        LookupEntry e = LookupEntry.sample("QA-Expense");
        expense.addEntry(e);
        assertTrue(listContainsAcrossPages("/cpanel/expense-types", e.getName()),
                "DISPATCHER should be able to add expense type '" + e.getName() + "'.");
    }

    @Test(description = "DISPATCHER can add an Insurance Type")
    @Severity(SeverityLevel.NORMAL)
    public void addInsuranceType() {
        signIn(Role.DISPATCHER);
        InsuranceTypesPage insurance = new InsuranceTypesPage(page);
        insurance.open();
        LookupEntry e = LookupEntry.sample("QA-Insurance");
        insurance.addEntry(e);
        assertTrue(listContainsAcrossPages("/cpanel/insurance-types", e.getName()),
                "DISPATCHER should be able to add insurance type '" + e.getName() + "'.");
    }
}
