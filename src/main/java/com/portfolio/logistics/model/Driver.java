package com.portfolio.logistics.model;

public class Driver {

    private final String firstName;
    private final String lastName;
    private final String licenseNumber;
    private final String phone;
    private final String email;
    /**
     * License Expiry (yyyy-MM-dd), or null to leave the field blank. The Add Driver form does NOT
     * mark it required, so null is a legitimate case the suite must be able to express.
     */
    private final String licenseExpiry;

    public Driver(String firstName, String lastName, String licenseNumber,
                  String phone, String email, String licenseExpiry) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.licenseNumber = licenseNumber;
        this.phone = phone;
        this.email = email;
        this.licenseExpiry = licenseExpiry;
    }

    /** A driver with every field the form offers filled in, including License Expiry. */
    public static Driver sample() {
        return base(TestData.uniqueId(), "2030-01-15");
    }

    /** A driver with License Expiry left blank — the form marks that field optional. */
    public static Driver sampleWithoutLicenseExpiry() {
        return base(TestData.uniqueId(), null);
    }

    private static Driver base(String uid, String licenseExpiry) {
        return new Driver(
                "QA",
                "Driver-" + uid,
                "DL-" + uid,
                "(555) " + uid.substring(0, 3) + "-" + uid.substring(3, 7),
                "driver+" + uid + "@example.com",
                licenseExpiry);
    }

    public String getFirstName()     { return firstName; }
    public String getLastName()      { return lastName; }
    public String getLicenseNumber() { return licenseNumber; }
    public String getPhone()         { return phone; }
    public String getEmail()         { return email; }
    public String getLicenseExpiry() { return licenseExpiry; }
    public boolean hasLicenseExpiry(){ return licenseExpiry != null && !licenseExpiry.isBlank(); }

    /** Full name as it will render in the list, for verification. */
    public String getFullName()      { return firstName + " " + lastName; }
}
