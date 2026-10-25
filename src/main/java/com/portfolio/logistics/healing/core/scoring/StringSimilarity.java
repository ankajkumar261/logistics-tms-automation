package com.portfolio.logistics.healing.core.scoring;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Similarity of two identifiers or labels, 0..1. Formatting differences don't count —
 * {@code companyName}, {@code company-name}, {@code Company Name*} and {@code company_names} all score 1.0 —
 * but a different word does: {@code phone} vs {@code phone2} ≈ 0.70, {@code Add Customer} vs
 * {@code New Customer} ≈ 0.57. That is what keeps "a field was renamed" healable while "a
 * different field" is not.
 */
public final class StringSimilarity {

    private StringSimilarity() { }

    public static double similarity(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) return 0;
        if (na.equals(nb)) return 1;
        String ca = na.replace(" ", "");
        String cb = nb.replace(" ", "");
        if (ca.equals(cb)) return 1;
        double lev = 1.0 - (double) levenshtein(ca, cb) / Math.max(ca.length(), cb.length());
        return 0.6 * lev + 0.4 * jaccard(tokens(na), tokens(nb));
    }

    /** Lower-case words: camelCase and digits split, punctuation dropped, simple plural "s" removed. */
    public static String normalize(String s) {
        if (s == null) return "";
        String spaced = s
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("([A-Za-z])([0-9])", "$1 $2")
                .replaceAll("([0-9])([A-Za-z])", "$1 $2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (spaced.isEmpty()) return "";
        return Arrays.stream(spaced.split(" "))
                .map(t -> t.length() > 3 && t.endsWith("s") && !t.endsWith("ss") ? t.substring(0, t.length() - 1) : t)
                .collect(Collectors.joining(" "));
    }

    private static Set<String> tokens(String normalized) {
        return new HashSet<>(Arrays.asList(normalized.split(" ")));
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0 : (double) inter.size() / union.size();
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }
}
