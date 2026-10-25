package com.portfolio.logistics.model;

public class Customer {

    private final String companyName;
    private final String invoiceCode;
    private final String contactName;
    private final String phone;
    private final String email;
    private final String street;
    private final String creditLimit;

    public Customer(String companyName, String invoiceCode, String contactName,
                    String phone, String email, String street, String creditLimit) {
        this.companyName = companyName;
        this.invoiceCode = invoiceCode;
        this.contactName = contactName;
        this.phone = phone;
        this.email = email;
        this.street = street;
        this.creditLimit = creditLimit;
    }

    public static Customer sample() {
        String ts = String.valueOf(System.currentTimeMillis());
        String shortTs = ts.substring(ts.length() - 6);
        return new Customer(
                "QA-Customer-" + ts,
                "QA" + shortTs,
                "QA Contact",
                "5035551234",
                "qa+" + shortTs + "@example.com",
                "123 Test Street",
                "50000");
    }

    public String getCompanyName() { return companyName; }
    public String getInvoiceCode() { return invoiceCode; }
    public String getContactName() { return contactName; }
    public String getPhone()       { return phone; }
    public String getEmail()       { return email; }
    public String getStreet()      { return street; }
    public String getCreditLimit() { return creditLimit; }
}