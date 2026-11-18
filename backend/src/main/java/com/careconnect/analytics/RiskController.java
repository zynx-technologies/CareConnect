package com.careconnect.analytics;

import com.careconnect.access.AccessService;
import com.careconnect.audit.AuditService;
import com.careconnect.common.ApiErrors;
import com.careconnect.common.Db;
import com.careconnect.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Clinician feedback on risk scores, and the controlled API the scoring job writes through. */
@RestController
@RequestMapping("/api/v1/risk-scores")
public class RiskController {

    public record Feedback(@NotBlank @Pattern(regexp = "ACCEPTED|DISMISSED|REPORTED") String outcome,
                           @Size(max = 500) String reason) {}

    public record ScoreUpload(@NotBlank String patientId, @NotBlank String model, @NotBlank String modelVersion,
                              @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH") String level,
                              @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal score,
                              @NotNull @Size(max = 10) List<RiskService.Factor> factors) {}

    public record ScoreBatch(@NotEmpty @Size(max = 5000) List<@Valid ScoreUpload> scores) {}

    private final JdbcClient jdbc;
    private final RiskService risk;
    private final AccessService access;
    private final AuditService audit;
    private final CurrentUser user;
    private final TransactionTemplate tx;

    public RiskController(JdbcClient jdbc, RiskService risk, AccessService access, AuditService audit,
                          CurrentUser user, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.risk = risk;
        this.access = access;
        this.audit = audit;
        this.user = user;
        this.tx = tx;
    }

    /** A dismissal or an error report needs a reason, because the governance committee reviews them. */
    @PostMapping("/{id}/feedback")
    @PreAuthorize("hasAnyRole('CLINICIAN','COORDINATOR')")
    public void feedback(@PathVariable String id, @Valid @RequestBody Feedback feedback) {
        String patientId = risk.patientOf(id).orElseThrow(() -> new ApiErrors.NotFound("Risk score not found"));
        access.requirePatientAccess(patientId, "RISK_FEEDBACK");
        String reason = feedback.reason() == null ? null : feedback.reason().strip();
        if (!"ACCEPTED".equals(feedback.outcome()) && (reason == null || reason.length() < 5)) {
            throw new ApiErrors.BadRequest("Give a reason of at least 5 characters");
        }
        Map<String, Object> score = jdbc.sql("SELECT model, model_version FROM risk_score WHERE id = ?").param(id)
                .query().singleRow();
        jdbc.sql("""
                INSERT INTO ai_feedback (id, feature, model_version, patient_id, output_id, outcome, reason, user_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(Db.newId(), score.get("model"), score.get("model_version"), patientId, id,
                        feedback.outcome(), reason, user.id(), Db.now())
                .update();
        audit.record(user.id(), "RISK_FEEDBACK", patientId, id, "ALLOWED", feedback.outcome().toLowerCase());
    }

    /** Daily batch from the scoring job. Each version must already be registered, so nothing unapproved appears. */
    @PutMapping
    @PreAuthorize("hasRole('INTEGRATION')")
    public Map<String, Integer> upload(@Valid @RequestBody ScoreBatch batch) {
        tx.executeWithoutResult(s -> {
            for (ScoreUpload score : batch.scores()) {
                boolean registered = jdbc.sql("SELECT COUNT(*) FROM ml_model WHERE model = ? AND version = ?")
                        .params(score.model(), score.modelVersion()).query(Long.class).single() > 0;
                if (!registered) {
                    throw new ApiErrors.Conflict("Model version is not registered: " + score.model() + " " + score.modelVersion());
                }
                boolean patient = jdbc.sql("SELECT COUNT(*) FROM patient WHERE id = ?").param(score.patientId())
                        .query(Long.class).single() > 0;
                if (!patient) {
                    throw new ApiErrors.NotFound("Unknown patient in score batch");
                }
                jdbc.sql("""
                        INSERT INTO risk_score (id, patient_id, model, model_version, risk_level, score, factors, scored_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
                        .params(Db.newId(), score.patientId(), score.model(), score.modelVersion(), score.level(),
                                score.score(), risk.factorsJson(score.factors()), Db.now())
                        .update();
            }
        });
        audit.record(user.id(), "RISK_SCORES_LOADED", null, null, "ALLOWED", batch.scores().size() + " scores");
        return Map.of("loaded", batch.scores().size());
    }
}
