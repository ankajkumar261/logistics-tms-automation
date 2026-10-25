package com.portfolio.logistics.healing.core;

/** Everything the engine needs to know about one broken locator. */
public final class HealingRequest {

    /** Stable identity across runs: where the locator was built + its selector. */
    public final String key;
    /** Where the locator was built, e.g. {@code CustomersPage.companyName(CustomersPage.java:33)}. */
    public final String callSite;
    public final String originalSelector;
    /** The container the original searched in, or null for the whole page. */
    public final String scopeSelector;
    public final ActionKind action;
    /** What the element looked like: the baseline fingerprint, or the selector's stated intent. */
    public final ElementSnapshot reference;
    public final boolean referenceIsBaseline;

    public HealingRequest(String key, String callSite, String originalSelector, String scopeSelector,
                          ActionKind action, ElementSnapshot reference, boolean referenceIsBaseline) {
        this.key = key;
        this.callSite = callSite;
        this.originalSelector = originalSelector;
        this.scopeSelector = scopeSelector;
        this.action = action;
        this.reference = reference;
        this.referenceIsBaseline = referenceIsBaseline;
    }
}
