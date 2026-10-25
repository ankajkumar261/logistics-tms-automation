package com.portfolio.logistics.model;

public class Carrier {

    private final String companyName;
    private final String mcNumber;
    private final String dotNumber;
    private final String contactName;
    private final String phone;
    private final String email;
    private final String address;

    public Carrier(String companyName, String mcNumber, String dotNumber,
                   String contactName, String phone, String email, String address) {
        this.companyName = companyName;
        this.mcNumber = mcNumber;
        this.dotNumber = dotNumber;
        this.contactName = contactName;
        this.phone = phone;
        this.email = email;
        this.address = address;
    }

    public static Carrier sample() {
        String uid = uniqueId();
        return new Carrier(
                "QA-Carrier-" + uid,
                "MC-" + uid,
                uid,
                "QA Contact " + uid,
                "(555) " + uid.substring(0, 3) + "-" + uid.substring(3, 7),
                "carrier+" + uid + "@example.com",
                "456 Freight Ave");
    }

    /** 8-digit run id: last 4 of the clock + 4 random. Unique across quick reruns. */
    private static String uniqueId() {
        String ts = String.valueOf(System.currentTimeMillis());
        String tail = ts.substring(ts.length() - 4);
        String rand = String.format("%04d", new java.util.Random().nextInt(10000));
        return tail + rand;
    }
    public String getCompanyName() { return companyName; }
    public String getMcNumber()    { return mcNumber; }
    public String getDotNumber()   { return dotNumber; }
    public String getContactName() { return contactName; }
    public String getPhone()       { return phone; }
    public String getEmail()       { return email; }
    public String getAddress()     { return address; }
}