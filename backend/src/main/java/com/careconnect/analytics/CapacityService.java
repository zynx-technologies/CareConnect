package com.careconnect.analytics;

import com.careconnect.common.Db;
import com.careconnect.common.SiteClock;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps today's bed census and the 7-day forecast up to date. Runs at startup and every hour. Every replica
 * may run it; the work is idempotent and a lost race is ignored.
 */
@Service
public class CapacityService {

    public static final String ALL_WARDS = "ALL";
    static final int HORIZON_DAYS = 7;
    private static final Logger log = LoggerFactory.getLogger(CapacityService.class);

    public record Accuracy(double meanAbsoluteError, double coverage, int samples) {}

    private record ForecastRow(LocalDate forecastDate, BigDecimal p10, BigDecimal p50, BigDecimal p90) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final SiteClock clock;

    public CapacityService(JdbcClient jdbc, TransactionTemplate tx, SiteClock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 5 * * * *")
    public void refresh() {
        try {
            LocalDate today = clock.today();
            recordCensus(today);
            forecast(today);
        } catch (DataAccessException e) {
            log.warn("Capacity refresh skipped: {}", e.getClass().getSimpleName());
        }
    }

    /** Counts admitted patients on each ward now, and stores it as today's census. */
    public void recordCensus(LocalDate day) {
        List<String> wards = jdbc.sql("SELECT code FROM ward").query(String.class).list();
        if (wards.isEmpty()) {
            return;
        }
        Map<String, Long> counts = new HashMap<>();
        jdbc.sql("SELECT ward_code, COUNT(*) AS n FROM encounter WHERE status = 'ADMITTED' AND ward_code IS NOT NULL GROUP BY ward_code")
                .query((rs, i) -> counts.put(rs.getString(1), rs.getLong(2))).list();
        tx.executeWithoutResult(s -> {
            jdbc.sql("DELETE FROM bed_occupancy WHERE census_date = ?").param(Date.valueOf(day)).update();
            for (String ward : wards) {
                jdbc.sql("INSERT INTO bed_occupancy (ward_code, census_date, occupied) VALUES (?, ?, ?)")
                        .params(ward, Date.valueOf(day), counts.getOrDefault(ward, 0L)).update();
            }
        });
    }

    /** Forecasts each ward and the hospital total from the census up to and including asOf. */
    public void forecast(LocalDate asOf) {
        Map<String, SortedMap<LocalDate, Integer>> byWard = new HashMap<>();
        jdbc.sql("SELECT ward_code, census_date, occupied FROM bed_occupancy WHERE census_date BETWEEN ? AND ?")
                .params(Date.valueOf(asOf.minusDays(40)), Date.valueOf(asOf))
                .query((rs, i) -> byWard.computeIfAbsent(rs.getString(1), k -> new TreeMap<>())
                        .put(rs.getDate(2).toLocalDate(), rs.getInt(3)))
                .list();
        if (byWard.isEmpty()) {
            return;
        }
        byWard.put(ALL_WARDS, totals(byWard));
        tx.executeWithoutResult(s -> {
            jdbc.sql("DELETE FROM capacity_forecast WHERE as_of = ?").param(Date.valueOf(asOf)).update();
            byWard.forEach((ward, history) -> {
                for (CapacityForecaster.Point p : CapacityForecaster.forecast(history, asOf, HORIZON_DAYS)) {
                    jdbc.sql("""
                            INSERT INTO capacity_forecast (ward_code, as_of, forecast_date, p10, p50, p90, model_version, generated_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
                            .params(ward, Date.valueOf(asOf), Date.valueOf(p.date()), p.p10(), p.p50(), p.p90(),
                                    CapacityForecaster.VERSION, Db.now())
                            .update();
                }
            });
        });
    }

    /** Hospital totals for days where every ward has a census. */
    public SortedMap<LocalDate, Integer> hospitalHistory(LocalDate from, LocalDate to) {
        Map<String, SortedMap<LocalDate, Integer>> byWard = new HashMap<>();
        jdbc.sql("SELECT ward_code, census_date, occupied FROM bed_occupancy WHERE census_date BETWEEN ? AND ?")
                .params(Date.valueOf(from), Date.valueOf(to))
                .query((rs, i) -> byWard.computeIfAbsent(rs.getString(1), k -> new TreeMap<>())
                        .put(rs.getDate(2).toLocalDate(), rs.getInt(3)))
                .list();
        return totals(byWard);
    }

    /**
     * How the hospital forecast did over the last 14 completed days, across all horizons: mean absolute error in
     * beds, and the share of actual values inside the 80% range (it should be close to 0.8).
     */
    public Accuracy accuracy() {
        LocalDate today = clock.today();
        SortedMap<LocalDate, Integer> actual = hospitalHistory(today.minusDays(14), today.minusDays(1));
        List<ForecastRow> rows = new ArrayList<>(jdbc.sql("""
                SELECT forecast_date, p10, p50, p90 FROM capacity_forecast
                WHERE ward_code = ? AND forecast_date >= ? AND forecast_date < ? AND as_of < forecast_date""")
                .params(ALL_WARDS, Date.valueOf(today.minusDays(14)), Date.valueOf(today))
                .query(ForecastRow.class).list());
        double error = 0;
        int inside = 0;
        int n = 0;
        for (ForecastRow r : rows) {
            Integer y = actual.get(r.forecastDate());
            if (y == null) {
                continue;
            }
            error += Math.abs(y - r.p50().doubleValue());
            if (y >= r.p10().doubleValue() && y <= r.p90().doubleValue()) {
                inside++;
            }
            n++;
        }
        return n == 0 ? new Accuracy(0, 0, 0)
                : new Accuracy(Math.round(error / n * 10) / 10.0, Math.round(inside * 100.0 / n) / 100.0, n);
    }

    private static SortedMap<LocalDate, Integer> totals(Map<String, SortedMap<LocalDate, Integer>> byWard) {
        SortedMap<LocalDate, Integer> total = new TreeMap<>();
        Map<LocalDate, Integer> wardsSeen = new HashMap<>();
        byWard.forEach((ward, history) -> {
            if (ALL_WARDS.equals(ward)) {
                return;
            }
            history.forEach((day, occupied) -> {
                total.merge(day, occupied, Integer::sum);
                wardsSeen.merge(day, 1, Integer::sum);
            });
        });
        long wardCount = byWard.keySet().stream().filter(w -> !ALL_WARDS.equals(w)).count();
        total.keySet().removeIf(day -> wardsSeen.get(day) < wardCount);
        return total;
    }
}
