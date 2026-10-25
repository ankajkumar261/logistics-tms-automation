package com.portfolio.logistics.healing;

import com.microsoft.playwright.Page;
import com.portfolio.logistics.healing.core.HealingConfig;
import com.portfolio.logistics.healing.core.HealingContext;
import com.portfolio.logistics.healing.core.HealingEngine;
import com.portfolio.logistics.healing.core.HealingMode;
import com.portfolio.logistics.healing.core.ai.AiLocatorAdvisor;
import com.portfolio.logistics.healing.core.scoring.ConfidenceScorer;
import com.portfolio.logistics.healing.core.scoring.ScoringProfile;
import com.portfolio.logistics.healing.core.store.JsonHealingStore;
import com.portfolio.logistics.healing.playwright.HealingPageHandler;
import com.portfolio.logistics.healing.report.AllureHealingReporter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * Entry point for self-healing locators.
 *
 * <pre>
 *   page = Healing.wrap(BrowserManager.startPage());   // BaseTest — page objects and tests stay as they are
 *   Healing.withoutHealing(() -> ...);                  // exact locators only, for a block of code
 * </pre>
 *
 * Settings: {@code healing.*} in config.properties or -D (see {@link HealingConfig}). With
 * {@code healing.mode=OFF}, {@link #wrap} returns the page untouched.
 */
public final class Healing {

    private static final Logger LOG = LogManager.getLogger("com.portfolio.logistics.healing");
    private static volatile HealingEngine engine;

    private Healing() { }

    /** The shared engine, created on first use from {@link HealingConfig#load()}. */
    public static HealingEngine engine() {
        HealingEngine e = engine;
        if (e == null) {
            synchronized (Healing.class) {
                e = engine;
                if (e == null) {
                    e = create(HealingConfig.load());
                    engine = e;
                    HealingEngine finalEngine = e;
                    // The TestNG listener flushes at the end of the run; this covers runs without it.
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> finalEngine.store().flush(), "healing-store-flush"));
                }
            }
        }
        return e;
    }

    public static boolean isInitialized() {
        return engine != null;
    }

    public static Page wrap(Page page) {
        return wrap(page, engine());
    }

    public static Page wrap(Page page, HealingEngine engine) {
        if (page == null || engine.config().mode == HealingMode.OFF) return page;
        return HealingPageHandler.wrap(page, engine);
    }

    /** Run {@code body} with exact locators only (no healing) on this thread. */
    public static <T> T withoutHealing(Supplier<T> body) {
        return HealingContext.suppress(body);
    }

    public static void withoutHealing(Runnable body) {
        HealingContext.suppress(() -> {
            body.run();
            return null;
        });
    }

    static HealingEngine create(HealingConfig config) {
        AiLocatorAdvisor ai = null;
        if (config.aiEnabled) {
            // Loaded only when switched on, so the Anthropic SDK isn't touched otherwise.
            ai = new com.portfolio.logistics.healing.ai.ClaudeLocatorAdvisor(config);
        }
        HealingEngine e = new HealingEngine(config, new JsonHealingStore(config.storeDir),
                new ConfidenceScorer(ScoringProfile.web()), ai);
        e.addReporter(new AllureHealingReporter());
        LOG.info("[HEAL] Self-healing locators: {}", config);
        return e;
    }
}
