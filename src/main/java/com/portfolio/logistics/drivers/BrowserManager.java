package com.portfolio.logistics.drivers;

import com.microsoft.playwright.*;
import com.portfolio.logistics.config.ConfigReader;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ThreadLocal Playwright lifecycle. The Playwright + Browser process is created ONCE per test
 * thread and reused across every test that runs on that thread; only the BrowserContext + Page are
 * per-test. Contexts are cheap and fully isolated (their own cookies/storage/cache), so tests still
 * run against a clean session — but we no longer pay a full Chromium launch (~2-4s) on every method.
 *
 * iOS analogy: the Browser is the installed app kept warm; each test gets its own fresh sandbox
 * (context) — no UserDefaults/keychain bleeding between tests — without reinstalling the app.
 */
public final class BrowserManager {

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();

    // Long-lived resources across all threads, closed by a single JVM shutdown hook at the end.
    private static final List<Playwright> ALL_PLAYWRIGHT = new CopyOnWriteArrayList<>();
    private static final List<Browser> ALL_BROWSERS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean HOOK_REGISTERED = new AtomicBoolean(false);

    private BrowserManager() { }

    /** Reuse this thread's Browser (launching it once) and hand back a fresh isolated context + page. */
    public static Page startPage() {
        Browser browser = liveBrowser();

        BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setAcceptDownloads(true));

        Page page = context.newPage();
        page.setDefaultTimeout(ConfigReader.defaultTimeout());
        page.setDefaultNavigationTimeout(ConfigReader.defaultTimeout());

        CONTEXT.set(context);
        PAGE.set(page);
        return page;
    }

    /** The per-thread Browser, launched on first use and relaunched if it has died. */
    private static Browser liveBrowser() {
        Browser browser = BROWSER.get();
        if (browser != null && browser.isConnected()) {
            return browser;
        }
        // First test on this thread, or the previous browser crashed — (re)launch.
        launchForThread();
        return BROWSER.get();
    }

    private static void launchForThread() {
        Playwright pw = Playwright.create();
        Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(ConfigReader.headless())
                .setSlowMo(ConfigReader.slowMo()));
        PLAYWRIGHT.set(pw);
        BROWSER.set(browser);
        ALL_PLAYWRIGHT.add(pw);
        ALL_BROWSERS.add(browser);
        registerShutdownHook();
    }

    public static Page page()              { return PAGE.get(); }
    public static BrowserContext context() { return CONTEXT.get(); }

    /** End of a test: close only the per-test context + page. The Browser stays warm for reuse. */
    public static void stop() {
        try { if (PAGE.get() != null)    PAGE.get().close();    } catch (Exception ignored) { }
        try { if (CONTEXT.get() != null) CONTEXT.get().close(); } catch (Exception ignored) { }
        PAGE.remove();
        CONTEXT.remove();
    }

    /** Close every reused Browser/Playwright once, when the JVM exits (all threads done). */
    private static void registerShutdownHook() {
        if (HOOK_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                for (Browser b : ALL_BROWSERS)     { try { b.close(); } catch (Exception ignored) { } }
                for (Playwright p : ALL_PLAYWRIGHT) { try { p.close(); } catch (Exception ignored) { } }
            }, "playwright-shutdown"));
        }
    }
}
