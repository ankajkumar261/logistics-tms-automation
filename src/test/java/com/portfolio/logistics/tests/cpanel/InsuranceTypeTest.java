package com.portfolio.logistics.tests.cpanel;

import com.portfolio.logistics.base.BaseTest;
import com.portfolio.logistics.model.LookupEntry;
import com.portfolio.logistics.pages.InsuranceTypesPage;
import com.portfolio.logistics.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

@Epic("Logistics TMS TMS")
@Feature("Module 2 — Master Data")
public class InsuranceTypeTest extends BaseTest {

    @Test(description = "2.7b Insurance Types: add an entry and verify it appears")
    @Severity(SeverityLevel.NORMAL)
    public void addInsuranceType() {
        new LoginPage(page).open().loginAsSuperAdmin();
        page.waitForURL("**/dashboard**");

        InsuranceTypesPage insurance = new InsuranceTypesPage(page);
        insurance.open();

        LookupEntry e = LookupEntry.sample("QA-Insurance");
        insurance.addEntry(e);

        assertTrue(insurance.isEntryPresent(e.getName()),
                "Expected insurance type '" + e.getName() + "' to appear");
    }
}