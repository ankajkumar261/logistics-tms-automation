package com.portfolio.logistics.healing.core;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

/**
 * Self-healing settings. Each value is read from (first match wins):
 *   1. a -D system property, e.g. {@code mvn test -Dhealing.mode=SUGGEST}
 *   2. config.properties on the classpath (the same file the rest of the suite uses)
 *   3. the defaults below.
 * Deliberately independent of ConfigReader so the core can move to its own module (Appium) unchanged,
 * and so the healing self-tests run without a filled-in config.properties.
 */
public final class HealingConfig {

    public final HealingMode mode;
    /** Minimum confidence (0–100) to apply a heal. */
    public final double threshold;
    /** The best candidate must beat the runner-up by at least this many points. */
    public final double minMargin;
    /** Multiplier when there is no baseline and the reference comes only from the selector text. */
    public final double noBaselineFactor;
    /** Calls with an explicit timeout at or below this are treated as "is it there?" probes: never healed. */
    public final long probeTimeoutMs;
    /** How long to give an original locator that was already broken (this run or last) before healing. */
    public final long knownBrokenProbeMs;
    /** Heal innerText/textContent/inputValue/getAttribute too (off: those values feed assertions). */
    public final boolean healReads;
    public final int maxCandidates;
    /** Where baselines and history live between runs (survives mvn clean). */
    public final Path storeDir;
    /** Where per-run reports (summary, log) go. */
    public final Path reportDir;

    public final boolean aiEnabled;
    public final String aiModel;
    public final int aiMaxCallsPerRun;
    /** The AI is only asked when the best deterministic score is at least this (0–100). */
    public final double aiMinScore;
    public final int aiTimeoutSeconds;

    private HealingConfig(Builder b) {
        this.mode = b.mode;
        this.threshold = b.threshold;
        this.minMargin = b.minMargin;
        this.noBaselineFactor = b.noBaselineFactor;
        this.probeTimeoutMs = b.probeTimeoutMs;
        this.knownBrokenProbeMs = b.knownBrokenProbeMs;
        this.healReads = b.healReads;
        this.maxCandidates = b.maxCandidates;
        this.storeDir = b.storeDir;
        this.reportDir = b.reportDir;
        this.aiEnabled = b.aiEnabled;
        this.aiModel = b.aiModel;
        this.aiMaxCallsPerRun = b.aiMaxCallsPerRun;
        this.aiMinScore = b.aiMinScore;
        this.aiTimeoutSeconds = b.aiTimeoutSeconds;
    }

    public static HealingConfig load() {
        Properties file = new Properties();
        try (InputStream in = HealingConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) file.load(in);
        } catch (Exception ignored) {
            // no config file → defaults; the suite's own ConfigReader reports a missing file loudly
        }
        Source src = key -> {
            String v = System.getProperty(key);
            if (v == null) v = file.getProperty(key);
            return v == null || v.isBlank() ? null : v.trim();
        };
        Builder b = builder();
        b.mode(HealingMode.valueOf(src.get("healing.mode", b.mode.name()).toUpperCase(Locale.ROOT)));
        b.threshold(Double.parseDouble(src.get("healing.threshold", String.valueOf(b.threshold))));
        b.minMargin(Double.parseDouble(src.get("healing.minMargin", String.valueOf(b.minMargin))));
        b.noBaselineFactor(Double.parseDouble(src.get("healing.noBaselineFactor", String.valueOf(b.noBaselineFactor))));
        b.probeTimeoutMs(Long.parseLong(src.get("healing.probeTimeoutMs", String.valueOf(b.probeTimeoutMs))));
        b.knownBrokenProbeMs(Long.parseLong(src.get("healing.knownBrokenProbeMs", String.valueOf(b.knownBrokenProbeMs))));
        b.healReads(Boolean.parseBoolean(src.get("healing.healReads", String.valueOf(b.healReads))));
        b.maxCandidates(Integer.parseInt(src.get("healing.maxCandidates", String.valueOf(b.maxCandidates))));
        b.storeDir(Paths.get(src.get("healing.store.dir", b.storeDir.toString())));
        b.reportDir(Paths.get(src.get("healing.report.dir", b.reportDir.toString())));
        b.aiEnabled(Boolean.parseBoolean(src.get("healing.ai.enabled", String.valueOf(b.aiEnabled))));
        b.aiModel(src.get("healing.ai.model", b.aiModel));
        b.aiMaxCallsPerRun(Integer.parseInt(src.get("healing.ai.maxCallsPerRun", String.valueOf(b.aiMaxCallsPerRun))));
        b.aiMinScore(Double.parseDouble(src.get("healing.ai.minScore", String.valueOf(b.aiMinScore))));
        b.aiTimeoutSeconds(Integer.parseInt(src.get("healing.ai.timeoutSeconds", String.valueOf(b.aiTimeoutSeconds))));
        return b.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "mode=" + mode + ", threshold=" + threshold + "%, minMargin=" + minMargin
                + ", probeTimeoutMs=" + probeTimeoutMs + ", healReads=" + healReads
                + ", store=" + storeDir + ", ai=" + (aiEnabled ? "ON (" + aiModel + ", max " + aiMaxCallsPerRun + "/run)" : "OFF");
    }

    @FunctionalInterface
    private interface Source {
        String get(String key);

        default String get(String key, String fallback) {
            String v = get(key);
            return v == null ? fallback : v;
        }
    }

    public static final class Builder {
        private HealingMode mode = HealingMode.HEAL;
        private double threshold = 90;
        private double minMargin = 10;
        private double noBaselineFactor = 0.95;
        private long probeTimeoutMs = 5000;
        private long knownBrokenProbeMs = 2000;
        private boolean healReads = false;
        private int maxCandidates = 800;
        private Path storeDir = Paths.get(".healing");
        private Path reportDir = Paths.get("target", "healing");
        private boolean aiEnabled = false;
        private String aiModel = "claude-opus-5-5";
        private int aiMaxCallsPerRun = 10;
        private double aiMinScore = 75;
        private int aiTimeoutSeconds = 30;

        public Builder mode(HealingMode v) { mode = v; return this; }
        public Builder threshold(double v) { threshold = v; return this; }
        public Builder minMargin(double v) { minMargin = v; return this; }
        public Builder noBaselineFactor(double v) { noBaselineFactor = v; return this; }
        public Builder probeTimeoutMs(long v) { probeTimeoutMs = v; return this; }
        public Builder knownBrokenProbeMs(long v) { knownBrokenProbeMs = v; return this; }
        public Builder healReads(boolean v) { healReads = v; return this; }
        public Builder maxCandidates(int v) { maxCandidates = v; return this; }
        public Builder storeDir(Path v) { storeDir = v; return this; }
        public Builder reportDir(Path v) { reportDir = v; return this; }
        public Builder aiEnabled(boolean v) { aiEnabled = v; return this; }
        public Builder aiModel(String v) { aiModel = v; return this; }
        public Builder aiMaxCallsPerRun(int v) { aiMaxCallsPerRun = v; return this; }
        public Builder aiMinScore(double v) { aiMinScore = v; return this; }
        public Builder aiTimeoutSeconds(int v) { aiTimeoutSeconds = v; return this; }

        public HealingConfig build() {
            return new HealingConfig(this);
        }
    }
}
