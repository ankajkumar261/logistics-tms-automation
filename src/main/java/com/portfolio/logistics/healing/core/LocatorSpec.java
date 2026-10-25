package com.portfolio.logistics.healing.core;

import java.util.Objects;

/**
 * A replacement locator chosen by a heal, in driver-neutral form. The adapter turns it back into a
 * native locator (Playwright: page.locator / getByRole / getByLabel ...; Appium later: AppiumBy.*).
 *
 * Web kinds: CSS, ROLE, LABEL, PLACEHOLDER, TEXT. {@code scoped} means it is resolved inside the same
 * container as the original locator (e.g. the dialog), not the whole page.
 */
public class LocatorSpec {

    public String kind;
    public String value;
    /** ARIA role for kind ROLE. */
    public String role;
    public boolean scoped;
    /** Java code a person can paste into the page object to make the fix permanent. */
    public String javaCode;

    public LocatorSpec() { }

    public LocatorSpec(String kind, String value, String role, boolean scoped) {
        this.kind = kind;
        this.value = value;
        this.role = role;
        this.scoped = scoped;
    }

    @Override
    public String toString() {
        String body = "ROLE".equals(kind)
                ? "role=" + role + "[name=\"" + value + "\"]"
                : kind.toLowerCase() + "=" + ("CSS".equals(kind) ? value : "\"" + value + "\"");
        return scoped ? "(in scope) " + body : body;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LocatorSpec && toString().equals(o.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(toString());
    }
}
