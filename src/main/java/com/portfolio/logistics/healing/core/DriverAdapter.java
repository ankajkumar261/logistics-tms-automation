package com.portfolio.logistics.healing.core;

import java.util.List;

/**
 * The only thing the engine needs from a UI driver, bound to one heal attempt and to the container
 * the original locator searched in (the whole page, or e.g. one dialog).
 *
 * Playwright implements it with a DOM sweep ({@code healing/dom-snapshot.js}); an Appium adapter
 * would build the same snapshots from the page source and return AppiumBy-style specs.
 */
public interface DriverAdapter {

    /**
     * Visible elements inside the container that {@code action} can be performed on. Each snapshot's
     * {@code index} must identify the live element for {@link #locatorsFor}.
     */
    List<ElementSnapshot> candidates(ActionKind action);

    /**
     * Locators that resolve to exactly this one candidate (checked against the live element), most
     * stable first. Empty if none can be built.
     */
    List<LocatorSpec> locatorsFor(ElementSnapshot candidate);

    /** Current page / screen identifier for the log (URL path, activity name, ...). */
    String location();

    /** "web", "android", "ios" — recorded with each event. */
    String platform();
}
