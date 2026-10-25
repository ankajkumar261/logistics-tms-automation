package com.portfolio.logistics.healing.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One heal attempt, applied or not: what broke, what replaced it (if anything), how sure the engine
 * was and why. Logged, attached to Allure, and kept in {@code .healing/history.json}.
 */
public class HealingEvent {

    public enum Outcome {
        /** Test still running. */
        PENDING,
        PASSED,
        FAILED,
        /** That attempt failed and TestNG's RetryAnalyzer ran the test again. */
        RETRIED,
        SKIPPED,
        /** Happened outside a test method (e.g. teardown). */
        NO_TEST
    }

    public String runId;
    public String timestamp;
    public String platform;
    public String testId;
    public String key;
    public String callSite;
    public String action;
    public String location;
    public String original;
    public String scope;
    public String decision;
    public boolean applied;
    /** 0–100. */
    public double confidence;
    public Double runnerUpConfidence;
    /** BASELINE (fingerprint from an earlier pass), SELECTOR_INTENT (no baseline), or AI_ASSISTED. */
    public String strategy;
    public String healed;
    public String suggestedCode;
    public String chosenElement;
    public String reason;
    public Map<String, Double> signals = new LinkedHashMap<>();
    public List<String> alternatives = new ArrayList<>();
    public String aiRationale;
    public Outcome outcome = Outcome.PENDING;

    public Decision decision() {
        return Decision.valueOf(decision);
    }

    /** Multi-line text used for both the log and the Allure attachment. */
    public String toReport() {
        StringBuilder sb = new StringBuilder();
        sb.append(applied ? "[HEAL] " : "[HEAL-" + decision + "] ").append(callSite)
                .append("  action=").append(action).append("  at=").append(location).append('\n');
        sb.append("  test      : ").append(testId).append('\n');
        sb.append("  original  : ").append(original);
        if (scope != null) sb.append("   (inside ").append(scope).append(')');
        sb.append('\n');
        if (healed != null) {
            sb.append(applied ? "  healed    : " : "  suggested : ").append(healed).append('\n');
        }
        if (chosenElement != null) {
            sb.append("  element   : ").append(chosenElement).append('\n');
            sb.append("  confidence: ").append(String.format("%.1f%%", confidence));
            if (runnerUpConfidence != null) sb.append("   runner-up ").append(String.format("%.1f%%", runnerUpConfidence));
            sb.append("   via ").append(strategy).append('\n');
        }
        if (!signals.isEmpty()) {
            sb.append("  signals   : ");
            signals.forEach((k, v) -> sb.append(k.toLowerCase()).append('=').append(String.format("%.2f", v)).append(' '));
            sb.append('\n');
        }
        sb.append("  reason    : ").append(reason).append('\n');
        if (aiRationale != null) sb.append("  ai        : ").append(aiRationale).append('\n');
        if (suggestedCode != null) sb.append("  fix       : ").append(suggestedCode).append('\n');
        if (!alternatives.isEmpty()) {
            sb.append("  candidates:\n");
            alternatives.forEach(a -> sb.append("    - ").append(a).append('\n'));
        }
        return sb.toString();
    }

    /** One line for console summaries. */
    public String oneLine() {
        return String.format("%s  %s  %s → %s  (%.1f%%)", decision, callSite, original,
                healed == null ? "-" : healed, confidence);
    }
}
