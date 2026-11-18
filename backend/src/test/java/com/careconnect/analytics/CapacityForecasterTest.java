package com.careconnect.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class CapacityForecasterTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 11, 18);

    private static SortedMap<LocalDate, Integer> history(int days, int base, double perDay, int weekendDrop) {
        SortedMap<LocalDate, Integer> h = new TreeMap<>();
        for (int back = days - 1; back >= 0; back--) {
            LocalDate d = AS_OF.minusDays(back);
            boolean weekend = d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
            h.put(d, (int) Math.round(base - perDay * back - (weekend ? weekendDrop : 0) + (back % 3) - 1));
        }
        return h;
    }

    @Test
    void needsTwoWeeksOfHistory() {
        assertThat(CapacityForecaster.forecast(history(10, 100, 0, 0), AS_OF, 7)).isEmpty();
    }

    @Test
    void forecastsSevenDaysWithAnOrderedRangeThatWidens() {
        List<CapacityForecaster.Point> points = CapacityForecaster.forecast(history(40, 400, 0, 0), AS_OF, 7);
        assertThat(points).hasSize(7);
        assertThat(points.getFirst().date()).isEqualTo(AS_OF.plusDays(1));
        for (CapacityForecaster.Point p : points) {
            assertThat(p.p10()).isLessThanOrEqualTo(p.p50());
            assertThat(p.p50()).isLessThanOrEqualTo(p.p90());
            assertThat(p.p50()).isBetween(390.0, 410.0);
        }
        double firstWidth = points.getFirst().p90() - points.getFirst().p10();
        double lastWidth = points.getLast().p90() - points.getLast().p10();
        assertThat(lastWidth).isGreaterThan(firstWidth);
    }

    @Test
    void followsTrendAndWeekendPattern() {
        List<CapacityForecaster.Point> points = CapacityForecaster.forecast(history(40, 400, 1, 12), AS_OF, 7);
        CapacityForecaster.Point saturday = points.stream().filter(p -> p.date().getDayOfWeek() == DayOfWeek.SATURDAY).findFirst().orElseThrow();
        CapacityForecaster.Point friday = points.stream().filter(p -> p.date().getDayOfWeek() == DayOfWeek.FRIDAY).findFirst().orElseThrow();
        assertThat(saturday.p50()).isLessThan(friday.p50() - 5);
        assertThat(points.getFirst().p50()).isGreaterThan(398.0);
    }
}
