package com.careconnect.integration;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.CanonicalModelClassFactory;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import com.careconnect.audit.AuditService;
import com.careconnect.common.Db;
import com.careconnect.security.CurrentUser;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles admission (ADT^A01) and discharge (ADT^A03) messages. The raw message is stored first, so a failed
 * message is never lost and can be reprocessed. A message is linked to a patient only by exact MRN, because a
 * wrong merge is a patient safety incident.
 */
@Service
public class Hl7Service {

    public record Result(boolean accepted, String ack) {}

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final AuditService audit;
    private final CurrentUser user;
    private final PipeParser parser;

    public Hl7Service(JdbcClient jdbc, TransactionTemplate tx, AuditService audit, CurrentUser user) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.audit = audit;
        this.user = user;
        HapiContext context = new DefaultHapiContext();
        context.setValidationContext(ValidationContextFactory.noValidation());
        context.setModelClassFactory(new CanonicalModelClassFactory("2.5"));
        this.parser = context.getPipeParser();
    }

    public Result receive(String raw) {
        Message message;
        Terser terser;
        String controlId;
        try {
            message = parser.parse(raw.strip().replace("\r\n", "\r").replace("\n", "\r"));
            terser = new Terser(message);
            controlId = terser.get("/.MSH-10");
        } catch (HL7Exception e) {
            // The message cannot be identified, so reject it and let the sending system retry or alert.
            audit.record(user.id(), "HL7_RECEIVE", null, null, "FAILED", "unparseable message");
            return new Result(false, "Message could not be parsed");
        }
        if (controlId == null || controlId.isBlank()) {
            return new Result(false, ack(message, "AR"));
        }

        String type = field(terser, "/.MSH-9-1") + "^" + field(terser, "/.MSH-9-2");
        try {
            jdbc.sql("INSERT INTO hl7_message (control_id, message_type, raw_message, status, received_at) VALUES (?, ?, ?, 'RECEIVED', ?)")
                    .params(controlId, type, raw, Db.now()).update();
        } catch (DuplicateKeyException e) {
            audit.record(user.id(), "HL7_RECEIVE", null, controlId, "DUPLICATE", type);
            return new Result(true, ack(message, "AA"));
        }

        try {
            tx.executeWithoutResult(s -> apply(terser, type));
            jdbc.sql("UPDATE hl7_message SET status = 'PROCESSED' WHERE control_id = ?").param(controlId).update();
            jdbc.sql("UPDATE interface_feed SET last_message_at = ? WHERE code = 'EMR'").param(Db.now()).update();
            audit.record(user.id(), "HL7_RECEIVE", null, controlId, "PROCESSED", type);
            return new Result(true, ack(message, "AA"));
        } catch (RuntimeException e) {
            // Keep the exception out of the logs and table text, because HL7 errors can quote patient data.
            jdbc.sql("UPDATE hl7_message SET status = 'FAILED', error = ? WHERE control_id = ?")
                    .params(e.getClass().getSimpleName(), controlId).update();
            audit.record(user.id(), "HL7_RECEIVE", null, controlId, "FAILED", type);
            return new Result(false, ack(message, "AE"));
        }
    }

    private void apply(Terser terser, String type) {
        switch (type) {
            case "ADT^A01" -> admit(terser);
            case "ADT^A03" -> discharge(terser);
            default -> throw new UnsupportedMessage();
        }
    }

    private void admit(Terser terser) {
        String patientId = upsertPatient(terser);
        String visit = required(terser, "/.PV1-19-1");
        Timestamp admitted = hl7Time(field(terser, "/.PV1-44-1"));
        String ward = blankToNull(field(terser, "/.PV1-3-1"));
        int updated = jdbc.sql("UPDATE encounter SET status = 'ADMITTED', admitted_at = ?, discharged_at = NULL, ward_code = ? WHERE visit_number = ? AND patient_id = ?")
                .params(admitted, ward, visit, patientId).update();
        if (updated == 0) {
            jdbc.sql("INSERT INTO encounter (id, patient_id, visit_number, status, admitted_at, ward_code) VALUES (?, ?, ?, 'ADMITTED', ?, ?)")
                    .params(Db.newId(), patientId, visit, admitted, ward).update();
        }
        jdbc.sql("""
                INSERT INTO consent_policy (patient_id, purpose, status, updated_at, updated_by)
                SELECT ?, 'CARE', 'ACTIVE', ?, 'policy-default' WHERE NOT EXISTS
                  (SELECT 1 FROM consent_policy WHERE patient_id = ? AND purpose = 'CARE')""")
                .params(patientId, Db.now(), patientId).update();
    }

    private void discharge(Terser terser) {
        String patientId = upsertPatient(terser);
        String visit = required(terser, "/.PV1-19-1");
        Timestamp discharged = hl7Time(field(terser, "/.PV1-45-1"));
        int updated = jdbc.sql("UPDATE encounter SET status = 'DISCHARGED', discharged_at = ? WHERE visit_number = ? AND patient_id = ?")
                .params(discharged, visit, patientId).update();
        if (updated == 0) {
            // A discharge can arrive for a visit that started before this feed went live.
            jdbc.sql("INSERT INTO encounter (id, patient_id, visit_number, status, discharged_at) VALUES (?, ?, ?, 'DISCHARGED', ?)")
                    .params(Db.newId(), patientId, visit, discharged).update();
        }
        Instant due = discharged.toInstant().plus(7, ChronoUnit.DAYS);
        jdbc.sql("""
                INSERT INTO care_task (id, patient_id, title, status, due_at, created_at, task_type, reason)
                VALUES (?, ?, 'Book GP follow-up within 7 days of discharge', 'OPEN', ?, ?, 'GP_FOLLOW_UP', 'Discharged')""")
                .params(Db.newId(), patientId, Db.ts(due), Db.now()).update();
    }

    private String upsertPatient(Terser terser) {
        String mrn = required(terser, "/.PID-3-1");
        String family = required(terser, "/.PID-5-1");
        String given = required(terser, "/.PID-5-2");
        String birth = field(terser, "/.PID-7-1");
        var birthDate = birth.length() >= 8 ? java.sql.Date.valueOf(LocalDate.parse(birth.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE)) : null;
        return jdbc.sql("SELECT id FROM patient WHERE mrn = ?").param(mrn).query(String.class).optional().map(id -> {
            jdbc.sql("UPDATE patient SET family_name = ?, given_name = ?, birth_date = ? WHERE id = ?")
                    .params(family, given, birthDate, id).update();
            return id;
        }).orElseGet(() -> {
            String id = Db.newId();
            jdbc.sql("INSERT INTO patient (id, mrn, family_name, given_name, birth_date, created_at) VALUES (?, ?, ?, ?, ?, ?)")
                    .params(id, mrn, family, given, birthDate, Db.now()).update();
            return id;
        });
    }

    /** HL7 times carry no zone unless sent. A zone-less time is treated as UTC until the site's zone is confirmed. */
    private static Timestamp hl7Time(String value) {
        if (value.length() < 12) {
            return Db.now();
        }
        LocalDateTime time = LocalDateTime.parse(value.substring(0, 12), DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        return Timestamp.from(time.toInstant(ZoneOffset.UTC));
    }

    private static String field(Terser terser, String path) {
        try {
            String value = terser.get(path);
            return value == null ? "" : value;
        } catch (HL7Exception e) {
            return "";
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String required(Terser terser, String path) {
        String value = field(terser, path);
        if (value.isBlank()) {
            throw new MissingField();
        }
        return value;
    }

    private static String ack(Message message, String code) {
        try {
            return message.generateACK(ca.uhn.hl7v2.AcknowledgmentCode.valueOf(code), null).encode();
        } catch (HL7Exception | IOException e) {
            return code;
        }
    }

    private static final class UnsupportedMessage extends RuntimeException {}

    private static final class MissingField extends RuntimeException {}
}
