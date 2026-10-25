package com.portfolio.logistics.healing.playwright;

import com.microsoft.playwright.Locator;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * Where a locator came from: its selector, the page-object method that built it, and the container
 * it searches in. The key ({@code CustomersPage.companyName | #companyName}) is what baselines and
 * history are stored under, so it must be the same on every run — hence method name, not line number.
 */
final class LocatorOrigin {

    /** Ops that narrow the same set of elements; everything else returning a Locator searches inside. */
    private static final Set<String> REFINEMENTS = Set.of("first", "last", "nth", "filter", "and", "or", "describe", "all");
    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final Field SELECTOR_FIELD = selectorField();

    final String selector;
    /** e.g. {@code CustomersPage.companyName(CustomersPage.java:33)} */
    final String site;
    /** e.g. {@code CustomersPage.companyName} */
    final String siteKey;
    /** Raw container locator, or null for the whole page. */
    final Locator scope;
    final String scopeSelector;

    private LocatorOrigin(String selector, StackWalker.StackFrame frame, Locator scope, String scopeSelector) {
        this.selector = selector;
        this.scope = scope;
        this.scopeSelector = scopeSelector;
        if (frame == null) {
            this.site = "(unknown)";
            this.siteKey = "(unknown)";
        } else {
            String cls = frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1);
            this.siteKey = cls + "." + frame.getMethodName();
            this.site = siteKey + "(" + frame.getFileName() + ":" + frame.getLineNumber() + ")";
        }
    }

    /** A locator made directly from the page. */
    static LocatorOrigin root(Locator created) {
        return new LocatorOrigin(selectorOf(created), callerFrame(), null, null);
    }

    /** A locator made from another one via {@code op}; {@code base} is the raw locator it was derived from. */
    LocatorOrigin derive(String op, Locator child, Locator base) {
        boolean narrowsSameSet = REFINEMENTS.contains(op);
        return new LocatorOrigin(selectorOf(child), callerFrame(),
                narrowsSameSet ? scope : base,
                narrowsSameSet ? scopeSelector : selectorOf(base));
    }

    String key() {
        return siteKey + " | " + selector;
    }

    /** First frame outside healing, Playwright, proxies and the JDK — the page object or test. */
    private static StackWalker.StackFrame callerFrame() {
        return WALKER.walk(frames -> frames
                .filter(f -> !isInternal(f.getClassName()))
                .findFirst()
                .orElse(null));
    }

    private static boolean isInternal(String cls) {
        return cls.startsWith("com.portfolio.logistics.healing.")
                || cls.startsWith("com.microsoft.playwright.")
                || cls.startsWith("java.") || cls.startsWith("jdk.") || cls.startsWith("sun.")
                || cls.startsWith("com.sun.") || cls.contains("$Proxy");
    }

    /** Playwright's internal selector string (LocatorImpl.selector); falls back to toString(). */
    static String selectorOf(Locator l) {
        if (SELECTOR_FIELD != null && SELECTOR_FIELD.getDeclaringClass().isInstance(l)) {
            try {
                return (String) SELECTOR_FIELD.get(l);
            } catch (IllegalAccessException ignored) {
                // fall through
            }
        }
        String s = String.valueOf(l);
        return s.startsWith("Locator@") ? s.substring("Locator@".length()) : s;
    }

    private static Field selectorField() {
        try {
            Field f = Class.forName("com.microsoft.playwright.impl.LocatorImpl").getDeclaredField("selector");
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
