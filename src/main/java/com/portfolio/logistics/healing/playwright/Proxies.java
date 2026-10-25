package com.portfolio.logistics.healing.playwright;

import com.microsoft.playwright.Locator;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/** Reflection plumbing shared by the page and locator proxies. */
final class Proxies {

    private Proxies() { }

    /** Invoke on the real object, rethrowing the real exception (not InvocationTargetException). */
    static Object call(Object target, Method m, Object[] args) {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw sneaky(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Playwright casts Locator arguments to its own implementation class ({@code and/or/locator(Locator)},
     * {@code FilterOptions.setHas(...)}), so every proxy handed back to it must be swapped for the real one —
     * including proxies sitting inside options objects.
     */
    static Object[] unwrapArgs(Object[] args) {
        if (args == null) return null;
        Object[] out = args.clone();
        for (int i = 0; i < out.length; i++) out[i] = unwrap(out[i]);
        return out;
    }

    static Object unwrap(Object o) {
        if (o == null) return null;
        if (Proxy.isProxyClass(o.getClass())) {
            InvocationHandler h = Proxy.getInvocationHandler(o);
            if (h instanceof HealingLocatorHandler lh) return lh.target();
            if (h instanceof HealingPageHandler ph) return ph.raw();
            return o;
        }
        if (PlaywrightActions.isOptions(o)) unwrapFields(o);
        return o;
    }

    private static void unwrapFields(Object options) {
        for (Field f : options.getClass().getFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !Locator.class.isAssignableFrom(f.getType())) continue;
            try {
                Object v = f.get(options);
                Object u = unwrap(v);
                if (u != v) f.set(options, u);
            } catch (IllegalAccessException ignored) {
                // public field; not expected
            }
        }
    }

    /** Object methods on a proxy: identity semantics, readable toString. */
    static Object objectMethod(Object proxy, Method m, Object[] args, Object target) {
        return switch (m.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> String.valueOf(target);
        };
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneaky(Throwable t) throws E {
        throw (E) t;
    }
}
