package com.portfolio.logistics.healing.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Per-thread link between heal events and the test that caused them. The TestNG listener drives it:
 * {@code @BeforeMethod} → {@link #beginSetup()}, test start → {@link #beginTest}, test end →
 * {@link #endTest}. Heals in @BeforeMethod belong to the test that follows; heals after the test
 * ends (teardown) are recorded with outcome NO_TEST.
 */
public final class HealingContext {

    private enum Phase { IDLE, SETUP, TEST, TEARDOWN }

    private static final class State {
        Phase phase = Phase.IDLE;
        String testId;
        final List<HealingEvent> events = new ArrayList<>();
        int suppressDepth;
    }

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private HealingContext() { }

    public static void beginSetup() {
        State s = STATE.get();
        if (s.phase != Phase.SETUP) {
            orphanLeftovers(s);
            s.phase = Phase.SETUP;
            s.testId = null;
        }
    }

    public static void beginTest(String testId) {
        State s = STATE.get();
        if (s.phase != Phase.SETUP) orphanLeftovers(s);
        s.phase = Phase.TEST;
        s.testId = testId;
        s.events.forEach(e -> e.testId = testId);
    }

    /** Events of the running test (including its @BeforeMethod). */
    public static List<HealingEvent> testEvents() {
        return new ArrayList<>(STATE.get().events);
    }

    /** Close the test: stamp its events with the outcome and hand them back. */
    public static List<HealingEvent> endTest(HealingEvent.Outcome outcome) {
        State s = STATE.get();
        List<HealingEvent> mine = new ArrayList<>(s.events);
        mine.forEach(e -> e.outcome = outcome);
        s.events.clear();
        s.phase = Phase.TEARDOWN;
        return mine;
    }

    static void record(HealingEvent event) {
        State s = STATE.get();
        event.testId = s.testId != null ? s.testId : s.phase == Phase.SETUP ? "(test setup)" : "(outside a test)";
        if (s.phase == Phase.SETUP || s.phase == Phase.TEST) {
            s.events.add(event);
        } else {
            event.outcome = HealingEvent.Outcome.NO_TEST;
        }
    }

    public static String currentTestId() {
        String id = STATE.get().testId;
        return id == null ? "(no test)" : id;
    }

    public static boolean suppressed() {
        return STATE.get().suppressDepth > 0;
    }

    /** Run {@code body} with healing switched off on this thread (exact locators only). */
    public static <T> T suppress(Supplier<T> body) {
        State s = STATE.get();
        s.suppressDepth++;
        try {
            return body.get();
        } finally {
            s.suppressDepth--;
        }
    }

    private static void orphanLeftovers(State s) {
        s.events.forEach(e -> e.outcome = HealingEvent.Outcome.NO_TEST);
        s.events.clear();
    }
}
