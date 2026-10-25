package com.portfolio.logistics.model;

public class Incorporation {

    private final String name;
    private final String registrationNumber;

    public Incorporation(String name, String registrationNumber) {
        this.name = name;
        this.registrationNumber = registrationNumber;
    }

    public static Incorporation sample() {
        String uid = TestData.uniqueId();
        return new Incorporation(
                "QA-Inc-" + uid,
                "REG-" + uid);
    }

    public String getName()               { return name; }
    public String getRegistrationNumber() { return registrationNumber; }
}