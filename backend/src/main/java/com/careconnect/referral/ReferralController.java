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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
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
                           String source, String reason, String urgency, String suggestedUrgency, String status,
                           Instant createdAt, String triagedBy, String triageReason, Instant appointmentAt,
                           String bookedService, String bookingStatus, String extractedSummary, String extractedSource) {}
    public record Suggestion(String id, String name, Instant nextSlotAt, int distanceKm, boolean rapidAccess) {}
    public record NewReferral(@NotBlank String patientId, @NotBlank @Size(max = 100) String service,
                              @NotBlank @Size(max = 1000) String reason) {}
    public record Triage(@NotBlank @Pattern(regexp = "URGENT|SEMI_URGENT|ROUTINE") String urgency,
                         @NotBlank @Size(min = 5, max = 500) String reason) {}
    public record Booking(@NotNull @Future Instant appointmentAt) {}
    public record AcceptAndBook(@NotBlank String serviceId,
                                @NotBlank @Pattern(regexp = "URGENT|SEMI_URGENT|ROUTINE") String urgency,
                                @NotBlank @Size(min = 5, max = 500) String reason) {}
    public record InfoRequest(@NotBlank @Size(min = 5, max = 450) String question) {}

    private record ServiceRow(String id, String name, Instant nextSlotAt, int distanceKm, int rapidAccess) {}

    private final JdbcClient jdbc;
    private final AccessService access;
    private final AuditService audit;
    private final CurrentUser user;
    private final TransactionTemplate tx;

    public ReferralController(JdbcClient jdbc, AccessService access, AuditService audit, CurrentUser user, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.user = user;
        this.tx = tx;
    }

    /**
     * Open referrals oldest first, so nothing waits unseen, plus bookings from the last 30 days and upcoming.
     * The list itself is audited because it names patients.
     */
    @GetMapping
    @PreAuthorize("hasRole('TRIAGE')")
    public List<Referral> open() {
        audit.record(user.id(), "REFERRAL_LIST_READ", null, null, "ALLOWED", null);
        return jdbc.sql("""
                SELECT r.id, r.patient_id, p.mrn, p.family_name, p.given_name, r.service, r.source, r.reason, r.urgency,
                       r.suggested_urgency, r.status, r.created_at, r.triaged_by, r.triage_reason, r.appointment_at,
                       r.booked_service, r.booking_status, r.extracted_summary, r.extracted_source
                FROM referral_case r
                JOIN patient p ON p.id = r.patient_id
                JOIN consent_policy cp ON cp.patient_id = r.patient_id AND cp.purpose = 'CARE' AND cp.status = 'ACTIVE'
                WHERE r.status <> 'BOOKED' OR r.appointment_at >= ?
                ORDER BY r.created_at OFFSET 0 ROWS FETCH NEXT 300 ROWS ONLY""")
                .param(Timestamp.from(Instant.now().minus(30, ChronoUnit.DAYS)))
                .query(Referral.class).list();
    }

    /**
     * Services that accept this specialty, from eligibility rules and published wait times. Urgent referrals list
     * rapid-access services first. These are suggestions: the triage clinician decides.
     */
    @GetMapping("/{id}/suggestions")
    @PreAuthorize("hasRole('TRIAGE')")
    public List<Suggestion> suggestions(@PathVariable String id) {
        Map<String, Object> referral = jdbc.sql("SELECT patient_id, service, urgency, suggested_urgency FROM referral_case WHERE id = ?")
                .param(id).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ApiErrors.NotFound("Referral not found"));
        access.requirePatientAccess((String) referral.get("patient_id"), "REFERRAL_SUGGESTIONS");
        String urgency = "UNTRIAGED".equals(referral.get("urgency")) ? (String) referral.get("suggested_urgency") : (String) referral.get("urgency");
        boolean urgent = "URGENT".equals(urgency);
        return jdbc.sql("SELECT id, name, next_slot_at, distance_km, rapid_access FROM care_service WHERE specialty = ?")
                .param(referral.get("service")).query(ServiceRow.class).list().stream()
                .sorted((a, b) -> {
                    if (urgent && a.rapidAccess() != b.rapidAccess()) return b.rapidAccess() - a.rapidAccess();
                    if (a.nextSlotAt() == null) return 1;
                    if (b.nextSlotAt() == null) return -1;
                    return a.nextSlotAt().compareTo(b.nextSlotAt());
                })
                .map(s -> new Suggestion(s.id(), s.name(), s.nextSlotAt(), s.distanceKm(), s.rapidAccess() == 1))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLINICIAN','COORDINATOR')")
    public Map<String, String> create(@Valid @RequestBody NewReferral referral) {
        access.requirePatientAccess(referral.patientId(), "REFERRAL_CREATE");
        String id = Db.newId();
        jdbc.sql("""
                INSERT INTO referral_case (id, patient_id, service, reason, urgency, status, created_at, created_by, source)
                VALUES (?, ?, ?, ?, 'UNTRIAGED', 'NEW', ?, ?, 'CareConnect')""")
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
                WHERE id = ? AND status IN ('NEW', 'TRIAGED', 'INFO_REQUESTED')""")
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
        int updated = jdbc.sql("""
                UPDATE referral_case SET status = 'BOOKED', appointment_at = ?, booking_status = 'CONFIRMED', booked_service = service
                WHERE id = ? AND status = 'TRIAGED'""")
                .params(Db.ts(booking.appointmentAt()), id).update();
        if (updated == 0) {
            throw new ApiErrors.Conflict("Only a triaged referral can be booked");
        }
    }

    /** Sets the clinician's urgency and books the chosen service's next slot in one step. */
    @PostMapping("/{id}/accept-and-book")
    @PreAuthorize("hasRole('TRIAGE')")
    public void acceptAndBook(@PathVariable String id, @Valid @RequestBody AcceptAndBook request) {
        String patientId = patientOf(id);
        access.requirePatientAccess(patientId, "REFERRAL_BOOK");
        ServiceRow service = jdbc.sql("SELECT id, name, next_slot_at, distance_km, rapid_access FROM care_service WHERE id = ?")
                .param(request.serviceId()).query(ServiceRow.class).optional()
                .orElseThrow(() -> new ApiErrors.NotFound("Service not found"));
        if (service.nextSlotAt() == null || !service.nextSlotAt().isAfter(Instant.now())) {
            throw new ApiErrors.Conflict("This service has no future slot. Book a time by hand.");
        }
        tx.executeWithoutResult(s -> {
            int updated = jdbc.sql("""
                    UPDATE referral_case SET urgency = ?, triaged_by = ?, triage_reason = ?, status = 'BOOKED',
                           appointment_at = ?, booked_service = ?, booking_status = 'CONFIRMED'
                    WHERE id = ? AND status IN ('NEW', 'TRIAGED', 'INFO_REQUESTED')""")
                    .params(request.urgency(), user.id(), request.reason().strip(), Db.ts(service.nextSlotAt()),
                            service.name(), id).update();
            if (updated == 0) {
                throw new ApiErrors.Conflict("This referral is already booked");
            }
        });
        audit.record(user.id(), "REFERRAL_BOOKED", patientId, id, "ALLOWED", request.urgency());
    }

    /** Returns the referral to the sender for more information. It stays in the New column until answered. */
    @PostMapping("/{id}/request-information")
    @PreAuthorize("hasRole('TRIAGE')")
    public void requestInformation(@PathVariable String id, @Valid @RequestBody InfoRequest request) {
        String patientId = patientOf(id);
        access.requirePatientAccess(patientId, "REFERRAL_INFO_REQUEST");
        int updated = jdbc.sql("""
                UPDATE referral_case SET status = 'INFO_REQUESTED', triaged_by = ?, triage_reason = ?
                WHERE id = ? AND status IN ('NEW', 'TRIAGED')""")
                .params(user.id(), "Information requested: " + request.question().strip(), id).update();
        if (updated == 0) {
            throw new ApiErrors.Conflict("Information can only be requested before booking");
        }
    }

    private String patientOf(String id) {
        return jdbc.sql("SELECT patient_id FROM referral_case WHERE id = ?").param(id).query(String.class).optional()
                .orElseThrow(() -> new ApiErrors.NotFound("Referral not found"));
    }
}
