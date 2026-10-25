package com.portfolio.logistics.healing.core.store;

import com.portfolio.logistics.healing.core.ElementSnapshot;
import com.portfolio.logistics.healing.core.HealingEvent;
import com.portfolio.logistics.healing.core.ai.AiVerdict;

import java.util.List;
import java.util.Optional;

/** Memory between runs: baseline fingerprints, heal history, blacklisted heals, cached AI answers. */
public interface HealingStore {

    String runId();

    Optional<BaselineEntry> baseline(String key);

    /** True until a fingerprint has been captured for {@code key} in this run. */
    boolean needsBaseline(String key);

    void saveBaseline(String key, String selector, String callSite, String location, ElementSnapshot fingerprint);

    /** The original locator worked (clears "known broken"). */
    void markWorked(String key);

    /** The original locator found nothing. */
    void markBroken(String key, String selector, String callSite);

    boolean knownBroken(String key);

    void record(HealingEvent event);

    /** Events recorded during this run (this JVM). */
    List<HealingEvent> eventsThisRun();

    /** How many runs before this one applied a heal to {@code key}. */
    long earlierHealedRuns(String key);

    boolean isBlacklisted(String key, String spec);

    void blacklist(String key, String spec);

    Optional<AiVerdict> aiVerdict(String cacheKey);

    void saveAiVerdict(String cacheKey, AiVerdict verdict);

    /** Write everything to disk. Safe to call more than once. */
    void flush();
}
