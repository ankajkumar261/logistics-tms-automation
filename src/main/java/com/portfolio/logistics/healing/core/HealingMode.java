package com.portfolio.logistics.healing.core;

/** {@code healing.mode}: what to do when an original locator finds nothing. */
public enum HealingMode {
    /** No interception at all — tests run exactly as before. */
    OFF,
    /** Work out and report the best replacement, but still fail the test. */
    SUGGEST,
    /** Apply a replacement when it scores at or above the threshold; otherwise fail. */
    HEAL
}
