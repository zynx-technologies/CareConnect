package com.careconnect.analytics;

import com.careconnect.audit.AuditService;
import com.careconnect.common.ApiErrors;
import com.careconnect.common.Db;
import com.careconnect.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Evidence for the clinical safety committee. Status rules use the release-gate thresholds in section 8. */
@RestController
@RequestMapping("/api/v1/governance")
@PreAuthorize("hasRole('SAFETY')")
public class GovernanceController {

    /** A subgroup within this margin of a limit is marked WATCH. */
    static final BigDecimal AUROC_MARGIN = new BigDecimal("0.02");
    static final BigDecimal CALIBRATION_MARGIN = new BigDecimal("0.01");

    public record ModelSummary(String model, String version, String purpose, String clinicalStatus) {}
    public record Subgroup(String groupName, BigDecimal auroc, BigDecimal sensitivity, BigDecimal calibrationError, String status) {}
    public record FeedbackRates(long total, double accepted, double dismissed, double reported) {}
    public record DraftOutcomes(long signed, long discarded, long awaitingReview) {}
    public record Evidence(String item, String owner, String status, LocalDate recordedOn) {}
    public record OpenAction(String title, String detail, String status) {}
    public record ModelView(String model, String version, String purpose, String clinicalStatus, BigDecimal auroc,
                            BigDecimal minAuroc, BigDecimal calibrationError, BigDecimal maxCalibrationError,
                            LocalDate nextReview, String statusChangedBy, Instant statusChangedAt, String statusReason,
                            List<Subgroup> subgroups, FeedbackRates feedback, DraftOutcomes drafts,
                            CapacityService.Accuracy forecastAccuracy, List<Evidence> evidence, List<OpenAction> actions,
                            boolean featureSwitchedOff) {}
    public record StatusChange(@NotBlank @Pattern(regexp = "LIVE|SUSPENDED") String status,
                               @NotBlank @Size(min = 10, max = 500) String reason) {}

    private record ModelRow(String model, String version, String purpose, String clinicalStatus, BigDecimal auroc,
                            BigDecimal calibrationError, BigDecimal minAuroc, BigDecimal maxCalibrationError,
                            LocalDate nextReview, String statusChangedBy, Instant statusChangedAt, String statusReason) {}
    private record MetricRow(String groupName, BigDecimal auroc, BigDecimal sensitivity, BigDecimal calibrationError) {}

    private final JdbcClient jdbc;
    private final CapacityService capacity;
    private final AuditService audit;
    private final CurrentUser user;
    private final boolean draftsEnabled;

    public GovernanceController(JdbcClient jdbc, CapacityService capacity, AuditService audit, CurrentUser user,
                                @Value("${careconnect.ai.enabled}") boolean draftsEnabled) {
        this.jdbc = jdbc;
        this.capacity = capacity;
        this.audit = audit;
        this.user = user;
        this.draftsEnabled = draftsEnabled;
    }

    @GetMapping("/models")
    public List<ModelSummary> models() {
        return jdbc.sql("SELECT model, version, purpose, clinical_status FROM ml_model ORDER BY model, version")
                .query(ModelSummary.class).list();
    }

    @GetMapping("/models/{model}/{version}")
    public ModelView model(@PathVariable String model, @PathVariable String version) {
        ModelRow m = jdbc.sql("""
                SELECT model, version, purpose, clinical_status, auroc, calibration_error, min_auroc, max_calibration_error,
                       next_review, status_changed_by, status_changed_at, status_reason
                FROM ml_model WHERE model = ? AND version = ?""")
                .params(model, version).query(ModelRow.class).optional()
                .orElseThrow(() -> new ApiErrors.NotFound("Model version not found"));

        List<Subgroup> subgroups = jdbc.sql("""
                SELECT group_name, auroc, sensitivity, calibration_error FROM ml_subgroup_metric
                WHERE model = ? AND version = ? ORDER BY display_order""")
                .params(model, version).query(MetricRow.class).list().stream()
                .map(r -> new Subgroup(r.groupName(), r.auroc(), r.sensitivity(), r.calibrationError(),
                        subgroupStatus(r.auroc(), r.calibrationError(), m.minAuroc(), m.maxCalibrationError())))
                .toList();

        DraftOutcomes drafts = null;
        if ("cc-summary".equals(model)) {
            drafts = new DraftOutcomes(countDrafts("SIGNED"), countDrafts("DISCARDED"), countDrafts("DRAFT"));
        }
        CapacityService.Accuracy accuracy = "cc-capacity".equals(model) ? capacity.accuracy() : null;

        List<Evidence> evidence = jdbc.sql("""
                SELECT item, owner, status, recorded_on FROM ml_evidence WHERE model = ? AND version = ? ORDER BY recorded_on DESC, item""")
                .params(model, version).query(Evidence.class).list();
        List<OpenAction> actions = jdbc.sql("""
                SELECT title, detail, status FROM ml_action WHERE model = ? AND version = ? AND status = 'OPEN' ORDER BY title""")
                .params(model, version).query(OpenAction.class).list();

        return new ModelView(m.model(), m.version(), m.purpose(), m.clinicalStatus(), m.auroc(), m.minAuroc(),
                m.calibrationError(), m.maxCalibrationError(), m.nextReview(), m.statusChangedBy(), m.statusChangedAt(),
                m.statusReason(), subgroups, feedback(model, version), drafts, accuracy, evidence, actions,
                "cc-summary".equals(model) && !draftsEnabled);
    }

    /**
     * The safety kill switch for risk models. A suspended model's scores disappear from every care screen at
     * once. Only a live model can be suspended, and only a suspended one can be restored.
     */
    @PostMapping("/models/{model}/{version}/status")
    public void changeStatus(@PathVariable String model, @PathVariable String version, @Valid @RequestBody StatusChange change) {
        String from = "LIVE".equals(change.status()) ? "SUSPENDED" : "LIVE";
        int updated = jdbc.sql("""
                UPDATE ml_model SET clinical_status = ?, status_changed_by = ?, status_changed_at = ?, status_reason = ?
                WHERE model = ? AND version = ? AND clinical_status = ?""")
                .params(change.status(), user.id(), Db.now(), change.reason().strip(), model, version, from).update();
        if (updated == 0) {
            throw new ApiErrors.Conflict("Only a live model can be suspended, and only a suspended model restored");
        }
        audit.record(user.id(), "MODEL_STATUS_CHANGED", null, model + ":" + version, "ALLOWED", from + " to " + change.status());
    }

    static String subgroupStatus(BigDecimal auroc, BigDecimal calibration, BigDecimal minAuroc, BigDecimal maxCalibration) {
        if (minAuroc == null || maxCalibration == null) {
            return "NO_LIMIT";
        }
        if (auroc.compareTo(minAuroc) < 0 || calibration.compareTo(maxCalibration) > 0) {
            return "REVIEW";
        }
        if (auroc.compareTo(minAuroc.add(AUROC_MARGIN)) < 0 || calibration.compareTo(maxCalibration.subtract(CALIBRATION_MARGIN)) > 0) {
            return "WATCH";
        }
        return "OK";
    }

    private FeedbackRates feedback(String model, String version) {
        record Count(String outcome, long n) {}
        List<Count> counts = jdbc.sql("""
                SELECT outcome, COUNT(*) AS n FROM ai_feedback WHERE feature = ? AND model_version = ? GROUP BY outcome""")
                .params(model, version).query(Count.class).list();
        long total = counts.stream().mapToLong(Count::n).sum();
        if (total == 0) {
            return new FeedbackRates(0, 0, 0, 0);
        }
        java.util.function.ToDoubleFunction<String> share = outcome -> Math.round(counts.stream()
                .filter(c -> c.outcome().equals(outcome)).mapToLong(Count::n).sum() * 100.0 / total) / 100.0;
        return new FeedbackRates(total, share.applyAsDouble("ACCEPTED"), share.applyAsDouble("DISMISSED"), share.applyAsDouble("REPORTED"));
    }

    private long countDrafts(String status) {
        return jdbc.sql("SELECT COUNT(*) FROM ai_draft WHERE status = ?").param(status).query(Long.class).single();
    }
}
