package com.portfolio.logistics.model;

public class LookupEntry {

    private final String name;

    public LookupEntry(String name) {
        this.name = name;
    }

    public static LookupEntry sample(String prefix) {
        return new LookupEntry(prefix + "-" + TestData.uniqueId());
    }

    public String getName() { return name; }
}