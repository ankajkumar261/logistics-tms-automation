package com.portfolio.logistics.healing.report;

import com.portfolio.logistics.healing.core.HealingEvent;
import com.portfolio.logistics.healing.core.HealingReporter;
import io.qameta.allure.Allure;

import java.io.ByteArrayInputStream;

/**
 * Puts each heal attempt in the Allure report as a step, at the point in the test where it happened,
 * with the full reasoning and (when one was chosen) a screenshot of the replacement element.
 * The PASS_WITH_HEALING marker itself is set by {@link HealingListener} once the test's result is known.
 */
public final class AllureHealingReporter implements HealingReporter {

    @Override
    public void onEvent(HealingEvent e, byte[] screenshot) {
        String title;
        if (e.applied) {
            title = String.format("Self-healed locator (%.1f%%): %s → %s", e.confidence, e.original, e.healed);
        } else if (e.healed != null && "SUGGESTED".equals(e.decision)) {
            title = String.format("Self-healing suggestion, not applied (%.1f%%): %s → %s", e.confidence, e.original, e.healed);
        } else {
            title = "Self-healing not applied (" + e.decision + "): " + e.original;
        }
        Allure.step(title, () -> {
            Allure.addAttachment("Healing details", "text/plain", e.toReport(), ".txt");
            if (screenshot != null) {
                Allure.addAttachment(e.applied ? "Healed element" : "Suggested element", "image/png",
                        new ByteArrayInputStream(screenshot), "png");
            }
        });
    }
}
