package com.portfolio.logistics.pages;

import com.microsoft.playwright.Page;

public class ExpenseTypesPage extends LookupPage {

    public ExpenseTypesPage(Page page) {
        super(page);
    }

    @Override
    protected String route() {
        return "expense-types";
    }

    @Override
    protected String addButtonName() {
        return "Add Expense Type";
    }
}