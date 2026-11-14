package com.careconnect.referral;

import com.careconnect.access.AccessService;
import com.careconnect.audit.AuditService;
import com.careconnect.common.ApiErrors;
import com.careconnect.common.Db;
import com.careconnect.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/referrals")
public class ReferralController {

    public record Referral(String id, String patientId, String mrn, String familyName, String givenName, String service,
                           String reason, String urgency, String status, Instant createdAt, String triagedBy,
                           String triageReason, Instant appointmentAt) {}
    public record NewReferral(@NotBlank String patientId, @NotBlank @Size(max = 100) String service,
                              @NotBlank @Size(max = 1000) String reason) {}
    public record Triage(@NotBlank @Pattern(regexp = "URGENT|SEMI_URGENT|ROUTINE") String urgency,
                         @NotBlank @Size(min = 5, max = 500) String reason) {}
    public record Booking(@NotNull @Future Instant appointmentAt) {}

    private final JdbcClient jdbc;
    private final AccessService access;
    private final AuditService audit;
    private final CurrentUser user;

    public ReferralController(JdbcClient jdbc, AccessService access, AuditService audit, CurrentUser user) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.user = user;
    }

    /** Oldest first, so nothing waits unseen. The list itself is audited because it names patients. */
    @GetMapping
    @PreAuthorize("hasRole('TRIAGE')")
    public List<Referral> open() {
        audit.record(user.id(), "REFERRAL_LIST_READ", null, null, "ALLOWED", null);
        return jdbc.sql("""
                SELECT r.id, r.patient_id, p.mrn, p.family_name, p.given_name, r.service, r.reason, r.urgency,
                       r.status, r.created_at, r.triaged_by, r.triage_reason, r.appointment_at
                FROM referral_case r
                JOIN patient p ON p.id = r.patient_id
                JOIN consent_policy cp ON cp.patient_id = r.patient_id AND cp.purpose = 'CARE' AND cp.status = 'ACTIVE'
                WHERE r.status <> 'BOOKED'
                ORDER BY r.created_at OFFSET 0 ROWS FETCH NEXT 200 ROWS ONLY""")
                .query(Referral.class).list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLINICIAN','COORDINATOR')")
    public Map<String, String> create(@Valid @RequestBody NewReferral referral) {
        access.requirePatientAccess(referral.patientId(), "REFERRAL_CREATE");
        String id = Db.newId();
        jdbc.sql("""
                INSERT INTO referral_case (id, patient_id, service, reason, urgency, status, created_at, created_by)
                VALUES (?, ?, ?, ?, 'UNTRIAGED', 'NEW', ?, ?)""")
                .params(id, referral.patientId(), referral.service(), referral.reason(), Db.now(), user.id())
                .update();
        return Map.of("id", id);
    }

    /** Urgency is a clinical decision, so it records who made it and why. */
    @PostMapping("/{id}/triage")
    @PreAuthorize("hasRole('TRIAGE')")
    public void triage(@PathVariable String id, @Valid @RequestBody Triage triage) {
        String patientId = patientOf(id);
        access.requirePatientAccess(patientId, "REFERRAL_TRIAGE");
        int updated = jdbc.sql("""
                UPDATE referral_case SET urgency = ?, status = 'TRIAGED', triaged_by = ?, triage_reason = ?
                WHERE id = ? AND status IN ('NEW', 'TRIAGED')""")
                .params(triage.urgency(), user.id(), triage.reason(), id).update();
        if (updated == 0) {
            throw new ApiErrors.Conflict("A booked referral cannot be triaged again");
        }
    }

    @PostMapping("/{id}/book")
    @PreAuthorize("hasRole('TRIAGE')")
    public void book(@PathVariable String id, @Valid @RequestBody Booking booking) {
        String patientId = patientOf(id);
        access.requirePatientAccess(patientId, "REFERRAL_BOOK");
        int updated = jdbc.sql("UPDATE referral_case SET status = 'BOOKED', appointment_at = ? WHERE id = ? AND status = 'TRIAGED'")
                .params(Db.ts(booking.appointmentAt()), id).update();
        if (updated == 0) {
            throw new ApiErrors.Conflict("Only a triaged referral can be booked");
        }
    }

    private String patientOf(String id) {
        return jdbc.sql("SELECT patient_id FROM referral_case WHERE id = ?").param(id).query(String.class).optional()
                .orElseThrow(() -> new ApiErrors.NotFound("Referral not found"));
    }
}
