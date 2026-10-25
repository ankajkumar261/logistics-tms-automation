package com.portfolio.logistics.healing.report;

import com.google.gson.GsonBuilder;
import com.portfolio.logistics.healing.core.HealingConfig;
import com.portfolio.logistics.healing.core.HealingEngine;
import com.portfolio.logistics.healing.core.HealingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * End-of-run report: console summary, {@code target/healing/healing-summary.md} (what to fix, with
 * paste-ready code) and {@code healing-summary.json}, plus the totals in Allure's Environment widget.
 */
final class HealingSummary {

    private static final Logger LOG = LogManager.getLogger("com.portfolio.logistics.healing");

    private HealingSummary() { }

    static void write(HealingEngine engine) {
        HealingConfig cfg = engine.config();
        List<HealingEvent> events = engine.store().eventsThisRun();
        List<HealingEvent> healed = events.stream().filter(e -> e.applied).toList();
        List<HealingEvent> notApplied = events.stream().filter(e -> !e.applied).toList();
        long passWithHealing = distinctTests(healed, HealingEvent.Outcome.PASSED);
        long failedAfterHeal = distinctTests(healed, HealingEvent.Outcome.FAILED);

        Map<String, String> env = new LinkedHashMap<>();
        env.put("Self-healing", cfg.mode + " (threshold " + fmt(cfg.threshold) + "%, AI " + (cfg.aiEnabled ? "on" : "off") + ")");
        env.put("Self-healing.locatorsHealed", String.valueOf(healed.size()));
        env.put("Self-healing.testsPassWithHealing", String.valueOf(passWithHealing));
        env.put("Self-healing.healsNotApplied", String.valueOf(notApplied.size()));
        AllureResults.writeEnvironment(env);

        if (events.isEmpty()) {
            LOG.info("[HEAL] Self-healing: no broken locators this run.");
            return;
        }

        // One row per broken locator: the latest event for each key.
        Map<String, HealingEvent> toFix = healed.stream().collect(Collectors.toMap(e -> e.key, Function.identity(),
                (a, b) -> b, LinkedHashMap::new));
        Path dir = cfg.reportDir;
        StringBuilder md = new StringBuilder();
        md.append("# Self-healing summary — run ").append(engine.store().runId()).append("\n\n");
        md.append("Mode ").append(cfg.mode).append(" · threshold ").append(fmt(cfg.threshold)).append("% · margin ")
                .append(fmt(cfg.minMargin)).append(" points · AI ").append(cfg.aiEnabled ? "on" : "off").append("\n\n");
        md.append("| | Count |\n|---|---|\n");
        md.append("| Locators healed | ").append(healed.size()).append(" |\n");
        md.append("| Tests PASS_WITH_HEALING | ").append(passWithHealing).append(" |\n");
        md.append("| Tests that still failed after a heal | ").append(failedAfterHeal).append(" |\n");
        md.append("| Heals not applied (test failed with its original error) | ").append(notApplied.size()).append(" |\n\n");

        if (!toFix.isEmpty()) {
            md.append("## Fix these locators in the page objects\n\n");
            md.append("| Where | Original | Replacement | Confidence | Healed in earlier runs |\n|---|---|---|---|---|\n");
            for (HealingEvent e : toFix.values()) {
                md.append("| `").append(e.callSite).append("` | `").append(cell(e.original)).append("` | `")
                        .append(cell(Objects.toString(e.suggestedCode, e.healed))).append("` | ")
                        .append(fmt(e.confidence)).append("% | ").append(engine.store().earlierHealedRuns(e.key)).append(" |\n");
            }
            md.append('\n');
        }
        if (!notApplied.isEmpty()) {
            md.append("## Not applied\n\n| Test | Where | Original | Decision | Reason |\n|---|---|---|---|---|\n");
            for (HealingEvent e : notApplied) {
                md.append("| ").append(e.testId).append(" | `").append(e.callSite).append("` | `").append(cell(e.original))
                        .append("` | ").append(e.decision).append(" | ").append(cell(e.reason)).append(" |\n");
            }
            md.append('\n');
        }
        md.append("## All events\n\n");
        events.forEach(e -> md.append("```\n").append(e.toReport()).append("outcome   : ").append(e.outcome).append("\n```\n\n"));

        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("healing-summary.md"), md, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("healing-summary.json"),
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(events), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            LOG.warn("[HEAL] Could not write healing summary to {}: {}", dir, ex.toString());
        }

        StringBuilder console = new StringBuilder("\n[HEAL] ===== Self-healing summary (" + cfg.mode + ", threshold "
                + fmt(cfg.threshold) + "%) =====\n");
        console.append("[HEAL] Locators healed: ").append(healed.size())
                .append(" | tests PASS_WITH_HEALING: ").append(passWithHealing)
                .append(" | tests failed after a heal: ").append(failedAfterHeal)
                .append(" | heals not applied: ").append(notApplied.size()).append('\n');
        toFix.values().forEach(e -> console.append("[HEAL]   fix ").append(e.callSite).append(": ").append(e.original)
                .append("  →  ").append(Objects.toString(e.suggestedCode, e.healed)).append('\n'));
        console.append("[HEAL] Details: ").append(dir.resolve("healing-summary.md").toAbsolutePath());
        LOG.info(console.toString());
    }

    private static long distinctTests(List<HealingEvent> events, HealingEvent.Outcome outcome) {
        return events.stream().filter(e -> e.outcome == outcome).map(e -> e.testId).distinct().count();
    }

    private static String cell(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format("%.1f", d);
    }
}
