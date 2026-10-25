package com.portfolio.logistics.healing.playwright;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.portfolio.logistics.healing.core.HealingEngine;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Stands in for the test's {@link Page}. Everything passes straight through; the only change is that
 * locators it creates come back as healing locators.
 */
public final class HealingPageHandler implements InvocationHandler {

    private final Page raw;
    private final HealingEngine engine;
    private Page proxy;

    private HealingPageHandler(Page raw, HealingEngine engine) {
        this.raw = raw;
        this.engine = engine;
    }

    public static Page wrap(Page raw, HealingEngine engine) {
        HealingPageHandler h = new HealingPageHandler(raw, engine);
        h.proxy = (Page) Proxy.newProxyInstance(Page.class.getClassLoader(), new Class<?>[]{Page.class}, h);
        return h.proxy;
    }

    @Override
    public Object invoke(Object proxy, Method m, Object[] args) {
        if (m.getDeclaringClass() == Object.class) return Proxies.objectMethod(proxy, m, args, raw);
        Object result = Proxies.call(raw, m, Proxies.unwrapArgs(args));
        if (result instanceof Locator l) {
            return HealingLocatorHandler.wrap(l, this, LocatorOrigin.root(l));
        }
        return result;
    }

    Page raw() {
        return raw;
    }

    Page proxy() {
        return proxy;
    }

    HealingEngine engine() {
        return engine;
    }
}
