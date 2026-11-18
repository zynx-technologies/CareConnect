package com.careconnect.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads risk scores written by the daily scoring job. Scores from a model that is not LIVE (silent trial,
 * suspended, or retired) are never returned, so the safety committee can withdraw a model at once.
 */
@Service
public class RiskService {

    public static final String READMISSION = "cc-readmit";

    public record Factor(String label, String impact, double strength) {}
    public record Risk(String id, String model, String modelVersion, String level, BigDecimal score,
                       List<Factor> factors, Instant scoredAt) {}

    private record Row(String id, String patientId, String model, String modelVersion, String riskLevel,
                       BigDecimal score, String factors, Instant scoredAt) {}

    private static final String LIVE_SCORES = """
            SELECT r.id, r.patient_id, r.model, r.model_version, r.risk_level, r.score, r.factors, r.scored_at
            FROM risk_score r
            JOIN ml_model m ON m.model = r.model AND m.version = r.model_version AND m.clinical_status = 'LIVE'
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public RiskService(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<Risk> current(String patientId, String model) {
        return jdbc.sql(LIVE_SCORES + " WHERE r.patient_id = ? AND r.model = ? ORDER BY r.scored_at DESC")
                .params(patientId, model).query(Row.class).list().stream().findFirst().map(this::view);
    }

    /** Latest level per patient, for lists. Patients with no live score are absent from the map. */
    public Map<String, String> currentLevels(Collection<String> patientIds, String model) {
        Map<String, String> levels = new HashMap<>();
        if (patientIds.isEmpty()) {
            return levels;
        }
        Map<String, Instant> latest = new HashMap<>();
        jdbc.sql(LIVE_SCORES + " WHERE r.model = :model AND r.patient_id IN (:ids)")
                .param("model", model).param("ids", List.copyOf(patientIds))
                .query(Row.class).list()
                .forEach(r -> {
                    Instant seen = latest.get(r.patientId());
                    if (seen == null || r.scoredAt().isAfter(seen)) {
                        latest.put(r.patientId(), r.scoredAt());
                        levels.put(r.patientId(), r.riskLevel());
                    }
                });
        return levels;
    }

    public Optional<String> patientOf(String scoreId) {
        return jdbc.sql("SELECT patient_id FROM risk_score WHERE id = ?").param(scoreId).query(String.class).optional();
    }

    public String factorsJson(List<Factor> factors) {
        return json.writeValueAsString(factors);
    }

    private Risk view(Row r) {
        List<Factor> factors = json.readValue(r.factors(), new TypeReference<>() {});
        return new Risk(r.id(), r.model(), r.modelVersion(), r.riskLevel(), r.score(), factors, r.scoredAt());
    }
}
