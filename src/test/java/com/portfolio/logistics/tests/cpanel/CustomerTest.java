package com.portfolio.logistics.tests.cpanel;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.Customer;
import com.portfolio.logistics.pages.CustomersPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 2 — Master Data")
public class CustomerTest extends BaseTest {

    @Test(description = "2.1 Customers: create a customer and verify it appears in the list")
    @Severity(SeverityLevel.CRITICAL)
    public void createCustomer() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        CustomersPage customers = new CustomersPage(page);
        customers.openCustomers();
        customers.clickAddCustomer();

        Customer c = Customer.sample();
        customers.createCustomer(c);

        assertTrue(customers.isCustomerPresent(c.getCompanyName()),
                "Expected new customer '" + c.getCompanyName() + "' to appear in the Customers list");
    }
}