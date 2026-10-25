package com.portfolio.logistics.healing.core;

/**
 * Added as a suppressed exception to the ORIGINAL locator failure when a heal was attempted but not
 * applied, so the stack trace shows what the engine considered and why it refused. The test fails
 * with the original exception type and message, exactly as it would without healing.
 */
public class HealingRejectedException extends RuntimeException {

    public HealingRejectedException(HealingEvent event) {
        super("Self-healing did not apply a replacement:\n" + event.toReport(), null, false, false);
    }
}
