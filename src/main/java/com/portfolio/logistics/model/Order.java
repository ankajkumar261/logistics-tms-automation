package com.portfolio.logistics.model;

public class Order {

    private final String reference;
    private final String pickupCity;
    private final String deliveryCity;
    private final String rate;

    public Order(String reference, String pickupCity, String deliveryCity, String rate) {
        this.reference = reference;
        this.pickupCity = pickupCity;
        this.deliveryCity = deliveryCity;
        this.rate = rate;
    }

    public static Order sample() {
        String uid = TestData.uniqueId();
        return new Order("QA-Order-" + uid, "Portland", "Seattle", "1500");
    }

    public String getReference()    { return reference; }
    public String getPickupCity()   { return pickupCity; }
    public String getDeliveryCity() { return deliveryCity; }
    public String getRate()         { return rate; }
}