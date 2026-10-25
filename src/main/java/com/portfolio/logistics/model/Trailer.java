package com.portfolio.logistics.model;

public class Trailer {

    private final String unitNumber;
    private final String type;
    private final String make;
    private final String year;
    private final String vin;
    private final String plate;

    public Trailer(String unitNumber, String type, String make,
                   String year, String vin, String plate) {
        this.unitNumber = unitNumber;
        this.type = type;
        this.make = make;
        this.year = year;
        this.vin = vin;
        this.plate = plate;
    }

    public static Trailer sample() {
        String uid = TestData.uniqueId();
        return new Trailer(
                "QA-Trailer-" + uid,
                "Dry Van",
                "Wabash",
                "2021",
                "VIN" + uid + "TRL",
                "QT-" + uid);
    }

    public String getUnitNumber() { return unitNumber; }
    public String getType()       { return type; }
    public String getMake()       { return make; }
    public String getYear()       { return year; }
    public String getVin()        { return vin; }
    public String getPlate()      { return plate; }
}