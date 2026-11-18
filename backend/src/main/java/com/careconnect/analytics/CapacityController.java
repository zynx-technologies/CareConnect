package com.careconnect.analytics;

import com.careconnect.audit.AuditService;
import com.careconnect.common.ApiErrors;
import com.careconnect.common.Db;
import com.careconnect.common.SiteClock;
import com.careconnect.security.CurrentUser;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bed and staffing forecast for managers. Counts only: no patient is identified on this screen. */
@RestController
@RequestMapping("/api/v1/capacity")
@PreAuthorize("hasRole('MANAGER')")
public class CapacityController {

    static final double HIGH = 0.95;
    static final double WATCH = 0.92;

    public record Peak(LocalDate date, double p10, double p50, double p90) {}
    public record Summary(long occupied, long beds, Peak peak, long expectedDischargesToday, long awaitingTransport,
                          long nursingGapShifts, String nursingGapLabel) {}
    public record SeriesPoint(LocalDate date, Integer actual, Double p10, Double p50, Double p90) {}
    public record WardPressure(String code, String name, int beds, long occupied, double forecastShare, String status) {}
    public record Action(String id, String action, String effect, String owner, String status, String plannedBy, Instant plannedAt) {}
    public record CapacityView(Summary summary, List<SeriesPoint> series, List<WardPressure> wards, List<Action> actions,
                               String modelVersion, Instant generatedAt, CapacityService.Accuracy accuracy) {}

    private record Ward(String code, String name, int beds) {}
    private record Forecast(String wardCode, LocalDate forecastDate, BigDecimal p10, BigDecimal p50, BigDecimal p90,
                            String modelVersion, Instant generatedAt) {}

    private final JdbcClient jdbc;
    private final CapacityService capacity;
    private final SiteClock clock;
    private final AuditService audit;
    private final CurrentUser user;

    public CapacityController(JdbcClient jdbc, CapacityService capacity, SiteClock clock, AuditService audit, CurrentUser user) {
        this.jdbc = jdbc;
        this.capacity = capacity;
        this.clock = clock;
        this.audit = audit;
        this.user = user;
    }

    @GetMapping
    public CapacityView view() {
        LocalDate today = clock.today();
        List<Ward> wards = jdbc.sql("SELECT code, name, beds FROM ward ORDER BY display_order").query(Ward.class).list();
        Map<String, Long> occupied = new HashMap<>();
        jdbc.sql("SELECT ward_code, COUNT(*) FROM encounter WHERE status = 'ADMITTED' AND ward_code IS NOT NULL GROUP BY ward_code")
                .query((rs, i) -> occupied.put(rs.getString(1), rs.getLong(2))).list();

        LocalDate asOf = jdbc.sql("SELECT MAX(as_of) FROM capacity_forecast WHERE ward_code = ?")
                .param(CapacityService.ALL_WARDS).query(LocalDate.class).optional().orElse(null);
        List<Forecast> forecasts = asOf == null ? List.of() : jdbc.sql("""
                SELECT ward_code, forecast_date, p10, p50, p90, model_version, generated_at
                FROM capacity_forecast WHERE as_of = ? ORDER BY forecast_date""")
                .param(Date.valueOf(asOf)).query(Forecast.class).list();
        List<Forecast> hospital = forecasts.stream().filter(f -> CapacityService.ALL_WARDS.equals(f.wardCode())).toList();
        Forecast peak = hospital.stream().max(Comparator.comparing(Forecast::p50)).orElse(null);

        List<SeriesPoint> series = new ArrayList<>();
        SortedMap<LocalDate, Integer> history = capacity.hospitalHistory(today.minusDays(14), today);
        history.forEach((day, n) -> series.add(new SeriesPoint(day, n, null, null, null)));
        for (Forecast f : hospital) {
            if (f.forecastDate().isAfter(today)) {
                series.add(new SeriesPoint(f.forecastDate(), null, f.p10().doubleValue(), f.p50().doubleValue(), f.p90().doubleValue()));
            }
        }

        List<WardPressure> pressure = new ArrayList<>();
        for (Ward w : wards) {
            double expected = forecasts.stream()
                    .filter(f -> w.code().equals(f.wardCode()) && peak != null && f.forecastDate().equals(peak.forecastDate()))
                    .mapToDouble(f -> f.p50().doubleValue()).findFirst()
                    .orElse(occupied.getOrDefault(w.code(), 0L));
            double share = w.beds() == 0 ? 0 : Math.round(expected / w.beds() * 100) / 100.0;
            pressure.add(new WardPressure(w.code(), w.name(), w.beds(), occupied.getOrDefault(w.code(), 0L), share,
                    share >= HIGH ? "HIGH" : share >= WATCH ? "WATCH" : "OK"));
        }
        pressure.sort(Comparator.comparingDouble(WardPressure::forecastShare).reversed());

        long dischargesToday = jdbc.sql("""
                SELECT COUNT(*) FROM encounter WHERE status = 'ADMITTED' AND expected_discharge_at >= ? AND expected_discharge_at < ?""")
                .params(clock.startOf(today), clock.startOf(today.plusDays(1))).query(Long.class).single();
        long transport = jdbc.sql("""
                SELECT COUNT(*) FROM encounter WHERE status = 'ADMITTED' AND awaiting_transport = 1
                AND expected_discharge_at >= ? AND expected_discharge_at < ?""")
                .params(clock.startOf(today), clock.startOf(today.plusDays(1))).query(Long.class).single();

        record Gap(String wardName, String shift, long shifts) {}
        List<Gap> gaps = jdbc.sql("""
                SELECT w.name AS ward_name, g.shift, SUM(g.gap_shifts) AS shifts
                FROM staffing_gap g JOIN ward w ON w.code = g.ward_code
                WHERE g.shift_date >= ? AND g.shift_date < ?
                GROUP BY w.name, g.shift ORDER BY SUM(g.gap_shifts) DESC""")
                .params(Date.valueOf(today), Date.valueOf(today.plusDays(3))).query(Gap.class).list();
        long gapTotal = gaps.stream().mapToLong(Gap::shifts).sum();
        String gapLabel = gaps.isEmpty() ? "No gaps recorded" : gaps.getFirst().wardName() + ", " + shiftName(gaps.getFirst().shift());

        List<Action> actions = jdbc.sql("""
                SELECT id, action, effect, owner, status, planned_by, planned_at FROM capacity_action ORDER BY created_at, action""")
                .query(Action.class).list();

        long beds = wards.stream().mapToLong(Ward::beds).sum();
        long occupiedTotal = occupied.values().stream().mapToLong(Long::longValue).sum();
        Summary summary = new Summary(occupiedTotal, beds,
                peak == null ? null : new Peak(peak.forecastDate(), peak.p10().doubleValue(), peak.p50().doubleValue(), peak.p90().doubleValue()),
                dischargesToday, transport, gapTotal, gapLabel);
        return new CapacityView(summary, series, pressure, actions,
                hospital.isEmpty() ? null : "cc-capacity " + hospital.getFirst().modelVersion(),
                hospital.isEmpty() ? null : hospital.getFirst().generatedAt(), capacity.accuracy());
    }

    /** Records that the owner has accepted a suggested action. Planning changes nothing clinical. */
    @PostMapping("/actions/{id}/plan")
    public void plan(@PathVariable String id) {
        int updated = jdbc.sql("UPDATE capacity_action SET status = 'PLANNED', planned_by = ?, planned_at = ? WHERE id = ? AND status = 'OPEN'")
                .params(user.id(), Db.now(), id).update();
        if (updated == 0) {
            throw new ApiErrors.Conflict("This action is already planned, or does not exist");
        }
        audit.record(user.id(), "CAPACITY_ACTION_PLANNED", null, id, "ALLOWED", null);
    }

    private static String shiftName(String shift) {
        return switch (shift) {
            case "NIGHT" -> "nights";
            case "EVENING" -> "evenings";
            default -> "days";
        };
    }
}
