package com.portfolio.logistics.healing.playwright;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.portfolio.logistics.healing.core.ActionKind;
import com.portfolio.logistics.healing.core.ElementSnapshot;
import com.portfolio.logistics.healing.core.HealingConfig;
import com.portfolio.logistics.healing.core.HealingContext;
import com.portfolio.logistics.healing.core.HealingEngine;
import com.portfolio.logistics.healing.core.HealingRejectedException;
import com.portfolio.logistics.healing.core.HealingRequest;
import com.portfolio.logistics.healing.core.HealingResult;
import com.portfolio.logistics.healing.core.LocatorIntent;
import com.portfolio.logistics.healing.core.store.BaselineEntry;
import com.portfolio.logistics.healing.core.store.HealingStore;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands in for a {@link Locator}. For an action (click, fill, ...):
 *  1. runs the ORIGINAL locator with the caller's own timeout;
 *  2. on success, records what the element looks like (once per run) as the baseline;
 *  3. only if it timed out AND the original matches no element at all, asks the engine for a replacement.
 * If the original matches an element that just couldn't be used (hidden, disabled, covered), it's
 * app state and the original error is thrown untouched. When no replacement qualifies, the original
 * error is thrown with the engine's reasoning attached as a suppressed exception.
 */
final class HealingLocatorHandler implements InvocationHandler {

    /** Stop re-trying baseline capture for a key whose element never shows up as exactly one match. */
    private static final int MAX_CAPTURE_ATTEMPTS = 3;
    private static final Map<String, AtomicInteger> CAPTURE_ATTEMPTS = new ConcurrentHashMap<>();

    private final Locator delegate;
    private final HealingPageHandler page;
    private final LocatorOrigin origin;
    /** Once this locator object has been healed, it keeps using the replacement. */
    private Locator healed;

    private HealingLocatorHandler(Locator delegate, HealingPageHandler page, LocatorOrigin origin) {
        this.delegate = delegate;
        this.page = page;
        this.origin = origin;
    }

    static Locator wrap(Locator raw, HealingPageHandler page, LocatorOrigin origin) {
        return (Locator) Proxy.newProxyInstance(Locator.class.getClassLoader(), new Class<?>[]{Locator.class},
                new HealingLocatorHandler(raw, page, origin));
    }

    /** The real locator currently in use. */
    Locator target() {
        return healed != null ? healed : delegate;
    }

    @Override
    public Object invoke(Object proxy, Method m, Object[] args) {
        if (m.getDeclaringClass() == Object.class) return Proxies.objectMethod(proxy, m, args, target());
        Object[] a = Proxies.unwrapArgs(args);
        if (m.getName().equals("page") && m.getParameterCount() == 0) return page.proxy();

        ActionKind kind = HealingContext.suppressed() ? null : PlaywrightActions.kindOf(m, a, config());
        if (kind == null) return wrapResult(m, Proxies.call(target(), m, a));
        return act(m, a, kind);
    }

    private Object act(Method m, Object[] a, ActionKind kind) {
        if (healed != null) return wrapResult(m, Proxies.call(healed, m, a));

        Long timeout = PlaywrightActions.explicitTimeout(a);
        if (timeout != null && timeout <= config().probeTimeoutMs) {
            // A short explicit timeout means "is it there?" — keep the exact answer, never heal.
            return wrapResult(m, Proxies.call(delegate, m, a));
        }

        HealingResult earlyAttempt = null;
        if (store().knownBroken(origin.key()) && !presentWithin(config().knownBrokenProbeMs)) {
            // Broken last time too: don't make every run sit through the full timeout first.
            earlyAttempt = attemptHeal(kind);
            if (earlyAttempt.applied()) return runHealed(m, a, earlyAttempt);
            // Not healable — give the original its full normal wait, so the outcome is what it always was.
        }

        captureBaselineIfNeeded(false); // before: a click may navigate away from the element
        Object result;
        try {
            result = Proxies.call(delegate, m, a);
        } catch (TimeoutError original) {
            if (safeCount(delegate) > 0) throw original; // element exists, just not usable: app state
            HealingResult r = earlyAttempt != null ? earlyAttempt : attemptHeal(kind);
            if (r.applied()) return runHealed(m, a, r);
            original.addSuppressed(new HealingRejectedException(r.event));
            throw original;
        }
        store().markWorked(origin.key());
        captureBaselineIfNeeded(true); // after: it may only have rendered during the action's wait
        return wrapResult(m, result);
    }

    private HealingResult attemptHeal(ActionKind kind) {
        HealingStore store = store();
        store.markBroken(origin.key(), origin.selector, origin.site);
        Optional<BaselineEntry> baseline = store.baseline(origin.key());
        boolean hasBaseline = baseline.isPresent() && baseline.get().fingerprint != null;
        ElementSnapshot reference = hasBaseline ? baseline.get().fingerprint : LocatorIntent.parse(origin.selector);

        PlaywrightAdapter adapter = new PlaywrightAdapter(page.raw(), origin.scope, origin.scopeSelector,
                config().maxCandidates);
        HealingRequest req = new HealingRequest(origin.key(), origin.site, origin.selector, origin.scopeSelector,
                kind, reference, hasBaseline);
        HealingResult r = engine().heal(req, adapter);

        byte[] shot = null;
        if (r.spec != null) {
            Locator replacement = adapter.toLocator(r.spec);
            shot = screenshot(replacement);
            if (r.applied()) healed = replacement;
        }
        engine().publish(r.event, shot);
        return r;
    }

    /**
     * Perform the action on the replacement. If even that fails, the replacement is blocked for this
     * locator in future runs — it demonstrably wasn't the right element.
     */
    private Object runHealed(Method m, Object[] a, HealingResult r) {
        try {
            return wrapResult(m, Proxies.call(healed, m, a));
        } catch (PlaywrightException e) {
            store().blacklist(origin.key(), r.spec.toString());
            e.addSuppressed(new IllegalStateException("The action failed on the self-healed locator " + r.spec
                    + " (replacing " + origin.selector + "); that replacement is now blocked for future runs."));
            throw e;
        }
    }

    /** Short wait for the original: true if it shows up (or the locator is unusable for another reason). */
    private boolean presentWithin(long ms) {
        try {
            delegate.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(ms));
            return true;
        } catch (TimeoutError e) {
            return safeCount(delegate) > 0;
        } catch (PlaywrightException e) {
            return true; // e.g. strict-mode violation: let the normal call report it exactly as before
        }
    }

    /** Record the element's fingerprint once per run. Never throws; never waits. */
    private void captureBaselineIfNeeded(boolean afterSuccess) {
        String key = origin.key();
        HealingStore store = store();
        if (!store.needsBaseline(key)) return;
        AtomicInteger misses = CAPTURE_ATTEMPTS.computeIfAbsent(store.runId() + key, k -> new AtomicInteger());
        if (misses.get() >= MAX_CAPTURE_ATTEMPTS) return;
        try {
            if (delegate.count() == 1) {
                ElementSnapshot fp = PlaywrightAdapter.fingerprint(delegate);
                store.saveBaseline(key, origin.selector, origin.site, PlaywrightAdapter.pathOf(page.raw().url()), fp);
                return;
            }
        } catch (RuntimeException e) {
            // element went away mid-capture (navigation, re-render) — try next time
        }
        if (afterSuccess) misses.incrementAndGet();
    }

    private Object wrapResult(Method m, Object result) {
        if (result instanceof Locator child) {
            Locator base = target();
            return wrap(child, page, origin.derive(m.getName(), child, base));
        }
        if (result instanceof List<?> list && m.getName().equals("all")) {
            Locator base = target();
            return list.stream()
                    .map(o -> o instanceof Locator l ? wrap(l, page, origin.derive("all", l, base)) : o)
                    .toList();
        }
        return result;
    }

    private static int safeCount(Locator l) {
        try {
            return l.count();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static byte[] screenshot(Locator l) {
        try {
            return l.screenshot(new Locator.ScreenshotOptions().setTimeout(3000));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private HealingEngine engine() {
        return page.engine();
    }

    private HealingConfig config() {
        return page.engine().config();
    }

    private HealingStore store() {
        return page.engine().store();
    }
}
