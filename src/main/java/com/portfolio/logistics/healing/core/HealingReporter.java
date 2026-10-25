package com.portfolio.logistics.healing.core;

/** Receives every heal attempt (applied or rejected), e.g. to put it in the Allure report. */
public interface HealingReporter {

    /** @param screenshot image of the healed element, or null */
    void onEvent(HealingEvent event, byte[] screenshot);
}
