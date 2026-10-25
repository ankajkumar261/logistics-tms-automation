package com.portfolio.logistics.pages;

import com.microsoft.playwright.Page;

public class InsuranceTypesPage extends LookupPage {

    public InsuranceTypesPage(Page page) {
        super(page);
    }

    @Override
    protected String route() {
        return "insurance-types";
    }

    @Override
    protected String addButtonName() {
        return "Add Insurance Type";
    }
}