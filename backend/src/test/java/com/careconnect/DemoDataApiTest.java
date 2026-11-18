package com.careconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careconnect.demo.DemoDataLoader;
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

/** Loads the synthetic demo data and checks each screen's API, analytics, and safety rule against it. */
@SpringBootTest(properties = "careconnect.demo-data.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class DemoDataApiTest {

    private static final String COORDINATOR = "r.coordinator@example.test";
    private static final String CLINICIAN = "p.example@example.test";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired DemoDataLoader loader;

    private static RequestPostProcessor as(String name, String... roles) {
        return user(name).roles(roles);
    }

    private String one(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query(String.class).list().getFirst();
    }

    @Test
    void loadsOncePerDay() {
        long before = jdbc.sql("SELECT COUNT(*) FROM patient WHERE id LIKE 'demo-%'").query(Long.class).single();
        loader.ensureCurrent();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM patient WHERE id LIKE 'demo-%'").query(Long.class).single()).isEqualTo(before).isGreaterThan(400);
    }

    @Test
    void worklistShowsKpisTeamLoadInterfacesAndRisk() throws Exception {
        mvc.perform(get("/api/v1/worklist/summary").with(as(COORDINATOR, "COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openTasks", greaterThanOrEqualTo(35)))
                .andExpect(jsonPath("$.overdueTasks", greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.referralsAwaitingTriage", greaterThanOrEqualTo(14)))
                .andExpect(jsonPath("$.followUpWithin7Days", closeTo(0.83, 0.03)))
                .andExpect(jsonPath("$.teamLoad", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$.interfaces[?(@.code == 'GP')].status").value(hasItem("DELAYED")))
                .andExpect(jsonPath("$.interfaces[?(@.code == 'EMR')].status").value(hasItem("LIVE")));
        mvc.perform(get("/api/v1/worklist").with(as(COORDINATOR, "COORDINATOR")))
                .andExpect(jsonPath("$[?(@.mrn == '90021')].readmissionRisk").value(hasItem("HIGH")));
    }

    @Test
    void patientRecordShowsAlertsConsentTimelineMedicinesAndRiskWithFeedback() throws Exception {
        mvc.perform(get("/api/v1/patients/demo-p-90021").with(as(COORDINATOR, "COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts", hasSize(2)))
                .andExpect(jsonPath("$.consents", hasSize(4)))
                .andExpect(jsonPath("$.medicines", hasSize(3)))
                .andExpect(jsonPath("$.timeline.length()", greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.latestSummary.author").value("Dr P. Example"))
                .andExpect(jsonPath("$.readmissionRisk.level").value("HIGH"))
                .andExpect(jsonPath("$.readmissionRisk.factors", hasSize(3)));
        String scoreId = one("SELECT id FROM risk_score WHERE patient_id = 'demo-p-90021'");
        String feedback = "{\"outcome\":\"DISMISSED\",\"reason\":%s}";
        mvc.perform(post("/api/v1/risk-scores/" + scoreId + "/feedback").with(as(COORDINATOR, "COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON).content(feedback.formatted("null"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/risk-scores/" + scoreId + "/feedback").with(as(COORDINATOR, "COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON).content(feedback.formatted("\"Community support in place\""))).andExpect(status().isOk());
    }

    @Test
    void silentTrialScoresAreHiddenAndSuspendingALiveModelHidesItsScores() throws Exception {
        assertThat(jdbc.sql("SELECT COUNT(*) FROM risk_score WHERE model = 'cc-noshow'").query(Long.class).single()).isPositive();
        String safety = "safety@example.test";
        String change = "{\"status\":\"%s\",\"reason\":\"Safety committee decision for testing\"}";
        mvc.perform(post("/api/v1/governance/models/cc-readmit/v6/status").with(as(safety, "SAFETY"))
                .contentType(MediaType.APPLICATION_JSON).content(change.formatted("SUSPENDED"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/patients/demo-p-90021").with(as(COORDINATOR, "COORDINATOR")))
                .andExpect(jsonPath("$.readmissionRisk").doesNotExist());
        mvc.perform(post("/api/v1/governance/models/cc-readmit/v6/status").with(as(safety, "SAFETY"))
                .contentType(MediaType.APPLICATION_JSON).content(change.formatted("LIVE"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/patients/demo-p-90021").with(as(COORDINATOR, "COORDINATOR")))
                .andExpect(jsonPath("$.readmissionRisk.level").value("HIGH"));
    }

    @Test
    void capacityShowsCensusForecastWardPressureAndAccuracy() throws Exception {
        mvc.perform(get("/api/v1/capacity").with(as(COORDINATOR, "COORDINATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/capacity").with(as("manager@example.test", "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.beds").value(450))
                .andExpect(jsonPath("$.summary.occupied").value(412))
                .andExpect(jsonPath("$.summary.peak.p50", notNullValue()))
                .andExpect(jsonPath("$.summary.expectedDischargesToday").value(47))
                .andExpect(jsonPath("$.summary.awaitingTransport").value(11))
                .andExpect(jsonPath("$.summary.nursingGapShifts").value(9))
                .andExpect(jsonPath("$.summary.nursingGapLabel").value("Medicine, nights"))
                .andExpect(jsonPath("$.series[?(@.p50 != null)]", hasSize(7)))
                .andExpect(jsonPath("$.wards", hasSize(5)))
                .andExpect(jsonPath("$.wards[0].code").value("MED"))
                .andExpect(jsonPath("$.wards[0].status").value("HIGH"))
                .andExpect(jsonPath("$.accuracy.samples", greaterThan(0)))
                .andExpect(jsonPath("$.actions", hasSize(3)));
    }

    @Test
    void governanceShowsSubgroupStatusFeedbackAndEvidence() throws Exception {
        mvc.perform(get("/api/v1/governance/models/cc-readmit/v6").with(as("safety@example.test", "SAFETY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicalStatus").value("LIVE"))
                .andExpect(jsonPath("$.subgroups", hasSize(7)))
                .andExpect(jsonPath("$.subgroups[2].status").value("REVIEW"))
                .andExpect(jsonPath("$.subgroups[3].status").value("WATCH"))
                .andExpect(jsonPath("$.subgroups[4].status").value("OK"))
                .andExpect(jsonPath("$.feedback.total", greaterThanOrEqualTo(120)))
                .andExpect(jsonPath("$.evidence", hasSize(5)))
                .andExpect(jsonPath("$.actions", hasSize(3)));
        mvc.perform(get("/api/v1/governance/models/cc-capacity/baseline-1").with(as("safety@example.test", "SAFETY")))
                .andExpect(jsonPath("$.forecastAccuracy.samples", greaterThan(0)));
    }

    @Test
    void referralBoardSuggestsRapidAccessForUrgentAndBooksInOneStep() throws Exception {
        String triage = "triage@example.test";
        mvc.perform(get("/api/v1/referrals").with(as(triage, "TRIAGE")))
                .andExpect(jsonPath("$[?(@.status == 'BOOKED')]", hasSize(greaterThanOrEqualTo(21))));
        String referral = one("SELECT id FROM referral_case WHERE patient_id = 'demo-p-90143'");
        mvc.perform(get("/api/v1/referrals/" + referral + "/suggestions").with(as(triage, "TRIAGE")))
                .andExpect(jsonPath("$[0].name").value("Rapid-access chest pain clinic"))
                .andExpect(jsonPath("$", hasSize(3)));
        String service = one("SELECT id FROM care_service WHERE name = 'Rapid-access chest pain clinic'");
        mvc.perform(post("/api/v1/referrals/" + referral + "/accept-and-book").with(as(triage, "TRIAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":\"" + service + "\",\"urgency\":\"URGENT\",\"reason\":\"Accepted suggested urgency\"}"))
                .andExpect(status().isOk());
        assertThat(one("SELECT booked_service FROM referral_case WHERE id = ?", referral)).isEqualTo("Rapid-access chest pain clinic");
    }

    @Test
    void noteReviewListsTheDraftWithTracedFactsAndItemsToCheck() throws Exception {
        mvc.perform(get("/api/v1/discharge-drafts").with(as(CLINICIAN, "CLINICIAN")))
                .andExpect(jsonPath("$[?(@.mrn == '90212')]", hasSize(1)));
        String draft = one("SELECT id FROM ai_draft WHERE patient_id = 'demo-p-90212'");
        mvc.perform(get("/api/v1/discharge-drafts/" + draft).with(as(CLINICIAN, "CLINICIAN")))
                .andExpect(jsonPath("$.tracedStatements").value(4))
                .andExpect(jsonPath("$.statements[0].section").value("Hospital course"))
                .andExpect(jsonPath("$.itemsToCheck").value(hasItem("No GP appointment found")));
    }

    @Test
    void privacyOfficerReviewsEmergencyAccess() throws Exception {
        String privacy = "privacy@example.test";
        mvc.perform(get("/api/v1/audit/summary").with(as(privacy, "PRIVACY")))
                .andExpect(jsonPath("$.emergencyPending", greaterThanOrEqualTo(2)));
        String pending = one("SELECT id FROM emergency_access WHERE user_id = 'locum.doctor@example.test'");
        String review = "{\"outcome\":\"APPROPRIATE\",\"note\":\"Confirmed with the ED consultant\"}";
        mvc.perform(post("/api/v1/audit/emergency-access/" + pending + "/review").with(as(privacy, "PRIVACY"))
                .contentType(MediaType.APPLICATION_JSON).content(review)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/audit/emergency-access/" + pending + "/review").with(as(privacy, "PRIVACY"))
                .contentType(MediaType.APPLICATION_JSON).content(review)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/audit/events").param("userId", privacy).with(as(privacy, "PRIVACY")))
                .andExpect(jsonPath("$[0].action", containsString("AUDIT")));
    }

    @Test
    void aNewUserCanJoinTheDemoCareTeam() throws Exception {
        String newcomer = "new.starter@example.test";
        mvc.perform(get("/api/v1/demo").with(as(newcomer, "COORDINATOR"))).andExpect(jsonPath("$.onDemoCareTeam").value(false));
        mvc.perform(post("/api/v1/demo/care-team").with(as(newcomer, "COORDINATOR")))
                .andExpect(jsonPath("$.patientsAdded", greaterThan(10)));
        mvc.perform(get("/api/v1/patients").with(as(newcomer, "COORDINATOR")))
                .andExpect(jsonPath("$.length()", greaterThan(10)));
    }
}
