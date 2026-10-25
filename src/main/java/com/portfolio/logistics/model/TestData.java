package com.portfolio.logistics.model;

import java.util.Random;

public final class TestData {

    private TestData() { }

    /** 8-digit run id: last 4 of the clock + 4 random. Unique across quick reruns. */
    public static String uniqueId() {
        String ts = String.valueOf(System.currentTimeMillis());
        String tail = ts.substring(ts.length() - 4);
        String rand = String.format("%04d", new Random().nextInt(10000));
        return tail + rand;
    }
}