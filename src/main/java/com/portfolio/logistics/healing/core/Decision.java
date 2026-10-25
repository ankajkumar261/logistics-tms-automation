package com.portfolio.logistics.healing.core;

/** Outcome of one heal attempt. Only HEALED changes what the test does. */
public enum Decision {
    HEALED,
    /** SUGGEST mode: a replacement qualified but was not applied. */
    SUGGESTED,
    /** The best candidate scored below the threshold. */
    LOW_CONFIDENCE,
    /** Two different elements scored within the margin of each other — can't tell which is right. */
    AMBIGUOUS,
    /** Only structure/position matched; nothing about the element's identity, label or text did. */
    INSUFFICIENT_EVIDENCE,
    /** No element on the page (or in the original's container) could take the action. */
    NO_CANDIDATES,
    /** The best match is disabled / read-only — that's app state, not a locator problem. */
    NOT_ACTIONABLE,
    /** No locator could be built that points at exactly that one element. */
    NOT_UNIQUE,
    /** This replacement was applied before and the action failed on it. */
    BLACKLISTED;

    public boolean applied() {
        return this == HEALED;
    }
}
