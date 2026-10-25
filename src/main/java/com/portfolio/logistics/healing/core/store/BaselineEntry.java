package com.portfolio.logistics.healing.core.store;

import com.portfolio.logistics.healing.core.ElementSnapshot;

/** What one locator's element looked like the last time the original locator worked. */
public class BaselineEntry {

    public String key;
    public String selector;
    public String callSite;
    public String location;
    /** Null until the locator has worked at least once with healing installed. */
    public ElementSnapshot fingerprint;
    public long firstSeen;
    public long lastSeen;
    /** Last time the original locator found its element. */
    public long lastWorkedAt;
    /** Last time the original locator found nothing. */
    public long lastBrokenAt;

    /** Broken more recently than it last worked — give it only a short wait before healing. */
    public boolean knownBroken() {
        return lastBrokenAt > lastWorkedAt;
    }
}
