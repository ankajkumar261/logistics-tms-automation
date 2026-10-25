package com.portfolio.logistics.healing.core.scoring;

import com.portfolio.logistics.healing.core.ElementSnapshot;

import java.util.Collections;
import java.util.Map;

/** A candidate element with its confidence (0–100) and the per-signal similarities behind it. */
public final class ScoredCandidate {

    public final ElementSnapshot element;
    public final double confidence;
    public final Map<Signal, Double> signals;
    /** At least one content signal (identity, label, text, hints) matched strongly. */
    public final boolean contentEvidence;

    public ScoredCandidate(ElementSnapshot element, double confidence, Map<Signal, Double> signals, boolean contentEvidence) {
        this.element = element;
        this.confidence = confidence;
        this.signals = Collections.unmodifiableMap(signals);
        this.contentEvidence = contentEvidence;
    }

    public ScoredCandidate withConfidence(double newConfidence) {
        return new ScoredCandidate(element, newConfidence, signals, contentEvidence);
    }

    public String describe() {
        return element.describe() + String.format(" (%.1f%%)", confidence);
    }
}
