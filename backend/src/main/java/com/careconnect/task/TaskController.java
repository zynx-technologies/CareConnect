package com.careconnect.task;

import com.careconnect.access.AccessService;
import com.careconnect.analytics.RiskService;
import com.careconnect.common.ApiErrors;
import com.careconnect.common.Db;
import com.careconnect.security.CurrentUser;
import com.careconnect.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
@RequestMapping("/api/v1")
@PreAuthorize(Roles.CARE_STAFF)
public class TaskController {

    public record WorklistItem(String id, String patientId, String mrn, String familyName, String givenName,
                               String title, String reason, String team, String status, Instant dueAt, String assignedTo,
                               String readmissionRisk) {}
    public record NewTask(@NotBlank @Size(max = 200) String title, @NotNull Instant dueAt, String assignedTo,
                          @Size(max = 200) String reason, @Size(max = 100) String team) {}

    private record Row(String id, String patientId, String mrn, String familyName, String givenName, String title,
                       String reason, String team, String status, Instant dueAt, String assignedTo) {}

    private final JdbcClient jdbc;
    private final AccessService access;
    private final CurrentUser user;
    private final RiskService risk;

    public TaskController(JdbcClient jdbc, AccessService access, CurrentUser user, RiskService risk) {
        this.jdbc = jdbc;
        this.access = access;
        this.user = user;
        this.risk = risk;
    }

    /** Open tasks for patients on the signed-in person's care team, soonest due first. */
    @GetMapping("/worklist")
    public List<WorklistItem> worklist() {
        List<Row> rows = jdbc.sql("""
                SELECT t.id, t.patient_id, p.mrn, p.family_name, p.given_name, t.title, t.reason, t.team, t.status,
                       t.due_at, t.assigned_to
                FROM care_task t
                JOIN patient p ON p.id = t.patient_id
                JOIN care_team_member c ON c.patient_id = t.patient_id AND c.user_id = ?
                JOIN consent_policy cp ON cp.patient_id = t.patient_id AND cp.purpose = 'CARE' AND cp.status = 'ACTIVE'
                WHERE t.status = 'OPEN'
                ORDER BY t.due_at OFFSET 0 ROWS FETCH NEXT 200 ROWS ONLY""")
                .param(user.id()).query(Row.class).list();
        Map<String, String> levels = risk.currentLevels(rows.stream().map(Row::patientId).collect(Collectors.toSet()),
                RiskService.READMISSION);
        return rows.stream().map(r -> new WorklistItem(r.id(), r.patientId(), r.mrn(), r.familyName(), r.givenName(),
                r.title(), r.reason(), r.team(), r.status(), r.dueAt(), r.assignedTo(), levels.get(r.patientId()))).toList();
    }

    @PostMapping("/patients/{patientId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> create(@PathVariable String patientId, @Valid @RequestBody NewTask task) {
        access.requirePatientAccess(patientId, "TASK_CREATE");
        String id = Db.newId();
        jdbc.sql("""
                INSERT INTO care_task (id, patient_id, title, status, due_at, assigned_to, created_at, reason, team)
                VALUES (?, ?, ?, 'OPEN', ?, ?, ?, ?, ?)""")
                .params(id, patientId, task.title(), Db.ts(task.dueAt()), task.assignedTo(), Db.now(), task.reason(), task.team())
                .update();
        return Map.of("id", id);
    }

    @PostMapping("/tasks/{id}/complete")
    public void complete(@PathVariable String id) {
        String patientId = jdbc.sql("SELECT patient_id FROM care_task WHERE id = ?").param(id)
                .query(String.class).optional()
                .orElseThrow(() -> new ApiErrors.NotFound("Task not found"));
        access.requirePatientAccess(patientId, "TASK_COMPLETE");
        int updated = jdbc.sql("UPDATE care_task SET status = 'DONE', completed_at = ?, completed_by = ? WHERE id = ? AND status = 'OPEN'")
                .params(Db.now(), user.id(), id).update();
        if (updated == 0) {
            throw new ApiErrors.Conflict("Task is already complete");
        }
    }
}
