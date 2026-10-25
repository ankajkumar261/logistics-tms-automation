package com.portfolio.logistics.healing.core.scoring;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Signal weights for a platform. Confidence is the weighted average of the signals the reference
 * actually has, so a missing attribute neither helps nor hurts. A mobile profile would weight
 * resource-id / accessibility id higher and drop structure (page-source XML is deeply nested).
 */
public final class ScoringProfile {

    /** A content signal must reach this similarity for the candidate to count as identified. */
    public final double contentEvidenceMin;
    /** Pixels between element centres at which GEOMETRY falls to 0. */
    public final double geometryFalloffPx;
    private final Map<Signal, Double> weights;

    public ScoringProfile(Map<Signal, Double> weights, double contentEvidenceMin, double geometryFalloffPx) {
        this.weights = Collections.unmodifiableMap(new EnumMap<>(weights));
        this.contentEvidenceMin = contentEvidenceMin;
        this.geometryFalloffPx = geometryFalloffPx;
    }

    public static ScoringProfile web() {
        Map<Signal, Double> w = new EnumMap<>(Signal.class);
        w.put(Signal.IDENTITY, 30.0);
        w.put(Signal.ACCESSIBLE_NAME, 25.0);
        w.put(Signal.ROLE, 15.0);
        w.put(Signal.TEXT, 10.0);
        w.put(Signal.STRUCTURE, 10.0);
        w.put(Signal.HINTS, 5.0);
        w.put(Signal.GEOMETRY, 5.0);
        return new ScoringProfile(w, 0.8, 400);
    }

    public double weight(Signal s) {
        return weights.getOrDefault(s, 0.0);
    }
}
