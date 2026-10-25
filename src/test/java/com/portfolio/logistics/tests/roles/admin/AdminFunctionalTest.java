package com.portfolio.logistics.tests.roles.admin;

import com.portfolio.logistics.model.Customer;
import com.portfolio.logistics.model.Order;
import com.portfolio.logistics.model.Role;
import com.portfolio.logistics.pages.*;
import com.portfolio.logistics.tests.roles.RoleTestBase;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertTrue;

/**
 * ADMIN has full access (like SUPERADMIN), so this exercises every module area — Orders, Trips,
 * Accounting (invoices/vendor bills/factoring/payments/settlements), C-Panel, Integrations,
 * Reports and Admin — to confirm the whole app works for the ADMIN role. It reuses the same page
 * objects as the SuperAdmin suite (single source of truth) rather than duplicating flows.
 */
@Epic("Logistics TMS TMS")
@Feature("Roles — ADMIN")
public class AdminFunctionalTest extends RoleTestBase {

    @Test(description = "ADMIN can create an order")
    @Severity(SeverityLevel.CRITICAL)
    public void createOrder() {
        signIn(Role.ADMIN);
        OrdersPage orders = new OrdersPage(page);
        orders.startNewOrder();
        String orderNo = orders.createOrder(Order.sample());
        assertTrue(orders.isOrderPresent(orderNo), "ADMIN should create order '" + orderNo + "'.");
    }

    @Test(description = "ADMIN can create a trip")
    @Severity(SeverityLevel.CRITICAL)
    public void createTrip() {
        signIn(Role.ADMIN);
        TripsPage trips = new TripsPage(page);
        trips.startNewTrip();
        String tripNo = trips.createTrip();
        assertTrue(tripNo != null && trips.isTripCreated(tripNo), "ADMIN should create a trip.");
    }

    @Test(description = "ADMIN can create an invoice")
    @Severity(SeverityLevel.CRITICAL)
    public void createInvoice() {
        signIn(Role.ADMIN);
        InvoicesPage invoices = new InvoicesPage(page);
        String invNo = "QA-INV-" + (System.currentTimeMillis() % 1_000_000L);
        assertTrue(invoices.createDraftInvoice(invNo, "500.00", "2026-12-31"),
                "ADMIN should create invoice " + invNo + ".");
        assertTrue(new InvoiceDetailPage(page).listShowsStatus(invNo, "Draft"),
                "ADMIN's invoice " + invNo + " should appear as Draft.");
    }

    @Test(description = "ADMIN can create a vendor bill")
    @Severity(SeverityLevel.NORMAL)
    public void createVendorBill() {
        signIn(Role.ADMIN);
        long ts = System.currentTimeMillis() % 1_000_000L;
        String billNo = "QA-VB-" + ts;
        VendorBillsPage bills = new VendorBillsPage(page);
        assertTrue(bills.createBill(billNo, "QA-Vendor-" + ts, "1250.00", "2026-12-31", "2027-01-30"),
                "ADMIN should create vendor bill " + billNo + ".");
        bills.open();
        assertTrue(bills.filterByBillNo(billNo) >= 1 && bills.tableContains(billNo),
                "Vendor bill " + billNo + " should appear.");
    }

    @Test(description = "ADMIN can add a factoring company")
    @Severity(SeverityLevel.NORMAL)
    public void addFactoringCompany() {
        signIn(Role.ADMIN);
        FactoringPage factoring = new FactoringPage(page);
        factoring.open();
        String name = "QA-Factor-" + (System.currentTimeMillis() % 1_000_000L);
        assertTrue(factoring.addCompany(name, "90", "3", "5"), "ADMIN should add factoring '" + name + "'.");
        assertTrue(factoring.search(name) >= 1, "Factoring '" + name + "' should be findable.");
    }

    @Test(description = "ADMIN can view Payment History")
    @Severity(SeverityLevel.NORMAL)
    public void paymentHistory() {
        signIn(Role.ADMIN);
        PaymentsPage payments = new PaymentsPage(page);
        payments.open();
        assertTrue(payments.totalResults() > 0, "ADMIN should see payment history.");
    }

    @Test(description = "ADMIN can view Driver Settlements")
    @Severity(SeverityLevel.NORMAL)
    public void settlementsList() {
        signIn(Role.ADMIN);
        SettlementsPage settlements = new SettlementsPage(page);
        settlements.open();
        assertTrue(settlements.totalResults() > 0, "ADMIN should see driver settlements.");
    }

    @Test(description = "ADMIN can add C-Panel master data (a customer)")
    @Severity(SeverityLevel.NORMAL)
    public void createCustomer() {
        signIn(Role.ADMIN);
        goTo("/cpanel/customers");
        CustomersPage customers = new CustomersPage(page);
        customers.clickAddCustomer();
        Customer c = Customer.sample();
        customers.createCustomer(c);
        assertTrue(listContainsAcrossPages("/cpanel/customers", c.getCompanyName()),
                "ADMIN should add customer '" + c.getCompanyName() + "'.");
    }

    @Test(description = "ADMIN can open Integrations pages")
    @Severity(SeverityLevel.NORMAL)
    public void integrationsRender() {
        signIn(Role.ADMIN);
        List<String> failures = new AppNavigator(page).firstFailures(
                "/integrations/chart-of-accounts", "Chart of Accounts",
                "/integrations/tax-codes", "Tax Codes",
                "/integrations/cards-detail", "Fuel Cards");
        assertTrue(failures.isEmpty(), "Integration pages that did not render for ADMIN: " + failures);
    }

    @Test(description = "ADMIN can open Reports")
    @Severity(SeverityLevel.NORMAL)
    public void reportsRender() {
        signIn(Role.ADMIN);
        List<String> failures = new AppNavigator(page).firstFailures(
                "/reports/sales", "Sales Report",
                "/reports/aging", "Aging Invoices",
                "/reports/outstanding", "Outstanding Report");
        assertTrue(failures.isEmpty(), "Reports that did not render for ADMIN: " + failures);
    }

    @Test(description = "ADMIN can open the Admin Panel")
    @Severity(SeverityLevel.NORMAL)
    public void adminPanelRenders() {
        signIn(Role.ADMIN);
        assertTrue(new AppNavigator(page).renders("/admin", "Admin Panel"),
                "ADMIN should open the Admin Panel.");
    }
}
