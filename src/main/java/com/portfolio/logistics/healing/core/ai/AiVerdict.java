package com.portfolio.logistics.healing.core.ai;

/** The AI advisor's pick: a candidate index (or -1 for "none of these"), its confidence 0–100, and why. */
public class AiVerdict {

    public int choice;
    public double confidence;
    public String rationale;

    public AiVerdict() { }

    public AiVerdict(int choice, double confidence, String rationale) {
        this.choice = choice;
        this.confidence = confidence;
        this.rationale = rationale;
    }
}
