package com.careconnect.patient;

import com.careconnect.access.AccessService;
import com.careconnect.audit.AuditService;
import com.careconnect.audit.AuditService.AuditEvent;
import com.careconnect.common.ApiErrors;
import com.careconnect.common.Db;
import com.careconnect.security.CurrentUser;
import com.careconnect.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    public record PatientRow(String id, String mrn, String familyName, String givenName, LocalDate birthDate) {}
    public record EncounterRow(String id, String visitNumber, String status, Instant admittedAt, Instant dischargedAt) {}
    public record NoteRow(String id, String noteType, String author, String body, Instant writtenAt) {}
    public record TaskRow(String id, String title, String status, Instant dueAt, String assignedTo) {}
    public record CareTeamRow(String userId, String teamRole) {}
    public record PatientRecord(PatientRow patient, String careConsent, List<CareTeamRow> careTeam,
                                List<EncounterRow> encounters, List<NoteRow> notes, List<TaskRow> tasks) {}
    public record EmergencyRequest(@NotBlank @Size(min = 10, max = 500) String reason) {}
    public record ConsentRequest(@Pattern(regexp = "ACTIVE|WITHDRAWN") @NotBlank String status) {}

    private final JdbcClient jdbc;
    private final AccessService access;
    private final AuditService audit;
    private final CurrentUser user;

    public PatientController(JdbcClient jdbc, AccessService access, AuditService audit, CurrentUser user) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.user = user;
    }

    /** Lists only patients the signed-in person already has a care relationship with. */
    @GetMapping
    @PreAuthorize(Roles.CARE_STAFF)
    public List<PatientRow> myPatients() {
        return jdbc.sql("""
                SELECT p.id, p.mrn, p.family_name, p.given_name, p.birth_date
                FROM patient p JOIN care_team_member c ON c.patient_id = p.id
                WHERE c.user_id = ? ORDER BY p.family_name, p.given_name""")
                .param(user.id()).query(PatientRow.class).list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLINICIAN','COORDINATOR','TRIAGE')")
    public PatientRecord record(@PathVariable String id) {
        access.requirePatientAccess(id, "PATIENT_RECORD_READ");
        return new PatientRecord(
                jdbc.sql("SELECT id, mrn, family_name, given_name, birth_date FROM patient WHERE id = ?")
                        .param(id).query(PatientRow.class).single(),
                jdbc.sql("SELECT status FROM consent_policy WHERE patient_id = ? AND purpose = 'CARE'")
                        .param(id).query(String.class).optional().orElse("NOT_RECORDED"),
                jdbc.sql("SELECT user_id, team_role FROM care_team_member WHERE patient_id = ?")
                        .param(id).query(CareTeamRow.class).list(),
                jdbc.sql("""
                        SELECT id, visit_number, status, admitted_at, discharged_at
                        FROM encounter WHERE patient_id = ? ORDER BY admitted_at DESC""")
                        .param(id).query(EncounterRow.class).list(),
                jdbc.sql("""
                        SELECT id, note_type, author, body, written_at
                        FROM clinical_note WHERE patient_id = ? ORDER BY written_at DESC""")
                        .param(id).query(NoteRow.class).list(),
                jdbc.sql("""
                        SELECT id, title, status, due_at, assigned_to
                        FROM care_task WHERE patient_id = ? ORDER BY due_at""")
                        .param(id).query(TaskRow.class).list());
    }

    @PostMapping("/{id}/emergency-access")
    @PreAuthorize("hasRole('CLINICIAN')")
    public Map<String, Instant> emergencyAccess(@PathVariable String id, @Valid @RequestBody EmergencyRequest request) {
        return Map.of("expiresAt", access.grantEmergencyAccess(id, request.reason()));
    }

    /** Upserts care-sharing consent. Withdrawal applies to the next access check. */
    @PutMapping("/{id}/consent/care")
    @PreAuthorize("hasAnyRole('PRIVACY','COORDINATOR')")
    public void setConsent(@PathVariable String id, @Valid @RequestBody ConsentRequest request) {
        int updated = jdbc.sql("UPDATE consent_policy SET status = ?, updated_at = ?, updated_by = ? WHERE patient_id = ? AND purpose = 'CARE'")
                .params(request.status(), Db.now(), user.id(), id).update();
        if (updated == 0) {
            boolean exists = jdbc.sql("SELECT COUNT(*) FROM patient WHERE id = ?").param(id).query(Long.class).single() > 0;
            if (!exists) {
                throw new ApiErrors.NotFound("Patient not found");
            }
            jdbc.sql("INSERT INTO consent_policy (patient_id, purpose, status, updated_at, updated_by) VALUES (?, 'CARE', ?, ?, ?)")
                    .params(id, request.status(), Db.now(), user.id()).update();
        }
        audit.record(user.id(), "CONSENT_CHANGED", id, null, "ALLOWED", "care sharing set to " + request.status());
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasRole('PRIVACY')")
    public List<AuditEvent> auditTrail(@PathVariable String id) {
        audit.record(user.id(), "AUDIT_READ", id, null, "ALLOWED", null);
        return audit.forPatient(id);
    }
}
