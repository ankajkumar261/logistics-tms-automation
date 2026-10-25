package com.portfolio.logistics.model;

public class Employee {

    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;
    private final String role;
    /** Department* — a required Radix select with no default; must be one of the app's options. */
    private final String department;

    public Employee(String firstName, String lastName, String email,
                    String phone, String role, String department) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.department = department;
    }

    public static Employee sample() {
        String uid = TestData.uniqueId();
        return new Employee(
                "QA",
                "Employee-" + uid,
                "employee+" + uid + "@example.com",
                "(555) " + uid.substring(0, 3) + "-" + uid.substring(3, 7),
                "Dispatcher",   // Role is a free-text field on the form
                "Operations");  // one of: Operations, Finance, Human Resources, Maintenance, Compliance, IT
    }

    public String getFirstName()  { return firstName; }
    public String getLastName()   { return lastName; }
    public String getEmail()      { return email; }
    public String getPhone()      { return phone; }
    public String getRole()       { return role; }
    public String getDepartment() { return department; }

    public String getFullName()  { return firstName + " " + lastName; }
}
