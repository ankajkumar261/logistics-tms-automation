package com.portfolio.logistics.healing.core;

/** What the engine decided; {@link #spec} is the replacement to use when {@link #applied()}. */
public final class HealingResult {

    public final HealingEvent event;
    public final LocatorSpec spec;

    public HealingResult(HealingEvent event, LocatorSpec spec) {
        this.event = event;
        this.spec = spec;
    }

    public boolean applied() {
        return event.applied;
    }
}
