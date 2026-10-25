package com.portfolio.logistics.healing.core.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.portfolio.logistics.healing.core.ElementSnapshot;
import com.portfolio.logistics.healing.core.HealingEvent;
import com.portfolio.logistics.healing.core.ai.AiVerdict;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link HealingStore} backed by two JSON files in {@code healing.store.dir} (default {@code .healing/}):
 *  - baseline.json — element fingerprints per locator key
 *  - history.json  — heal events, blacklisted heals, cached AI answers
 * Loaded once, kept in memory, written at the end of the run (atomically: temp file + rename).
 * Old data is pruned on write: baselines unseen for 60 days, events older than 90 days, max 5000 events.
 */
public final class JsonHealingStore implements HealingStore {

    private static final Logger LOG = LogManager.getLogger("com.portfolio.logistics.healing");
    private static final Duration BASELINE_TTL = Duration.ofDays(60);
    private static final Duration HISTORY_TTL = Duration.ofDays(90);
    private static final int MAX_EVENTS = 5000;

    private final Path dir;
    private final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Map<String, BaselineEntry> baselines = new ConcurrentHashMap<>();
    private final List<HealingEvent> events = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Set<String>> blacklist = new ConcurrentHashMap<>();
    private final Map<String, AiVerdict> aiVerdicts = new ConcurrentHashMap<>();
    private final Set<String> capturedThisRun = ConcurrentHashMap.newKeySet();

    /** On-disk shapes. */
    private static final class BaselineFile {
        int version = 1;
        Map<String, BaselineEntry> entries = new LinkedHashMap<>();
    }

    private static final class HistoryFile {
        int version = 1;
        List<HealingEvent> events = new ArrayList<>();
        Map<String, List<String>> blacklist = new LinkedHashMap<>();
        Map<String, AiVerdict> aiVerdicts = new LinkedHashMap<>();
    }

    public JsonHealingStore(Path dir) {
        this.dir = dir;
        BaselineFile b = read(dir.resolve("baseline.json"), BaselineFile.class);
        if (b != null && b.entries != null) baselines.putAll(b.entries);
        HistoryFile h = read(dir.resolve("history.json"), HistoryFile.class);
        if (h != null) {
            if (h.events != null) events.addAll(h.events);
            if (h.blacklist != null) h.blacklist.forEach((k, v) -> v.forEach(spec -> blacklist(k, spec)));
            if (h.aiVerdicts != null) aiVerdicts.putAll(h.aiVerdicts);
        }
    }

    @Override
    public String runId() {
        return runId;
    }

    @Override
    public Optional<BaselineEntry> baseline(String key) {
        return Optional.ofNullable(baselines.get(key));
    }

    @Override
    public boolean needsBaseline(String key) {
        return !capturedThisRun.contains(key);
    }

    @Override
    public void saveBaseline(String key, String selector, String callSite, String location, ElementSnapshot fingerprint) {
        long now = System.currentTimeMillis();
        BaselineEntry e = entry(key, selector, callSite);
        e.fingerprint = fingerprint;
        e.location = location;
        e.lastSeen = now;
        e.lastWorkedAt = Math.max(now, e.lastBrokenAt + 1);
        capturedThisRun.add(key);
    }

    @Override
    public void markWorked(String key) {
        BaselineEntry e = baselines.get(key);
        if (e == null) return;
        long now = System.currentTimeMillis();
        e.lastSeen = now;
        e.lastWorkedAt = Math.max(now, e.lastBrokenAt + 1);
    }

    @Override
    public void markBroken(String key, String selector, String callSite) {
        long now = System.currentTimeMillis();
        BaselineEntry e = entry(key, selector, callSite);
        e.lastSeen = now;
        e.lastBrokenAt = Math.max(now, e.lastWorkedAt + 1);
    }

    @Override
    public boolean knownBroken(String key) {
        BaselineEntry e = baselines.get(key);
        return e != null && e.knownBroken();
    }

    private BaselineEntry entry(String key, String selector, String callSite) {
        return baselines.computeIfAbsent(key, k -> {
            BaselineEntry e = new BaselineEntry();
            e.key = k;
            e.selector = selector;
            e.callSite = callSite;
            e.firstSeen = System.currentTimeMillis();
            return e;
        });
    }

    @Override
    public void record(HealingEvent event) {
        events.add(event);
    }

    @Override
    public List<HealingEvent> eventsThisRun() {
        synchronized (events) {
            return events.stream().filter(e -> runId.equals(e.runId)).toList();
        }
    }

    @Override
    public long earlierHealedRuns(String key) {
        synchronized (events) {
            return events.stream()
                    .filter(e -> e.applied && key.equals(e.key) && !runId.equals(e.runId))
                    .map(e -> e.runId).distinct().count();
        }
    }

    @Override
    public boolean isBlacklisted(String key, String spec) {
        Set<String> s = blacklist.get(key);
        return s != null && s.contains(spec);
    }

    @Override
    public void blacklist(String key, String spec) {
        blacklist.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(spec);
    }

    @Override
    public Optional<AiVerdict> aiVerdict(String cacheKey) {
        return Optional.ofNullable(aiVerdicts.get(cacheKey));
    }

    @Override
    public void saveAiVerdict(String cacheKey, AiVerdict verdict) {
        aiVerdicts.put(cacheKey, verdict);
    }

    @Override
    public synchronized void flush() {
        try {
            Files.createDirectories(dir);
            long now = System.currentTimeMillis();

            BaselineFile b = new BaselineFile();
            baselines.values().stream()
                    .filter(e -> now - e.lastSeen < BASELINE_TTL.toMillis())
                    .sorted(Comparator.comparing(e -> e.key))
                    .forEach(e -> b.entries.put(e.key, e));
            write(dir.resolve("baseline.json"), b);

            HistoryFile h = new HistoryFile();
            Instant cutoff = Instant.now().minus(HISTORY_TTL);
            synchronized (events) {
                List<HealingEvent> kept = events.stream()
                        .filter(e -> e.timestamp == null || Instant.parse(e.timestamp).isAfter(cutoff))
                        .toList();
                h.events = new ArrayList<>(kept.subList(Math.max(0, kept.size() - MAX_EVENTS), kept.size()));
            }
            blacklist.forEach((k, v) -> h.blacklist.put(k, v.stream().sorted().toList()));
            h.aiVerdicts.putAll(aiVerdicts);
            write(dir.resolve("history.json"), h);
        } catch (Exception e) {
            LOG.warn("[HEAL] Could not write healing store to {}: {}", dir.toAbsolutePath(), e.toString());
        }
    }

    private <T> T read(Path file, Class<T> type) {
        if (!Files.exists(file)) return null;
        try {
            return gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
            LOG.warn("[HEAL] {} is unreadable ({}); moved to {} and starting fresh", file, e.getMessage(), aside.getFileName());
            try { Files.move(file, aside); } catch (IOException ignored) { }
            return null;
        }
    }

    private void write(Path file, Object data) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, gson.toJson(data), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
