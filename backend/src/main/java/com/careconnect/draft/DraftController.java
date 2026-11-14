package com.careconnect.draft;

import com.careconnect.access.AccessService;
import com.careconnect.audit.AuditService;
import com.careconnect.common.ApiErrors;
import com.careconnect.common.Db;
import com.careconnect.draft.DraftChecker.CheckedStatement;
import com.careconnect.draft.DraftGenerator.SourceNote;
import com.careconnect.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** AI drafts are kept apart from the record. Only a clinician's signature creates a clinical note. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('CLINICIAN')")
public class DraftController {

    public record DraftView(String id, String patientId, String status, String content, List<CheckedStatement> statements,
                            String generator, Instant createdAt, String signedBy, Instant signedAt, String signedNoteId) {}
    public record SignRequest(@NotBlank String content,
                              @AssertTrue(message = "The clinician must confirm they reviewed the sources") boolean reviewedSources) {}

    private record Row(String id, String patientId, String status, String content, String statements, String generator,
                       Instant createdAt, String signedBy, Instant signedAt, String signedNoteId) {}

    private final JdbcClient jdbc;
    private final AccessService access;
    private final AuditService audit;
    private final CurrentUser user;
    private final DraftGenerator generator;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final boolean enabled;

    public DraftController(JdbcClient jdbc, AccessService access, AuditService audit, CurrentUser user,
                           DraftGenerator generator, ObjectMapper json, TransactionTemplate tx,
                           @Value("${careconnect.ai.enabled}") boolean enabled) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.user = user;
        this.generator = generator;
        this.json = json;
        this.tx = tx;
        this.enabled = enabled;
    }

    @PostMapping("/patients/{patientId}/discharge-drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public DraftView create(@PathVariable String patientId) {
        if (!enabled) {
            throw new ApiErrors.Unavailable("AI drafting is turned off");
        }
        access.requirePatientAccess(patientId, "AI_DRAFT_CREATE");
        List<SourceNote> notes = jdbc.sql("SELECT id, note_type, body FROM clinical_note WHERE patient_id = ? ORDER BY written_at")
                .param(patientId).query(SourceNote.class).list();
        if (notes.isEmpty()) {
            throw new ApiErrors.Conflict("This patient has no notes to summarise");
        }
        List<CheckedStatement> checked = DraftChecker.check(generator.generate(notes), notes);
        String content = checked.stream().map(CheckedStatement::text).collect(Collectors.joining("\n"));
        String id = Db.newId();
        jdbc.sql("""
                INSERT INTO ai_draft (id, patient_id, status, content, statements, generator, created_at, created_by)
                VALUES (?, ?, 'DRAFT', ?, ?, ?, ?, ?)""")
                .params(id, patientId, content, json.writeValueAsString(checked), generator.name(), Db.now(), user.id())
                .update();
        return get(id);
    }

    @GetMapping("/discharge-drafts/{id}")
    public DraftView get(@PathVariable String id) {
        Row row = load(id);
        access.requirePatientAccess(row.patientId(), "AI_DRAFT_READ");
        return view(row);
    }

    /** Files the clinician's final wording as a note. The signature and draft status change together. */
    @PostMapping("/discharge-drafts/{id}/sign")
    public DraftView sign(@PathVariable String id, @Valid @RequestBody SignRequest request) {
        Row row = load(id);
        access.requirePatientAccess(row.patientId(), "AI_DRAFT_SIGN");
        String noteId = Db.newId();
        tx.executeWithoutResult(status -> {
            jdbc.sql("""
                    INSERT INTO clinical_note (id, patient_id, note_type, author, body, written_at)
                    VALUES (?, ?, 'Discharge summary', ?, ?, ?)""")
                    .params(noteId, row.patientId(), user.id(), request.content(), Db.now()).update();
            int updated = jdbc.sql("""
                    UPDATE ai_draft SET status = 'SIGNED', content = ?, signed_at = ?, signed_by = ?, signed_note_id = ?
                    WHERE id = ? AND status = 'DRAFT'""")
                    .params(request.content(), Db.now(), user.id(), noteId, id).update();
            if (updated == 0) {
                throw new ApiErrors.Conflict("This draft has already been signed or discarded");
            }
        });
        audit.record(user.id(), "AI_DRAFT_SIGNED", row.patientId(), id, "ALLOWED", "generator " + row.generator());
        return view(load(id));
    }

    @PostMapping("/discharge-drafts/{id}/discard")
    public void discard(@PathVariable String id) {
        Row row = load(id);
        access.requirePatientAccess(row.patientId(), "AI_DRAFT_DISCARD");
        int updated = jdbc.sql("UPDATE ai_draft SET status = 'DISCARDED' WHERE id = ? AND status = 'DRAFT'").param(id).update();
        if (updated == 0) {
            throw new ApiErrors.Conflict("This draft has already been signed or discarded");
        }
    }

    private Row load(String id) {
        return jdbc.sql("""
                SELECT id, patient_id, status, content, statements, generator, created_at, signed_by, signed_at, signed_note_id
                FROM ai_draft WHERE id = ?""")
                .param(id).query(Row.class).optional()
                .orElseThrow(() -> new ApiErrors.NotFound("Draft not found"));
    }

    private DraftView view(Row r) {
        List<CheckedStatement> statements = json.readValue(r.statements(), new TypeReference<>() {});
        return new DraftView(r.id(), r.patientId(), r.status(), r.content(), statements, r.generator(), r.createdAt(),
                r.signedBy(), r.signedAt(), r.signedNoteId());
    }
}
