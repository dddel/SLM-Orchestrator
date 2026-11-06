package com.tirocinioaltrama.slmorchestrator.eval;

import java.util.Locale;

/**
 * Calcolo dell'intervallo di confidenza di Wilson al 95% per proporzioni binomiali.
 * Replicazione fedele di {@code eval/java_harness/wilson.py}.
 */
public final class WilsonScore {

    private static final double Z_95 = 1.959964;

    private WilsonScore() {}

    public record Interval(double lower, double upper) {}

    /**
     * Calcola l'intervallo di confidenza di Wilson al 95%.
     * Restituisce (lower, upper) in percentuale [0..100] arrotondata a 1 decimale.
     */
    public static Interval interval(int k, int n) {
        if (n == 0) {
            return new Interval(0.0, 0.0);
        }

        double p = (double) k / n;
        double z2 = Z_95 * Z_95;
        double denom = 1.0 + z2 / n;
        double center = (p + z2 / (2.0 * n)) / denom;
        double spread = (Z_95 / denom) * Math.sqrt((p * (1.0 - p) / n) + (z2 / (4.0 * n * n)));

        double lower = Math.max(0.0, center - spread) * 100.0;
        double upper = Math.min(1.0, center + spread) * 100.0;

        return new Interval(round1(lower), round1(upper));
    }

    /**
     * Formatta una proporzione binomiale con percentuale e intervallo Wilson 95%.
     * Es: "51/60 (85.0%, IC 95%: 73.9–91.9%)"
     */
    public static String format(int k, int n) {
        if (n == 0) {
            return "N/A";
        }
        double pct = ((double) k / n) * 100.0;
        if (n < 10) {
            return String.format(Locale.ROOT, "%d/%d (n < 10, percentuale non riportata)", k, n);
        }
        Interval iv = interval(k, n);
        return String.format(Locale.ROOT, "%d/%d (%.1f%%, IC 95%%: %.1f–%.1f%%)", k, n, pct, iv.lower(), iv.upper());
    }

    private static double round1(double val) {
        return Math.round(val * 10.0) / 10.0;
    }
}
