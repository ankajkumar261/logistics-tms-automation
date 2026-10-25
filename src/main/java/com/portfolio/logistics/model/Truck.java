package com.portfolio.logistics.model;

public class Truck {

    private final String unitNumber;
    private final String make;
    private final String model;
    private final String year;
    private final String vin;
    private final String plate;
    /** Odometer (mi) — the Add Truck form marks this required; blank submits as 0. */
    private final String odometer;

    public Truck(String unitNumber, String make, String model,
                 String year, String vin, String plate, String odometer) {
        this.unitNumber = unitNumber;
        this.make = make;
        this.model = model;
        this.year = year;
        this.vin = vin;
        this.plate = plate;
        this.odometer = odometer;
    }

    public static Truck sample() {
        String uid = TestData.uniqueId();
        return new Truck(
                "QA-Truck-" + uid,
                "Freightliner",
                "Cascadia",
                "2022",
                "VIN" + uid + "TEST",
                // Plate in the real ABC-1234 shape the field asks for, and unique per run so the
                // Trucks list can be asserted on this exact value.
                "QA-" + uid.substring(0, 4),
                "125000");
    }

    public String getUnitNumber() { return unitNumber; }
    public String getMake()       { return make; }
    public String getModel()      { return model; }
    public String getYear()       { return year; }
    public String getVin()        { return vin; }
    public String getPlate()      { return plate; }
    public String getOdometer()   { return odometer; }
}
