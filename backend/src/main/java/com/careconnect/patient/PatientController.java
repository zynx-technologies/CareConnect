package com.careconnect.patient;

import com.careconnect.access.AccessService;
import com.careconnect.analytics.RiskService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    public record PatientListItem(String id, String mrn, String familyName, String givenName, LocalDate birthDate,
                                  String ward, String encounterStatus, String readmissionRisk) {}
    public record EncounterRow(String id, String visitNumber, String status, String wardCode, Instant admittedAt,
                               Instant dischargedAt, Instant expectedDischargeAt) {}
    public record NoteRow(String id, String noteType, String author, String body, Instant writtenAt) {}
    public record TaskRow(String id, String title, String reason, String status, Instant dueAt, String assignedTo) {}
    public record CareTeamRow(String userId, String displayName, String teamRole) {}
    public record AlertRow(String category, String detail, String severity) {}
    public record ConsentRow(String purpose, String status, Instant updatedAt) {}
    public record MedicationRow(String name, String dose, String status) {}
    public record TimelineEntry(Instant occurredAt, String description, String source) {}
    public record PatientRecord(PatientRow patient, String careConsent, List<ConsentRow> consents, List<AlertRow> alerts,
                                List<CareTeamRow> careTeam, List<EncounterRow> encounters, List<TimelineEntry> timeline,
                                List<MedicationRow> medicines, List<NoteRow> notes, List<TaskRow> tasks,
                                NoteRow latestSummary, RiskService.Risk readmissionRisk) {}
    public record EmergencyRequest(@NotBlank @Size(min = 10, max = 500) String reason) {}
    public record ConsentRequest(@Pattern(regexp = "ACTIVE|WITHDRAWN") @NotBlank String status) {}

    private record WardRow(String patientId, String wardCode, String status, Instant admittedAt) {}

    private final JdbcClient jdbc;
    private final AccessService access;
    private final AuditService audit;
    private final CurrentUser user;
    private final RiskService risk;

    public PatientController(JdbcClient jdbc, AccessService access, AuditService audit, CurrentUser user, RiskService risk) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.user = user;
        this.risk = risk;
    }

    /** Lists only patients the signed-in person already has a care relationship with. */
    @GetMapping
    @PreAuthorize(Roles.CARE_STAFF)
    public List<PatientListItem> myPatients() {
        List<PatientRow> rows = jdbc.sql("""
                SELECT p.id, p.mrn, p.family_name, p.given_name, p.birth_date
                FROM patient p JOIN care_team_member c ON c.patient_id = p.id
                WHERE c.user_id = ? ORDER BY p.family_name, p.given_name""")
                .param(user.id()).query(PatientRow.class).list();
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> ids = rows.stream().map(PatientRow::id).toList();
        Map<String, WardRow> latest = jdbc.sql("""
                SELECT e.patient_id, COALESCE(w.name, e.ward_code) AS ward_code, e.status, e.admitted_at
                FROM encounter e LEFT JOIN ward w ON w.code = e.ward_code WHERE e.patient_id IN (:ids)""")
                .param("ids", ids).query(WardRow.class).list().stream()
                .collect(Collectors.toMap(WardRow::patientId, w -> w, (a, b) ->
                        (b.admittedAt() != null && (a.admittedAt() == null || b.admittedAt().isAfter(a.admittedAt()))) ? b : a));
        Map<String, String> levels = risk.currentLevels(ids, RiskService.READMISSION);
        return rows.stream().map(p -> {
            WardRow w = latest.get(p.id());
            return new PatientListItem(p.id(), p.mrn(), p.familyName(), p.givenName(), p.birthDate(),
                    w == null ? null : w.wardCode(), w == null ? null : w.status(), levels.get(p.id()));
        }).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLINICIAN','COORDINATOR','TRIAGE')")
    public PatientRecord record(@PathVariable String id) {
        access.requirePatientAccess(id, "PATIENT_RECORD_READ");
        List<ConsentRow> consents = jdbc.sql("SELECT purpose, status, updated_at FROM consent_policy WHERE patient_id = ? ORDER BY purpose")
                .param(id).query(ConsentRow.class).list();
        List<EncounterRow> encounters = jdbc.sql("""
                SELECT e.id, e.visit_number, e.status, COALESCE(w.name, e.ward_code) AS ward_code, e.admitted_at,
                       e.discharged_at, e.expected_discharge_at
                FROM encounter e LEFT JOIN ward w ON w.code = e.ward_code
                WHERE e.patient_id = ? ORDER BY e.admitted_at DESC""")
                .param(id).query(EncounterRow.class).list();
        List<NoteRow> notes = jdbc.sql("""
                SELECT id, note_type, author, body, written_at
                FROM clinical_note WHERE patient_id = ? ORDER BY written_at DESC""")
                .param(id).query(NoteRow.class).list();
        return new PatientRecord(
                jdbc.sql("SELECT id, mrn, family_name, given_name, birth_date FROM patient WHERE id = ?")
                        .param(id).query(PatientRow.class).single(),
                consents.stream().filter(c -> "CARE".equals(c.purpose())).map(ConsentRow::status).findFirst().orElse("NOT_RECORDED"),
                consents,
                jdbc.sql("SELECT category, detail, severity FROM patient_alert WHERE patient_id = ? ORDER BY recorded_at")
                        .param(id).query(AlertRow.class).list(),
                jdbc.sql("SELECT user_id, display_name, team_role FROM care_team_member WHERE patient_id = ? ORDER BY team_role")
                        .param(id).query(CareTeamRow.class).list(),
                encounters,
                timeline(id, encounters),
                jdbc.sql("SELECT name, dose, status FROM medication WHERE patient_id = ? AND status <> 'STOPPED' ORDER BY name")
                        .param(id).query(MedicationRow.class).list(),
                notes,
                jdbc.sql("""
                        SELECT id, title, reason, status, due_at, assigned_to
                        FROM care_task WHERE patient_id = ? ORDER BY due_at""")
                        .param(id).query(TaskRow.class).list(),
                notes.stream().filter(n -> "Discharge summary".equals(n.noteType())).findFirst().orElse(null),
                risk.current(id, RiskService.READMISSION).orElse(null));
    }

    private List<TimelineEntry> timeline(String id, List<EncounterRow> encounters) {
        List<TimelineEntry> entries = new ArrayList<>(jdbc.sql(
                        "SELECT occurred_at, description, source FROM clinical_event WHERE patient_id = ?")
                .param(id).query(TimelineEntry.class).list());
        for (EncounterRow e : encounters) {
            String ward = e.wardCode() == null ? "" : ", " + e.wardCode();
            if (e.admittedAt() != null) entries.add(new TimelineEntry(e.admittedAt(), "Admitted" + ward, "EMR"));
            if (e.dischargedAt() != null) entries.add(new TimelineEntry(e.dischargedAt(), "Discharged" + ward, "EMR"));
        }
        entries.sort(Comparator.comparing(TimelineEntry::occurredAt).reversed());
        return entries.size() > 30 ? entries.subList(0, 30) : entries;
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
