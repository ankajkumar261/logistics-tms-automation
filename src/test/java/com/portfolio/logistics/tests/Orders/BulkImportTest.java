package com.portfolio.logistics.tests.Orders;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.config.ConfigReader;
import com.portfolio.logistics.pages.LoginPage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * 3.5 Bulk import (BUG-001 fixed). The importer now validates each row: the Customer must be an
 * existing customer (matched by exact company name) and the Customer Order # must be unique.
 * This builds a valid file (a real customer + run-unique order numbers) and asserts the Import
 * Summary reports every row imported.
 */
@Epic("Logistics TMS TMS")
@Feature("Module 3 — Orders")
public class BulkImportTest extends BaseTest {

    // Current template columns (Download Template on /orders/import).
    private static final String HEADER = "Customer,Customer Order #,Origin Name,Origin Address,Origin City,"
            + "Origin State,Origin Postal Code,Origin Country,Destination Name,Destination Address,"
            + "Destination City,Destination State,Destination Postal Code,Destination Country,Pickup Date,"
            + "Delivery Date,Rate,Rate Currency,Commodity,Shipment Type,Weight,Weight Unit,Distance,"
            + "Distance Unit,Internal Reference,Pickup Number,Delivery Number,Special Instructions";

    @Test(description = "3.5 Bulk import: upload a CSV of orders and verify they import")
    @Severity(SeverityLevel.NORMAL)
    public void bulkImportOrders() throws Exception {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1000);

        // The import matches Customer by exact company name — grab a real existing customer.
        page.navigate(ConfigReader.baseUrl() + "/cpanel/customers");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1200);
        String customer = (String) page.evaluate(
                "() => { const r = document.querySelector('table tbody tr'); if(!r) return '';"
                + " const tds = [...r.querySelectorAll('td')].map(t => t.innerText.trim());"
                + " return tds.find(t => t && /[A-Za-z]/.test(t) && !/@|active|inactive/i.test(t)) || ''; }");
        assertTrue(!customer.isBlank(), "Precondition: need an existing customer to import against.");

        // Build a valid 2-row CSV with run-unique Customer Order #s.
        long ts = System.currentTimeMillis() % 1_000_000L;
        String tag = "QA-BULK-" + ts;
        String r1 = "\"" + customer + "\"," + tag + "-1,QA Origin,55 York St,Toronto,ON,M5J 1R7,Canada,"
                + "QA Dest,789 W Pender St,Vancouver,BC,V6C 1H2,Canada,2026-08-10,2026-09-10,2500,CAD,"
                + "General Freight,FTL,12000,lbs,,mi,,,,";
        String r2 = "\"" + customer + "\"," + tag + "-2,QA Origin,55 York St,Toronto,ON,M5J 1R7,Canada,"
                + "QA Dest,201 Portage Ave,Winnipeg,MB,R3B 3K6,Canada,2026-08-11,2026-09-11,1800,CAD,"
                + "General Freight,LTL,8000,lbs,,mi,,,,";
        Path csv = Files.createTempFile("bulk-" + tag + "-", ".csv");
        Files.writeString(csv, HEADER + "\n" + r1 + "\n" + r2 + "\n");

        // Upload and import.
        page.navigate(ConfigReader.baseUrl() + "/orders/import");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        page.waitForTimeout(1200);
        page.locator("input[type='file']").first().setInputFiles(csv);
        page.waitForTimeout(1500);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Import$", Pattern.CASE_INSENSITIVE))).first().click();

        // Read the Import Summary — poll for the "Imported N orders" result.
        int imported = -1;
        for (int i = 0; i < 20 && imported < 0; i++) {
            Object o = page.evaluate(
                    "() => { const m = document.body.innerText.match(/Imported\\s+(\\d+)\\s+orders?/i);"
                    + " return m ? parseInt(m[1]) : -1; }");
            imported = ((Number) o).intValue();
            if (imported < 0) page.waitForTimeout(500);
        }
        String errorRows = (String) page.evaluate(
                "() => { const m = document.body.innerText.match(/Row \\d+:[^\\n]*/g); return m ? m.slice(0,4).join(' | ') : ''; }");

        assertEquals(imported, 2,
                "Bulk import should import 2 orders (customer '" + customer + "', tag " + tag
                        + "). Import errors: " + errorRows);
    }
}
