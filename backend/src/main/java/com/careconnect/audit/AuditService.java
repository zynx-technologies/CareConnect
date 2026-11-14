package com.careconnect.audit;

import com.careconnect.common.Db;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    public record AuditEvent(String id, Instant occurredAt, String userId, String action, String patientId,
                             String resourceId, String outcome, String detail) {}

    private final JdbcClient jdbc;

    public AuditService(JdbcClient jdbc) { this.jdbc = jdbc; }

    /** detail must describe what happened without repeating clinical content. */
    public void record(String userId, String action, String patientId, String resourceId, String outcome, String detail) {
        jdbc.sql("""
                INSERT INTO audit_event (id, occurred_at, user_id, action, patient_id, resource_id, outcome, detail)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(Db.newId(), Db.now(), userId, action, patientId, resourceId, outcome, detail)
                .update();
    }

    public List<AuditEvent> forPatient(String patientId) {
        return jdbc.sql("""
                SELECT id, occurred_at, user_id, action, patient_id, resource_id, outcome, detail
                FROM audit_event WHERE patient_id = ?
                ORDER BY occurred_at DESC OFFSET 0 ROWS FETCH NEXT 500 ROWS ONLY""")
                .param(patientId).query(AuditEvent.class).list();
    }
}
