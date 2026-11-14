package com.careconnect.access;

import com.careconnect.audit.AuditService;
import com.careconnect.common.ApiErrors;
import com.careconnect.common.Db;
import com.careconnect.security.CurrentUser;
import com.careconnect.security.Roles;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * The single place that decides whether someone may see a patient's record. A request passes only if the
 * person has a care relationship and the patient's care-sharing consent is active, or holds a current
 * emergency grant. Every decision is audited, including denials.
 */
@Service
public class AccessService {

    private final JdbcClient jdbc;
    private final AuditService audit;
    private final CurrentUser user;
    private final Duration emergencyWindow;

    public AccessService(JdbcClient jdbc, AuditService audit, CurrentUser user,
                         @Value("${careconnect.access.emergency-minutes}") long emergencyMinutes) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.user = user;
        this.emergencyWindow = Duration.ofMinutes(emergencyMinutes);
    }

    public void requirePatientAccess(String patientId, String action) {
        requireExists(patientId);
        String userId = user.id();
        if (hasEmergencyGrant(patientId, userId)) {
            audit.record(userId, action, patientId, null, "ALLOWED", "emergency access");
            return;
        }
        // Triage clinicians assess referrals before a care relationship exists.
        boolean relationship = isOnCareTeam(patientId, userId) || user.hasRole(Roles.TRIAGE);
        if (!relationship) {
            deny(userId, action, patientId, "no care relationship");
        }
        if (!consentActive(patientId)) {
            deny(userId, action, patientId, "care-sharing consent not active");
        }
        audit.record(userId, action, patientId, null, "ALLOWED", null);
    }

    public Instant grantEmergencyAccess(String patientId, String reason) {
        requireExists(patientId);
        String userId = user.id();
        Instant expires = Instant.now().plus(emergencyWindow);
        jdbc.sql("""
                INSERT INTO emergency_access (id, patient_id, user_id, reason, granted_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)""")
                .params(Db.newId(), patientId, userId, reason, Db.now(), Db.ts(expires))
                .update();
        audit.record(userId, "EMERGENCY_ACCESS_GRANTED", patientId, null, "ALLOWED", "pending privacy review");
        return expires;
    }

    private void deny(String userId, String action, String patientId, String why) {
        audit.record(userId, action, patientId, null, "DENIED", why);
        throw new ApiErrors.AccessDenied("Access to this patient record is not permitted: " + why);
    }

    private void requireExists(String patientId) {
        boolean exists = jdbc.sql("SELECT COUNT(*) FROM patient WHERE id = ?").param(patientId)
                .query(Long.class).single() > 0;
        if (!exists) {
            throw new ApiErrors.NotFound("Patient not found");
        }
    }

    private boolean isOnCareTeam(String patientId, String userId) {
        return jdbc.sql("SELECT COUNT(*) FROM care_team_member WHERE patient_id = ? AND user_id = ?")
                .params(patientId, userId).query(Long.class).single() > 0;
    }

    private boolean consentActive(String patientId) {
        return jdbc.sql("SELECT COUNT(*) FROM consent_policy WHERE patient_id = ? AND purpose = 'CARE' AND status = 'ACTIVE'")
                .param(patientId).query(Long.class).single() > 0;
    }

    private boolean hasEmergencyGrant(String patientId, String userId) {
        Timestamp now = Db.now();
        return jdbc.sql("SELECT COUNT(*) FROM emergency_access WHERE patient_id = ? AND user_id = ? AND expires_at > ?")
                .params(patientId, userId, now).query(Long.class).single() > 0;
    }
}
