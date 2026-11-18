package com.careconnect.audit;

import com.careconnect.common.ApiErrors;
import com.careconnect.common.Db;
import com.careconnect.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Privacy officer screens: emergency-access review, access patterns, and the audit trail. Every read is audited. */
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasRole('PRIVACY')")
public class AuditController {

    /** A person with at least this many denied reads in 7 days is flagged for review. */
    static final int UNUSUAL_DENIALS = 3;

    public record EmergencyAccess(String id, String patientId, String mrn, String familyName, String givenName,
                                  String userId, String reason, Instant grantedAt, Instant expiresAt, Instant reviewedAt,
                                  String reviewedBy, String reviewOutcome, String reviewNote) {}
    public record Review(@NotBlank @Pattern(regexp = "APPROPRIATE|INAPPROPRIATE") String outcome,
                         @NotBlank @Size(min = 5, max = 500) String note) {}
    public record UserDenials(String userId, long denials) {}
    public record Summary(long recordReads, long denials, long emergencyPending, long emergencyLast7Days,
                          List<UserDenials> unusualAccess) {}

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final CurrentUser user;

    public AuditController(JdbcClient jdbc, AuditService audit, CurrentUser user) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.user = user;
    }

    @GetMapping("/summary")
    public Summary summary() {
        Timestamp since = Timestamp.from(Instant.now().minus(7, ChronoUnit.DAYS));
        long reads = jdbc.sql("SELECT COUNT(*) FROM audit_event WHERE action = 'PATIENT_RECORD_READ' AND outcome = 'ALLOWED' AND occurred_at >= ?")
                .param(since).query(Long.class).single();
        long denials = jdbc.sql("SELECT COUNT(*) FROM audit_event WHERE outcome = 'DENIED' AND occurred_at >= ?")
                .param(since).query(Long.class).single();
        long pending = jdbc.sql("SELECT COUNT(*) FROM emergency_access WHERE reviewed_at IS NULL").query(Long.class).single();
        long recent = jdbc.sql("SELECT COUNT(*) FROM emergency_access WHERE granted_at >= ?").param(since).query(Long.class).single();
        List<UserDenials> unusual = jdbc.sql("""
                SELECT user_id, COUNT(*) AS denials FROM audit_event WHERE outcome = 'DENIED' AND occurred_at >= ?
                GROUP BY user_id HAVING COUNT(*) >= ? ORDER BY COUNT(*) DESC""")
                .params(since, UNUSUAL_DENIALS).query(UserDenials.class).list();
        audit.record(user.id(), "AUDIT_SUMMARY_READ", null, null, "ALLOWED", null);
        return new Summary(reads, denials, pending, recent, unusual);
    }

    @GetMapping("/emergency-access")
    public List<EmergencyAccess> emergencyAccess() {
        audit.record(user.id(), "EMERGENCY_ACCESS_LIST_READ", null, null, "ALLOWED", null);
        return jdbc.sql("""
                SELECT e.id, e.patient_id, p.mrn, p.family_name, p.given_name, e.user_id, e.reason, e.granted_at,
                       e.expires_at, e.reviewed_at, e.reviewed_by, e.review_outcome, e.review_note
                FROM emergency_access e JOIN patient p ON p.id = e.patient_id
                ORDER BY CASE WHEN e.reviewed_at IS NULL THEN 0 ELSE 1 END, e.granted_at DESC
                OFFSET 0 ROWS FETCH NEXT 200 ROWS ONLY""")
                .query(EmergencyAccess.class).list();
    }

    /** Records the privacy officer's decision. An inappropriate use is then handled through the breach runbook. */
    @PostMapping("/emergency-access/{id}/review")
    public void review(@PathVariable String id, @Valid @RequestBody Review review) {
        String patientId = jdbc.sql("SELECT patient_id FROM emergency_access WHERE id = ?").param(id)
                .query(String.class).optional().orElseThrow(() -> new ApiErrors.NotFound("Emergency access not found"));
        int updated = jdbc.sql("""
                UPDATE emergency_access SET reviewed_at = ?, reviewed_by = ?, review_outcome = ?, review_note = ?
                WHERE id = ? AND reviewed_at IS NULL""")
                .params(Db.now(), user.id(), review.outcome(), review.note().strip(), id).update();
        if (updated == 0) {
            throw new ApiErrors.Conflict("This emergency access has already been reviewed");
        }
        audit.record(user.id(), "EMERGENCY_ACCESS_REVIEWED", patientId, id, "ALLOWED", review.outcome());
    }

    /** Recent events, newest first, filtered by person or by patient. */
    @GetMapping("/events")
    public List<AuditService.AuditEvent> events(@RequestParam(required = false) String userId,
                                                @RequestParam(required = false) String patientId) {
        audit.record(user.id(), "AUDIT_READ", patientId, null, "ALLOWED", userId == null ? null : "filtered by person");
        String person = blankToNull(userId);
        String patient = blankToNull(patientId);
        StringBuilder sql = new StringBuilder("SELECT id, occurred_at, user_id, action, patient_id, resource_id, outcome, detail FROM audit_event WHERE 1 = 1");
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (person != null) { sql.append(" AND user_id = ?"); params.add(person); }
        if (patient != null) { sql.append(" AND patient_id = ?"); params.add(patient); }
        sql.append(" ORDER BY occurred_at DESC OFFSET 0 ROWS FETCH NEXT 200 ROWS ONLY");
        return jdbc.sql(sql.toString()).params(params).query(AuditService.AuditEvent.class).list();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
