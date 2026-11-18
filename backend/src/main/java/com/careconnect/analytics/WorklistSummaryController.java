package com.careconnect.analytics;

import com.careconnect.common.Db;
import com.careconnect.security.CurrentUser;
import com.careconnect.security.Roles;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Headline measures for the coordination worklist. Task counts are limited to the person's own care-team
 * patients. Hospital-wide measures are counts only and identify no one.
 */
@RestController
@PreAuthorize(Roles.CARE_STAFF)
public class WorklistSummaryController {

    public static final double FOLLOW_UP_TARGET = 0.9;

    public record TeamLoad(String team, long tasks) {}
    public record Interface(String code, String name, Instant lastMessageAt, Long delayMinutes, String status) {}
    public record Summary(long openTasks, long overdueTasks, long referralsAwaitingTriage, Long oldestReferralDays,
                          long dischargesNext24h, long dischargesWithoutFollowUpPlan, Double followUpWithin7Days,
                          double followUpTarget, long followUpSample, List<TeamLoad> teamLoad, List<Interface> interfaces) {}

    private record Feed(String code, String name, int maxDelayMinutes, Instant lastMessageAt) {}

    private static final String MY_OPEN_TASKS = """
            FROM care_task t
            JOIN care_team_member c ON c.patient_id = t.patient_id AND c.user_id = ?
            JOIN consent_policy cp ON cp.patient_id = t.patient_id AND cp.purpose = 'CARE' AND cp.status = 'ACTIVE'
            WHERE t.status = 'OPEN'""";

    private final JdbcClient jdbc;
    private final CurrentUser user;

    public WorklistSummaryController(JdbcClient jdbc, CurrentUser user) {
        this.jdbc = jdbc;
        this.user = user;
    }

    @GetMapping("/api/v1/worklist/summary")
    public Summary summary() {
        String me = user.id();
        Timestamp now = Db.now();
        long open = jdbc.sql("SELECT COUNT(*) " + MY_OPEN_TASKS).param(me).query(Long.class).single();
        long overdue = jdbc.sql("SELECT COUNT(*) " + MY_OPEN_TASKS + " AND t.due_at < ?").params(me, now).query(Long.class).single();
        List<TeamLoad> load = jdbc.sql("SELECT COALESCE(t.team, 'No team') AS team, COUNT(*) AS tasks " + MY_OPEN_TASKS
                        + " GROUP BY COALESCE(t.team, 'No team') ORDER BY COUNT(*) DESC")
                .param(me).query(TeamLoad.class).list();

        long waiting = jdbc.sql("SELECT COUNT(*) FROM referral_case WHERE status IN ('NEW', 'INFO_REQUESTED')").query(Long.class).single();
        Long oldestDays = jdbc.sql("SELECT MIN(created_at) FROM referral_case WHERE status IN ('NEW', 'INFO_REQUESTED')")
                .query(Timestamp.class).optional()
                .map(t -> Duration.between(t.toInstant(), Instant.now()).toDays()).orElse(null);

        Timestamp in24h = Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS));
        long discharges = jdbc.sql("SELECT COUNT(*) FROM encounter WHERE status = 'ADMITTED' AND expected_discharge_at >= ? AND expected_discharge_at < ?")
                .params(now, in24h).query(Long.class).single();
        long withoutPlan = jdbc.sql("""
                SELECT COUNT(*) FROM encounter e
                WHERE e.status = 'ADMITTED' AND e.expected_discharge_at >= ? AND e.expected_discharge_at < ?
                AND NOT EXISTS (SELECT 1 FROM care_task t WHERE t.patient_id = e.patient_id AND t.task_type = 'GP_FOLLOW_UP' AND t.status = 'OPEN')""")
                .params(now, in24h).query(Long.class).single();

        // Follow-up tasks that fell due in the last 30 days, and how many were done by their due time.
        Timestamp since = Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS));
        long due = jdbc.sql("SELECT COUNT(*) FROM care_task WHERE task_type = 'GP_FOLLOW_UP' AND due_at >= ? AND due_at < ?")
                .params(since, now).query(Long.class).single();
        long onTime = jdbc.sql("""
                SELECT COUNT(*) FROM care_task WHERE task_type = 'GP_FOLLOW_UP' AND due_at >= ? AND due_at < ?
                AND completed_at IS NOT NULL AND completed_at <= due_at""")
                .params(since, now).query(Long.class).single();

        return new Summary(open, overdue, waiting, oldestDays, discharges, withoutPlan,
                due == 0 ? null : Math.round(onTime * 100.0 / due) / 100.0, FOLLOW_UP_TARGET, due, load, interfaces());
    }

    private List<Interface> interfaces() {
        Instant now = Instant.now();
        return jdbc.sql("SELECT code, name, max_delay_minutes, last_message_at FROM interface_feed ORDER BY code")
                .query(Feed.class).list().stream()
                .map(f -> {
                    if (f.lastMessageAt() == null) {
                        return new Interface(f.code(), f.name(), null, null, "NO_DATA");
                    }
                    long delay = Duration.between(f.lastMessageAt(), now).toMinutes();
                    return new Interface(f.code(), f.name(), f.lastMessageAt(), delay, delay > f.maxDelayMinutes() ? "DELAYED" : "LIVE");
                })
                .toList();
    }
}
