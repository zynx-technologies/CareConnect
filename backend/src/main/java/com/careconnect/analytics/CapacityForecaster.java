package com.careconnect.analytics;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Baseline bed-demand forecast: the last week's level, a weekly trend, and a day-of-week pattern, with an 80%
 * range taken from the model's own recent errors and widened with the horizon. It is deliberately simple and
 * explainable. The design's LightGBM model replaces it in Phase 4 once it passes the same accuracy checks.
 */
public final class CapacityForecaster {

    public static final String VERSION = "baseline-1";
    static final int MIN_HISTORY_DAYS = 14;

    public record Point(LocalDate date, double p10, double p50, double p90) {}

    private CapacityForecaster() {}

    /** history holds daily occupied beds up to and including asOf. Returns nothing if history is too short. */
    public static List<Point> forecast(SortedMap<LocalDate, Integer> history, LocalDate asOf, int horizon) {
        List<Point> points = new ArrayList<>();
        if (!hasDays(history, asOf, MIN_HISTORY_DAYS)) {
            return points;
        }
        double level = mean(history, asOf.minusDays(6), asOf);
        double previous = mean(history, asOf.minusDays(13), asOf.minusDays(7));
        double slope = (level - previous) / 7.0;
        Map<DayOfWeek, Double> effect = weekdayEffect(history, asOf);

        List<Double> residuals = new ArrayList<>();
        for (LocalDate d = asOf.minusDays(20); !d.isAfter(asOf); d = d.plusDays(1)) {
            if (history.containsKey(d) && hasDays(history, d.minusDays(1), 7)) {
                double predicted = mean(history, d.minusDays(7), d.minusDays(1)) + effect.getOrDefault(d.getDayOfWeek(), 0.0);
                residuals.add(history.get(d) - predicted);
            }
        }
        residuals.sort(Double::compare);
        double low = Math.min(quantile(residuals, 0.10), -1.0);
        double high = Math.max(quantile(residuals, 0.90), 1.0);

        for (int k = 1; k <= horizon; k++) {
            LocalDate date = asOf.plusDays(k);
            // The level is the mean of a window centred three days before asOf.
            double p50 = level + slope * (k + 3) + effect.getOrDefault(date.getDayOfWeek(), 0.0);
            double widen = Math.sqrt(k);
            points.add(new Point(date, round(Math.max(0, p50 + low * widen)), round(Math.max(0, p50)),
                    round(Math.max(0, p50 + high * widen))));
        }
        return points;
    }

    private static Map<DayOfWeek, Double> weekdayEffect(SortedMap<LocalDate, Integer> history, LocalDate asOf) {
        // Each day is compared with the 7-day average centred on it, so a rising or falling trend does not
        // leak into the weekday pattern.
        Map<DayOfWeek, double[]> sums = new EnumMap<>(DayOfWeek.class);
        for (LocalDate d = asOf.minusDays(30); !d.isAfter(asOf.minusDays(3)); d = d.plusDays(1)) {
            Integer y = history.get(d);
            if (y != null && hasDays(history, d.plusDays(3), 7)) {
                double[] s = sums.computeIfAbsent(d.getDayOfWeek(), k -> new double[2]);
                s[0] += y - mean(history, d.minusDays(3), d.plusDays(3));
                s[1]++;
            }
        }
        Map<DayOfWeek, Double> effect = new EnumMap<>(DayOfWeek.class);
        sums.forEach((day, s) -> effect.put(day, s[0] / s[1]));
        return effect;
    }

    private static boolean hasDays(SortedMap<LocalDate, Integer> history, LocalDate end, int days) {
        for (int i = 0; i < days; i++) {
            if (!history.containsKey(end.minusDays(i))) {
                return false;
            }
        }
        return true;
    }

    private static double mean(SortedMap<LocalDate, Integer> history, LocalDate from, LocalDate to) {
        double sum = 0;
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Integer y = history.get(d);
            if (y != null) {
                sum += y;
                n++;
            }
        }
        return n == 0 ? 0 : sum / n;
    }

    private static double quantile(List<Double> sorted, double q) {
        if (sorted.isEmpty()) {
            return 0;
        }
        return sorted.get((int) Math.min(sorted.size() - 1, Math.floor(q * sorted.size())));
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
