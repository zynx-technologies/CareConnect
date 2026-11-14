package com.careconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class CareConnectApiTest {

    private static final String COORDINATOR = "r.coordinator@example.test";
    private static final String CLINICIAN = "p.example@example.test";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    private static RequestPostProcessor as(String name, String... roles) {
        return user(name).roles(roles);
    }

    private long auditCount(String action, String outcome) {
        return jdbc.sql("SELECT COUNT(*) FROM audit_event WHERE action = ? AND outcome = ?")
                .params(action, outcome).query(Long.class).single();
    }

    @Test
    void healthIsPublicAndEverythingElseNeedsSignIn() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/patients/p-1")).andExpect(status().is4xxClientError());
    }

    @Test
    void careTeamMemberCanReadRecordAndTheReadIsAudited() throws Exception {
        long before = auditCount("PATIENT_RECORD_READ", "ALLOWED");
        mvc.perform(get("/api/v1/patients/p-1").with(as(COORDINATOR, "COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.mrn").value("MRN90021"))
                .andExpect(jsonPath("$.encounters[0].admittedAt", containsString("2026-10-26")));
        assertThat(auditCount("PATIENT_RECORD_READ", "ALLOWED")).isEqualTo(before + 1);
    }

    @Test
    void staffOutsideTheCareTeamAreDeniedAndTheDenialIsAudited() throws Exception {
        long before = auditCount("PATIENT_RECORD_READ", "DENIED");
        mvc.perform(get("/api/v1/patients/p-2").with(as(COORDINATOR, "COORDINATOR")))
                .andExpect(status().isForbidden());
        assertThat(auditCount("PATIENT_RECORD_READ", "DENIED")).isEqualTo(before + 1);
    }

    @Test
    void emergencyAccessNeedsAReasonAndThenOpensTheRecord() throws Exception {
        String clinician = "emergency.doctor@example.test";
        mvc.perform(get("/api/v1/patients/p-2").with(as(clinician, "CLINICIAN"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/patients/p-2/emergency-access").with(as(clinician, "CLINICIAN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/patients/p-2/emergency-access").with(as(clinician, "CLINICIAN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Unconscious in emergency department\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresAt", notNullValue()));
        mvc.perform(get("/api/v1/patients/p-2").with(as(clinician, "CLINICIAN"))).andExpect(status().isOk());
    }

    @Test
    void withdrawnConsentBlocksTheNextReadAndRestoringItReopens() throws Exception {
        mvc.perform(put("/api/v1/patients/p-3/consent/care").with(as("privacy@example.test", "PRIVACY"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"WITHDRAWN\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/patients/p-3").with(as(COORDINATOR, "COORDINATOR"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/patients/p-3/consent/care").with(as("privacy@example.test", "PRIVACY"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/patients/p-3").with(as(COORDINATOR, "COORDINATOR"))).andExpect(status().isOk());
    }

    @Test
    void worklistShowsOnlyOwnCareTeamPatients() throws Exception {
        mvc.perform(get("/api/v1/worklist").with(as("nobody@example.test", "COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/worklist").with(as(COORDINATOR, "COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].mrn", notNullValue()));
    }

    @Test
    void referralMustBeTriagedBeforeBookingAndOnlyByTriageRole() throws Exception {
        String triage = "triage@example.test";
        String booking = "{\"appointmentAt\":\"2099-01-15T01:00:00Z\"}";
        mvc.perform(post("/api/v1/referrals/r-1/book").with(as(triage, "TRIAGE"))
                .contentType(MediaType.APPLICATION_JSON).content(booking)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/referrals/r-1/triage").with(as(COORDINATOR, "COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"urgency\":\"URGENT\",\"reason\":\"Chest pain\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/referrals/r-1/triage").with(as(triage, "TRIAGE"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"urgency\":\"URGENT\",\"reason\":\"Chest pain\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/referrals/r-1/book").with(as(triage, "TRIAGE"))
                .contentType(MediaType.APPLICATION_JSON).content(booking)).andExpect(status().isOk());
    }

    @Test
    void draftIsNotPartOfTheRecordUntilAClinicianSignsIt() throws Exception {
        long notesBefore = jdbc.sql("SELECT COUNT(*) FROM clinical_note WHERE patient_id = 'p-3'").query(Long.class).single();
        String response = mvc.perform(post("/api/v1/patients/p-3/discharge-drafts").with(as(CLINICIAN, "CLINICIAN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.statements[0].sourceIds[0]", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        String id = response.replaceAll(".*?\"id\":\"([^\"]+)\".*", "$1");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM clinical_note WHERE patient_id = 'p-3'").query(Long.class).single()).isEqualTo(notesBefore);

        String signature = "{\"content\":\"Reviewed summary\",\"reviewedSources\":%s}";
        mvc.perform(post("/api/v1/discharge-drafts/" + id + "/sign").with(as(COORDINATOR, "COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON).content(signature.formatted(true))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/discharge-drafts/" + id + "/sign").with(as(CLINICIAN, "CLINICIAN"))
                .contentType(MediaType.APPLICATION_JSON).content(signature.formatted(false))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/discharge-drafts/" + id + "/sign").with(as(CLINICIAN, "CLINICIAN"))
                .contentType(MediaType.APPLICATION_JSON).content(signature.formatted(true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.signedBy").value(CLINICIAN));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM clinical_note WHERE patient_id = 'p-3'").query(Long.class).single()).isEqualTo(notesBefore + 1);
        mvc.perform(post("/api/v1/discharge-drafts/" + id + "/sign").with(as(CLINICIAN, "CLINICIAN"))
                .contentType(MediaType.APPLICATION_JSON).content(signature.formatted(true))).andExpect(status().isConflict());
    }

    private static String adt(String trigger, String controlId, String mrn, String visit) {
        return "MSH|^~\\&|EMR|HOSP|CARECONNECT|HOSP|202610300900||ADT^" + trigger + "^ADT_" + trigger + "|" + controlId + "|P|2.5\r"
                + "EVN|" + trigger + "|202610300900\r"
                + "PID|1||" + mrn + "^^^HOSP^MR||Hl7^Morgan||19700102|U\r"
                + "PV1|1|I|WARD1||||||||||||||||" + visit + "|||||||||||||||||||||||||202610280800|202610300900\r";
    }

    @Test
    void admissionAndDischargeCreateRecordsAndAFollowUpTaskAndDuplicatesAreIgnored() throws Exception {
        MediaType hl7 = MediaType.TEXT_PLAIN;
        mvc.perform(post("/api/v1/integration/hl7").with(as("emr-feed", "INTEGRATION")).contentType(hl7)
                        .content(adt("A01", "MSG-1", "MRN-HL7", "V-HL7")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("MSA|AA|MSG-1")));
        mvc.perform(post("/api/v1/integration/hl7").with(as("emr-feed", "INTEGRATION")).contentType(hl7)
                        .content(adt("A03", "MSG-2", "MRN-HL7", "V-HL7")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/integration/hl7").with(as("emr-feed", "INTEGRATION")).contentType(hl7)
                        .content(adt("A03", "MSG-2", "MRN-HL7", "V-HL7")))
                .andExpect(status().isOk());

        assertThat(jdbc.sql("SELECT status FROM encounter WHERE visit_number = 'V-HL7'").query(String.class).single()).isEqualTo("DISCHARGED");
        assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM care_task t JOIN patient p ON p.id = t.patient_id
                WHERE p.mrn = 'MRN-HL7' AND t.title LIKE 'Book GP%'""").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void hl7RejectsOtherRolesAndKeepsFailedMessagesForReprocessing() throws Exception {
        mvc.perform(post("/api/v1/integration/hl7").with(as(COORDINATOR, "COORDINATOR")).contentType(MediaType.TEXT_PLAIN)
                .content(adt("A01", "MSG-X", "M", "V"))).andExpect(status().isForbidden());
        String missingMrn = adt("A01", "MSG-BAD", "", "V-BAD");
        mvc.perform(post("/api/v1/integration/hl7").with(as("emr-feed", "INTEGRATION")).contentType(MediaType.TEXT_PLAIN)
                        .content(missingMrn))
                .andExpect(status().isUnprocessableContent()).andExpect(content().string(containsString("MSA|AE|MSG-BAD")));
        assertThat(jdbc.sql("SELECT status FROM hl7_message WHERE control_id = 'MSG-BAD'").query(String.class).single()).isEqualTo("FAILED");
    }
}
