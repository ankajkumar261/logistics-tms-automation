package com.portfolio.logistics.healing.core;

import com.portfolio.logistics.healing.core.ai.AiLocatorAdvisor;
import com.portfolio.logistics.healing.core.ai.AiVerdict;
import com.portfolio.logistics.healing.core.scoring.ConfidenceScorer;
import com.portfolio.logistics.healing.core.scoring.ScoredCandidate;
import com.portfolio.logistics.healing.core.scoring.StringSimilarity;
import com.portfolio.logistics.healing.core.store.HealingStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Decides whether a broken locator can be replaced, and with what. Driver-neutral: the live UI is
 * reached only through {@link DriverAdapter}.
 *
 * A replacement is applied only when ALL of these hold:
 *  - the best candidate scores at or above {@code healing.threshold} (default 90);
 *  - at least one content signal (id/name/test id, label, text, placeholder) matches strongly —
 *    structure and position alone never qualify;
 *  - it beats the best *different* element by {@code healing.minMargin} points (default 10);
 *  - it is enabled (and editable, for fill/type) — a disabled match is app state, not a broken locator;
 *  - a locator can be built that resolves to exactly that element;
 *  - that replacement has not previously led to a failed test.
 * Anything else is reported and the test fails with its original error.
 */
public final class HealingEngine {

    private static final Logger LOG = LogManager.getLogger("com.portfolio.logistics.healing");
    private static final int AI_CANDIDATES = 10;
    private static final int ALTERNATIVES_SHOWN = 3;

    private final HealingConfig config;
    private final HealingStore store;
    private final ConfidenceScorer scorer;
    private final AiLocatorAdvisor ai;
    private final List<HealingReporter> reporters = new CopyOnWriteArrayList<>();
    private final AtomicInteger aiCalls = new AtomicInteger();

    public HealingEngine(HealingConfig config, HealingStore store, ConfidenceScorer scorer, AiLocatorAdvisor ai) {
        this.config = config;
        this.store = store;
        this.scorer = scorer;
        this.ai = ai;
    }

    public HealingConfig config() {
        return config;
    }

    public HealingStore store() {
        return store;
    }

    public void addReporter(HealingReporter reporter) {
        reporters.add(reporter);
    }

    public HealingResult heal(HealingRequest req, DriverAdapter adapter) {
        HealingEvent ev = newEvent(req, adapter);
        double factor = req.referenceIsBaseline ? 1.0 : config.noBaselineFactor;

        List<ElementSnapshot> found;
        try {
            found = adapter.candidates(req.action);
        } catch (RuntimeException e) {
            return reject(ev, Decision.NO_CANDIDATES, "could not inspect the page: " + firstLine(e));
        }
        if (found.isEmpty()) {
            return reject(ev, Decision.NO_CANDIDATES, "nothing " + (req.scopeSelector != null
                    ? "inside " + req.scopeSelector : "on the page") + " can take a " + req.action
                    + (req.scopeSelector != null ? " (the container itself may be gone)" : ""));
        }

        Map<Integer, ElementSnapshot> byIndex = new HashMap<>();
        found.forEach(c -> byIndex.put(c.index, c));
        List<ScoredCandidate> scored = found.stream()
                .map(c -> scorer.score(req.reference, c, factor))
                .sorted(Comparator.comparingDouble((ScoredCandidate s) -> s.confidence).reversed())
                .toList();
        scored.stream().limit(ALTERNATIVES_SHOWN).forEach(s -> ev.alternatives.add(s.describe()));

        ScoredCandidate best = scored.get(0);
        ScoredCandidate runnerUp = bestOther(scored, best, byIndex);
        Decision verdict = judge(best, runnerUp);

        if (verdict == Decision.LOW_CONFIDENCE && aiEligible(best)) {
            ScoredCandidate aiPick = consultAi(req, scored, byIndex, ev);
            if (aiPick != null) {
                ScoredCandidate other = bestOther(scored, aiPick, byIndex);
                Decision aiVerdict = judge(aiPick, other);
                if (aiVerdict == null) {
                    best = aiPick;
                    runnerUp = other;
                    verdict = null;
                    ev.strategy = "AI_ASSISTED";
                }
            }
        }

        fillScores(ev, best, runnerUp);
        if (verdict != null) {
            return reject(ev, verdict, rejectionReason(verdict, best, runnerUp));
        }
        if (!actionable(best.element, req.action)) {
            return reject(ev, Decision.NOT_ACTIONABLE, best.element.describe() + " is the match but it is disabled"
                    + (needsEditable(req.action) ? " or read-only" : "") + " — that's app state, not a locator problem");
        }

        List<LocatorSpec> specs = adapter.locatorsFor(best.element);
        if (specs.isEmpty()) {
            return reject(ev, Decision.NOT_UNIQUE, "no locator could be built that points at exactly "
                    + best.element.describe());
        }
        for (LocatorSpec s : specs) {
            if (store.isBlacklisted(req.key, s.toString())) {
                ev.healed = s.toString();
                return reject(ev, Decision.BLACKLISTED, "replacing it with " + s + " was tried before and the action "
                        + "failed on that element. Fix the locator in " + req.callSite
                        + " (or remove the entry from " + config.storeDir.resolve("history.json") + ").");
            }
        }
        LocatorSpec spec = specs.get(0);
        ev.healed = spec.toString();
        ev.suggestedCode = spec.javaCode;
        ev.reason = explain(req.reference, best.element);
        long earlier = store.earlierHealedRuns(req.key);
        if (earlier > 0) ev.reason += " — also healed in " + earlier + " earlier run(s); update the page object";

        boolean apply = config.mode == HealingMode.HEAL;
        ev.decision = (apply ? Decision.HEALED : Decision.SUGGESTED).name();
        ev.applied = apply;
        return new HealingResult(ev, spec);
    }

    /** Log, remember and report an event (after the caller has acted on it). */
    public void publish(HealingEvent event, byte[] screenshot) {
        HealingContext.record(event);
        store.record(event);
        LOG.warn(event.toReport());
        for (HealingReporter r : reporters) {
            try {
                r.onEvent(event, screenshot);
            } catch (RuntimeException e) {
                LOG.warn("[HEAL] Reporter {} failed: {}", r.getClass().getSimpleName(), e.toString());
            }
        }
    }

    /** Null when the candidate qualifies; otherwise why it doesn't. */
    private Decision judge(ScoredCandidate best, ScoredCandidate runnerUp) {
        if (best.confidence < config.threshold) return Decision.LOW_CONFIDENCE;
        if (!best.contentEvidence) return Decision.INSUFFICIENT_EVIDENCE;
        if (runnerUp != null && best.confidence - runnerUp.confidence < config.minMargin) return Decision.AMBIGUOUS;
        return null;
    }

    /**
     * Disabled elements stay in the candidate list so they still count as look-alikes in the margin
     * check; they just can't be the replacement for an action that needs an enabled element.
     */
    private static boolean actionable(ElementSnapshot e, ActionKind action) {
        boolean needsEnabled = switch (action) {
            case CLICK, FILL, TYPE, PRESS, CHECK, SELECT -> true;
            default -> false;
        };
        if (needsEnabled && Boolean.FALSE.equals(e.enabled)) return false;
        return !needsEditable(action) || !Boolean.FALSE.equals(e.editable);
    }

    private static boolean needsEditable(ActionKind action) {
        return action == ActionKind.FILL || action == ActionKind.TYPE;
    }

    /**
     * The highest-scoring candidate that is a different element from {@code best} — not the same
     * index, and not an ancestor/descendant of it (a label wrapping its input, a span inside its
     * button are the same click target, not competitors).
     */
    private static ScoredCandidate bestOther(List<ScoredCandidate> scored, ScoredCandidate best, Map<Integer, ElementSnapshot> byIndex) {
        for (ScoredCandidate s : scored) {
            if (s.element.index.equals(best.element.index)) continue;
            if (related(s.element, best.element, byIndex) || related(best.element, s.element, byIndex)) continue;
            return s;
        }
        return null;
    }

    private static boolean related(ElementSnapshot child, ElementSnapshot ancestor, Map<Integer, ElementSnapshot> byIndex) {
        Integer i = child.ancestorIndex;
        for (int hops = 0; i != null && i >= 0 && hops < 50; hops++) {
            if (i.equals(ancestor.index)) return true;
            ElementSnapshot p = byIndex.get(i);
            i = p == null ? null : p.ancestorIndex;
        }
        return false;
    }

    private boolean aiEligible(ScoredCandidate best) {
        return config.aiEnabled && ai != null && best.confidence >= config.aiMinScore;
    }

    /**
     * Ask the AI to pick among the top candidates. Its confidence is averaged with the deterministic
     * score of the element it picked, so it can lift a strong-but-unsure match (≥80) over the line
     * but cannot rescue a weak one. Answers are cached so the same break doesn't call it again.
     */
    private ScoredCandidate consultAi(HealingRequest req, List<ScoredCandidate> scored,
                                      Map<Integer, ElementSnapshot> byIndex, HealingEvent ev) {
        List<ScoredCandidate> top = scored.stream().limit(AI_CANDIDATES).toList();
        String cacheKey = req.key + "|" + top.stream().map(s -> s.element.describe()).collect(Collectors.joining(";")).hashCode();
        Optional<AiVerdict> verdict = store.aiVerdict(cacheKey);
        if (verdict.isEmpty()) {
            if (aiCalls.incrementAndGet() > config.aiMaxCallsPerRun) {
                ev.aiRationale = "not asked: AI call budget for this run (" + config.aiMaxCallsPerRun + ") is used up";
                return null;
            }
            verdict = ai.choose(req.originalSelector, req.action, req.reference,
                    top.stream().map(s -> s.element).toList());
            verdict.ifPresent(v -> store.saveAiVerdict(cacheKey, v));
        }
        if (verdict.isEmpty()) {
            ev.aiRationale = "AI advisor unavailable";
            return null;
        }
        AiVerdict v = verdict.get();
        ev.aiRationale = String.format("picked #%d at %.0f%% — %s", v.choice, v.confidence, v.rationale);
        if (v.choice < 0 || v.choice >= top.size()) return null;
        ScoredCandidate picked = top.get(v.choice);
        double blended = Math.round((0.5 * picked.confidence + 0.5 * Math.max(0, Math.min(100, v.confidence))) * 10) / 10.0;
        return picked.withConfidence(blended);
    }

    private HealingEvent newEvent(HealingRequest req, DriverAdapter adapter) {
        HealingEvent ev = new HealingEvent();
        ev.runId = store.runId();
        ev.timestamp = Instant.now().toString();
        ev.platform = adapter.platform();
        ev.key = req.key;
        ev.callSite = req.callSite;
        ev.action = req.action.name();
        ev.original = req.originalSelector;
        ev.scope = req.scopeSelector;
        ev.strategy = req.referenceIsBaseline ? "BASELINE" : "SELECTOR_INTENT";
        try {
            ev.location = adapter.location();
        } catch (RuntimeException e) {
            ev.location = "?";
        }
        return ev;
    }

    private static void fillScores(HealingEvent ev, ScoredCandidate best, ScoredCandidate runnerUp) {
        ev.confidence = best.confidence;
        ev.runnerUpConfidence = runnerUp == null ? null : runnerUp.confidence;
        ev.chosenElement = best.element.describe();
        best.signals.forEach((k, v) -> ev.signals.put(k.name(), Math.round(v * 100) / 100.0));
    }

    private static HealingResult reject(HealingEvent ev, Decision d, String reason) {
        ev.decision = d.name();
        ev.applied = false;
        ev.reason = reason;
        return new HealingResult(ev, null);
    }

    private String rejectionReason(Decision d, ScoredCandidate best, ScoredCandidate runnerUp) {
        return switch (d) {
            case LOW_CONFIDENCE -> String.format("best candidate %s scored %.1f%% (needs %.0f%%)",
                    best.element.describe(), best.confidence, config.threshold);
            case INSUFFICIENT_EVIDENCE -> "only role/structure/position matched " + best.element.describe()
                    + "; nothing about its id, label or text did";
            case AMBIGUOUS -> String.format("%s (%.1f%%) and %s (%.1f%%) are within %.0f points — can't tell which is right",
                    best.element.describe(), best.confidence, runnerUp.element.describe(), runnerUp.confidence, config.minMargin);
            default -> d.name();
        };
    }

    /** Which identifying attributes changed and which stayed the same between reference and the chosen element. */
    static String explain(ElementSnapshot ref, ElementSnapshot got) {
        List<String> changed = new ArrayList<>();
        List<String> same = new ArrayList<>();
        compare("id", ref.id, got.id, changed, same);
        compare("name", ref.name, got.name, changed, same);
        compare("test id", ref.testId, got.testId, changed, same);
        compare("label", firstPresent(ref.labelText, ref.ariaLabel, ref.accessibleName),
                firstPresent(got.labelText, got.ariaLabel, got.accessibleName), changed, same);
        compare("text", ref.text, got.text, changed, same);
        compare("placeholder", ref.placeholder, got.placeholder, changed, same);
        compare("link", ref.href, got.href, changed, same);
        compare("role", ref.role, got.role, changed, same);
        if (ElementSnapshot.present(ref.heading) && ref.heading.equals(got.heading)) same.add("section '" + ref.heading + "'");
        StringBuilder sb = new StringBuilder();
        if (!changed.isEmpty()) sb.append(String.join("; ", changed));
        if (!same.isEmpty()) sb.append(sb.length() > 0 ? "; unchanged: " : "unchanged: ").append(String.join(", ", same));
        return sb.length() == 0 ? "closest match to " + ref.describe() : sb.toString();
    }

    private static void compare(String field, String before, String after, List<String> changed, List<String> same) {
        if (!ElementSnapshot.present(before)) return;
        if (StringSimilarity.normalize(before).equals(StringSimilarity.normalize(after))) {
            same.add(field.equals("role") || field.equals("id") || field.equals("name") ? field : field + " '" + before + "'");
        } else if (!ElementSnapshot.present(after)) {
            changed.add(field + " '" + before + "' removed");
        } else {
            changed.add(field + " '" + before + "' → '" + after + "'");
        }
    }

    private static String firstPresent(String... v) {
        return ElementSnapshot.firstPresent(v);
    }

    private static String firstLine(Throwable e) {
        String m = String.valueOf(e.getMessage());
        int nl = m.indexOf('\n');
        return nl > 0 ? m.substring(0, nl) : m;
    }
}
