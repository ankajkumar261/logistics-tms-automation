package com.portfolio.logistics.healing.core.scoring;

import com.portfolio.logistics.healing.core.ElementSnapshot;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores how likely a candidate is to be the element described by a reference snapshot.
 *
 * Each signal yields a similarity 0..1, or null when the reference has nothing to compare (null
 * signals are left out, so the weights of the remaining ones are re-normalised). Confidence is the
 * weighted average × 100. Driver-neutral: works on {@link ElementSnapshot}s from any platform.
 */
public final class ConfidenceScorer {

    private static final List<Set<String>> ROLE_FAMILIES = List.of(
            Set.of("button", "link", "menuitem", "tab", "option", "treeitem"),
            Set.of("textbox", "searchbox", "combobox", "spinbutton"),
            Set.of("checkbox", "radio", "switch", "menuitemcheckbox", "menuitemradio"),
            Set.of("cell", "gridcell", "columnheader", "rowheader"));

    private final ScoringProfile profile;

    public ConfidenceScorer(ScoringProfile profile) {
        this.profile = profile;
    }

    public ScoringProfile profile() {
        return profile;
    }

    /** @param factor multiplier applied to the result (e.g. 0.95 when the reference has no baseline) */
    public ScoredCandidate score(ElementSnapshot ref, ElementSnapshot cand, double factor) {
        Map<Signal, Double> s = new EnumMap<>(Signal.class);
        put(s, Signal.IDENTITY, identity(ref, cand));
        put(s, Signal.ACCESSIBLE_NAME, bestOf(names(ref), names(cand), 1.0));
        put(s, Signal.ROLE, role(ref, cand));
        put(s, Signal.TEXT, present(ref.text) ? StringSimilarity.similarity(ref.text, cand.text) : null);
        put(s, Signal.HINTS, hints(ref, cand));
        put(s, Signal.STRUCTURE, structure(ref, cand));
        put(s, Signal.GEOMETRY, geometry(ref, cand));

        double num = 0, den = 0;
        boolean evidence = false;
        for (Map.Entry<Signal, Double> e : s.entrySet()) {
            double w = profile.weight(e.getKey());
            num += w * e.getValue();
            den += w;
            if (e.getKey().content && e.getValue() >= profile.contentEvidenceMin) evidence = true;
        }
        double confidence = den == 0 ? 0 : 100.0 * num / den * factor;
        return new ScoredCandidate(cand, round1(confidence), s, evidence);
    }

    private static void put(Map<Signal, Double> m, Signal k, Double v) {
        if (v != null) m.put(k, Math.max(0, Math.min(1, v)));
    }

    /**
     * id / name / test id may swap with each other in a refactor ({@code #companyName} becoming
     * {@code [name=companyName]}), so cross-attribute matches count at 0.95; link targets only
     * compare with link targets.
     */
    private static Double identity(ElementSnapshot ref, ElementSnapshot cand) {
        String[] r = {ref.id, ref.name, ref.testId};
        String[] c = {cand.id, cand.name, cand.testId};
        boolean refHasId = present(r[0]) || present(r[1]) || present(r[2]);
        if (!refHasId && !present(ref.href)) return null;
        double best = 0;
        for (int i = 0; i < r.length; i++) {
            if (!present(r[i])) continue;
            for (int j = 0; j < c.length; j++) {
                if (!present(c[j])) continue;
                best = Math.max(best, StringSimilarity.similarity(r[i], c[j]) * (i == j ? 1.0 : 0.95));
            }
        }
        if (present(ref.href) && present(cand.href)) best = Math.max(best, StringSimilarity.similarity(ref.href, cand.href));
        return best;
    }

    private static String[] names(ElementSnapshot e) {
        return new String[]{e.accessibleName, e.ariaLabel, e.labelText};
    }

    private static Double hints(ElementSnapshot ref, ElementSnapshot cand) {
        String[] r = {ref.placeholder, ref.title, ref.alt};
        String[] c = {cand.placeholder, cand.title, cand.alt};
        Double best = null;
        for (int i = 0; i < r.length; i++) {
            if (!present(r[i])) continue;
            double v = present(c[i]) ? StringSimilarity.similarity(r[i], c[i]) : 0;
            best = best == null ? v : Math.max(best, v);
        }
        return best;
    }

    /** Best pairwise similarity between any present reference value and any present candidate value. */
    private static Double bestOf(String[] ref, String[] cand, double crossFactor) {
        boolean any = false;
        double best = 0;
        for (String r : ref) {
            if (!present(r)) continue;
            any = true;
            for (String c : cand) {
                if (present(c)) best = Math.max(best, StringSimilarity.similarity(r, c) * crossFactor);
            }
        }
        return any ? best : null;
    }

    private static Double role(ElementSnapshot ref, ElementSnapshot cand) {
        if (present(ref.role)) {
            if (ref.role.equals(cand.role)) return 1.0;
            if (sameFamily(ref.role, cand.role)) return 0.6;
            if (present(ref.tag) && ref.tag.equals(cand.tag)) return 0.4;
            return 0.0;
        }
        if (present(ref.tag)) {
            if (!ref.tag.equals(cand.tag)) return 0.0;
            return !present(ref.type) || ref.type.equals(cand.type) ? 1.0 : 0.6;
        }
        return null;
    }

    private static boolean sameFamily(String a, String b) {
        if (!present(a) || !present(b)) return false;
        for (Set<String> f : ROLE_FAMILIES) if (f.contains(a) && f.contains(b)) return true;
        return false;
    }

    private static Double structure(ElementSnapshot ref, ElementSnapshot cand) {
        if (ref.ancestry == null || cand.ancestry == null) return null;
        int lcs = lcs(ref.ancestry, cand.ancestry);
        int max = Math.max(ref.ancestry.size(), cand.ancestry.size());
        double path = max == 0 ? 1 : (double) lcs / max;
        double heading;
        if (!present(ref.heading) && !present(cand.heading)) heading = 1;
        else if (!present(ref.heading) || !present(cand.heading)) heading = 0.5;
        else heading = StringSimilarity.similarity(ref.heading, cand.heading);
        double dialog = ref.inDialog == null || ref.inDialog.equals(cand.inDialog) ? 1 : 0;
        return 0.5 * path + 0.3 * heading + 0.2 * dialog;
    }

    private Double geometry(ElementSnapshot ref, ElementSnapshot cand) {
        if (ref.x == null || ref.y == null || cand.x == null || cand.y == null) return null;
        double rx = ref.x + nz(ref.w) / 2, ry = ref.y + nz(ref.h) / 2;
        double cx = cand.x + nz(cand.w) / 2, cy = cand.y + nz(cand.h) / 2;
        double d = Math.hypot(rx - cx, ry - cy);
        return Math.max(0, 1 - d / profile.geometryFalloffPx);
    }

    private static int lcs(List<String> a, List<String> b) {
        int[][] dp = new int[a.size() + 1][b.size() + 1];
        for (int i = 1; i <= a.size(); i++)
            for (int j = 1; j <= b.size(); j++)
                dp[i][j] = a.get(i - 1).equals(b.get(j - 1)) ? dp[i - 1][j - 1] + 1 : Math.max(dp[i - 1][j], dp[i][j - 1]);
        return dp[a.size()][b.size()];
    }

    private static double nz(Double d) {
        return d == null ? 0 : d;
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
