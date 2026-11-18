package com.careconnect.demo;

import com.careconnect.analytics.CapacityService;
import com.careconnect.analytics.RiskService.Factor;
import com.careconnect.common.Db;
import com.careconnect.common.SiteClock;
import com.careconnect.draft.DraftChecker;
import com.careconnect.draft.DraftGenerator.SourceNote;
import com.careconnect.draft.ExtractiveDraftGenerator;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Loads synthetic people, wards, referrals, scores, and model evidence so every screen can be shown in dev and
 * locally. All names, results, and figures are fictional. It is rebuilt each day so dates stay current, and it
 * only ever touches rows whose IDs start with "demo-". Terraform refuses to turn it on in production.
 */
@Component
@ConditionalOnProperty(name = "careconnect.demo-data.enabled", havingValue = "true")
public class DemoDataLoader {

    public static final String ID_PREFIX = "demo-";
    static final String JOINED_ROLE = "Demo care team";
    static final List<String> DEMO_MODELS = List.of("cc-readmit", "cc-noshow");
    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    // Background patients use names apart from the mock-up patients, so no two people look alike.
    private static final String[] GIVEN = {"Jamie", "Charlie", "Frankie", "Harper", "Logan", "Emerson", "Finley", "Hayden",
            "Kendall", "Peyton", "Sasha", "Robin", "Dana", "Kai", "Noel", "Ari", "Bailey", "Cameron", "Devon", "Ellis",
            "Jesse", "Lane", "Marley", "Remy"};
    private static final String[] FAMILY = {"Example", "Placeholder", "Dummy", "Synth", "Specimen", "Pilot", "Prototype",
            "Sandbox", "Fixture", "Stub", "Proxy", "Replica"};
    private static final String[] TEAMS = {"Ward 4 East", "Ward 6 West", "Community team", "Outpatients"};

    private record Ward(String code, String name, int beds, int occupied, double drift, int order) {}

    private static final List<Ward> WARDS = List.of(
            new Ward("MED", "Medicine", 140, 136, 8, 1),
            new Ward("CARD", "Cardiology", 70, 66, 3, 2),
            new Ward("SURG", "Surgical", 110, 100, 0, 3),
            new Ward("REHAB", "Rehab", 60, 54, -2, 4),
            new Ward("PAED", "Paediatrics", 70, 56, 0, 5));

    private final JdbcClient jdbc;
    private final JdbcTemplate template;
    private final TransactionTemplate tx;
    private final SiteClock clock;
    private final CapacityService capacity;
    private final ObjectMapper json;
    private final List<String> careTeam;

    public DemoDataLoader(JdbcClient jdbc, JdbcTemplate template, TransactionTemplate tx, SiteClock clock,
                          CapacityService capacity, ObjectMapper json,
                          @Value("${careconnect.demo-data.care-team:}") List<String> careTeam) {
        this.jdbc = jdbc;
        this.template = template;
        this.tx = tx;
        this.clock = clock;
        this.capacity = capacity;
        this.json = json;
        this.careTeam = careTeam.stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    /** Runs at startup and every two minutes: reloads once a day, and keeps the simulated feeds current. */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(initialDelay = 120_000, fixedDelay = 120_000)
    public void ensureCurrent() {
        try {
            loadIfStale();
            heartbeat();
        } catch (RuntimeException e) {
            // Demo data must never stop the service starting.
            log.warn("Demo data was not loaded: {}", e.getClass().getSimpleName(), e);
        }
    }

    void loadIfStale() {
        LocalDate today = clock.today();
        boolean loaded = jdbc.sql("SELECT COUNT(*) FROM demo_seed WHERE load_date = ?").param(Date.valueOf(today))
                .query(Long.class).single() > 0;
        if (loaded) {
            return;
        }
        Set<String> members = new LinkedHashSet<>(careTeam);
        members.addAll(jdbc.sql("SELECT DISTINCT user_id FROM care_team_member WHERE patient_id LIKE 'demo-%' AND team_role = ?")
                .param(JOINED_ROLE).query(String.class).list());
        try {
            tx.executeWithoutResult(s -> {
                jdbc.sql("INSERT INTO demo_seed (load_date, loaded_at) VALUES (?, ?)").params(Date.valueOf(today), Db.now()).update();
                purge();
                new Generator(today, members).run();
            });
        } catch (DuplicateKeyException e) {
            return; // Another replica loaded it.
        }
        for (int back = 14; back >= 1; back--) {
            capacity.forecast(today.minusDays(back));
        }
        capacity.recordCensus(today);
        capacity.forecast(today);
        log.info("Synthetic demo data loaded for {}", today);
    }

    /** Simulates live interface traffic. GP referrals run 8 minutes behind, to show a delayed feed. */
    void heartbeat() {
        Timestamp now = Db.now();
        jdbc.sql("UPDATE interface_feed SET last_message_at = ? WHERE code IN ('EMR', 'PATHOLOGY')").param(now).update();
        jdbc.sql("UPDATE interface_feed SET last_message_at = ? WHERE code = 'GP'")
                .param(Timestamp.from(now.toInstant().minus(8, ChronoUnit.MINUTES))).update();
    }

    /** Adds the signed-in person to the demo care team, so they can see the demo patients. */
    public int join(String userId) {
        List<String> patients = jdbc.sql("""
                SELECT DISTINCT patient_id FROM care_team_member
                WHERE user_id = 'r.coordinator@example.test' AND patient_id LIKE 'demo-%'""").query(String.class).list();
        int added = 0;
        for (String patientId : patients) {
            added += jdbc.sql("""
                    INSERT INTO care_team_member (patient_id, user_id, team_role, display_name)
                    SELECT ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM care_team_member WHERE patient_id = ? AND user_id = ?)""")
                    .params(patientId, userId, JOINED_ROLE, userId, patientId, userId).update();
        }
        return added;
    }

    public boolean isMember(String userId) {
        return jdbc.sql("SELECT COUNT(*) FROM care_team_member WHERE user_id = ? AND patient_id LIKE 'demo-%'")
                .param(userId).query(Long.class).single() > 0;
    }

    private void purge() {
        for (String table : List.of("ai_feedback", "ai_draft", "risk_score", "medication", "patient_alert", "clinical_event",
                "emergency_access", "care_task", "referral_case", "clinical_note", "consent_policy", "care_team_member", "encounter")) {
            jdbc.sql("DELETE FROM " + table + " WHERE patient_id LIKE 'demo-%'").update();
        }
        jdbc.sql("DELETE FROM patient WHERE id LIKE 'demo-%'").update();
        for (String table : List.of("staffing_gap", "capacity_action", "capacity_forecast", "bed_occupancy", "ward", "care_service")) {
            jdbc.sql("DELETE FROM " + table).update();
        }
        for (String table : List.of("ml_subgroup_metric", "ml_evidence", "ml_action", "ml_model")) {
            jdbc.sql("DELETE FROM " + table + " WHERE model IN (:models)").param("models", DEMO_MODELS).update();
        }
    }

    /** Builds one day's data. Fixed random seed, so each day's data has the same shape. */
    private final class Generator {

        private final LocalDate today;
        private final Set<String> members;
        private final Instant now = Instant.now();
        private final Random rnd = new Random(20261101L);
        private final Map<String, List<Object[]>> batches = new LinkedHashMap<>();
        private final List<String> bulkPatients = new ArrayList<>();
        private final List<String> panel = new ArrayList<>();
        private int counter;

        Generator(LocalDate today, Set<String> members) {
            this.today = today;
            this.members = members;
        }

        void run() {
            wards();
            featured();
            inpatients();
            referrals();
            models();
            capacityDetail();
            flush();
        }

        // ---------- helpers ----------

        private void add(String sql, Object... args) {
            batches.computeIfAbsent(sql, k -> new ArrayList<>()).add(args);
        }

        private void flush() {
            batches.forEach(template::batchUpdate);
        }

        private String id(String kind) {
            return ID_PREFIX + kind + "-" + (++counter);
        }

        private Timestamp at(LocalDate day, int hour, int minute) {
            return Timestamp.from(clock.at(day, hour, minute));
        }

        private LocalDate day(int offset) {
            return today.plusDays(offset);
        }

        private void patient(String id, String mrn, String given, String family, LocalDate born) {
            add("INSERT INTO patient (id, mrn, family_name, given_name, birth_date, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                    id, mrn, family, given, Date.valueOf(born), Db.now());
            consent(id, "CARE", "ACTIVE");
        }

        private void consent(String patientId, String purpose, String status) {
            add("INSERT INTO consent_policy (patient_id, purpose, status, updated_at, updated_by) VALUES (?, ?, ?, ?, 'demo-loader')",
                    patientId, purpose, status, Db.now());
        }

        private void encounter(String patientId, String status, String ward, Timestamp admitted, Timestamp discharged,
                               Timestamp expected, boolean transport) {
            add("""
                    INSERT INTO encounter (id, patient_id, visit_number, status, admitted_at, discharged_at, ward_code,
                                           expected_discharge_at, awaiting_transport) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    id("e"), patientId, "DV" + counter, status, admitted, discharged, ward, expected, transport ? 1 : 0);
        }

        private void member(String patientId, String userId, String role, String name) {
            add("INSERT INTO care_team_member (patient_id, user_id, team_role, display_name) VALUES (?, ?, ?, ?)",
                    patientId, userId, role, name);
        }

        private void careTeam(String patientId, boolean withCardiologist) {
            member(patientId, "r.coordinator@example.test", "Care coordinator", "R. Coordinator");
            member(patientId, "p.example@example.test", withCardiologist ? "Cardiologist" : "Physician", "Dr P. Example");
            for (String user : members) {
                if (!user.equals("r.coordinator@example.test") && !user.equals("p.example@example.test")) {
                    member(patientId, user, JOINED_ROLE, user);
                }
            }
        }

        private void task(String patientId, String title, String reason, String team, Timestamp due, String type,
                          Timestamp completed) {
            add("""
                    INSERT INTO care_task (id, patient_id, title, status, due_at, assigned_to, created_at, completed_at,
                                           completed_by, task_type, reason, team) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    id("t"), patientId, title, completed == null ? "OPEN" : "DONE", due, "r.coordinator@example.test",
                    Timestamp.from(due.toInstant().minus(3, ChronoUnit.DAYS)), completed,
                    completed == null ? null : "r.coordinator@example.test", type, reason, team);
        }

        private void event(String patientId, int dayOffset, String description, String source) {
            add("INSERT INTO clinical_event (id, patient_id, occurred_at, description, source) VALUES (?, ?, ?, ?, ?)",
                    id("ev"), patientId, at(day(dayOffset), 10, 0), description, source);
        }

        private void alert(String patientId, String category, String detail, String severity) {
            add("INSERT INTO patient_alert (id, patient_id, category, detail, severity, recorded_at) VALUES (?, ?, ?, ?, ?, ?)",
                    id("a"), patientId, category, detail, severity, Db.now());
        }

        private void medicine(String patientId, String name, String dose, String status) {
            add("INSERT INTO medication (id, patient_id, name, dose, status, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                    id("m"), patientId, name, dose, status, Db.now());
        }

        private void note(String noteId, String patientId, String type, String author, String body, Timestamp written) {
            add("INSERT INTO clinical_note (id, patient_id, note_type, author, body, written_at) VALUES (?, ?, ?, ?, ?, ?)",
                    noteId, patientId, type, author, body, written);
        }

        private void risk(String patientId, String model, String version, String level, String score, Factor... factors) {
            add("""
                    INSERT INTO risk_score (id, patient_id, model, model_version, risk_level, score, factors, scored_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                    id("r"), patientId, model, version, level, new BigDecimal(score), json.writeValueAsString(List.of(factors)),
                    at(today, 2, 0));
        }

        // ---------- content ----------

        private void wards() {
            for (Ward w : WARDS) {
                add("INSERT INTO ward (code, name, beds, display_order) VALUES (?, ?, ?, ?)", w.code(), w.name(), w.beds(), w.order());
            }
            for (Ward w : WARDS) {
                for (int back = 60; back >= 1; back--) {
                    LocalDate d = today.minusDays(back);
                    double base = w.occupied() - w.drift() * back / 60.0;
                    boolean weekend = d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
                    double weekly = weekend ? ("SURG".equals(w.code()) ? -6 : -2) : 0;
                    int occupied = (int) Math.round(Math.min(w.beds(), base + weekly + rnd.nextInt(5) - 2));
                    add("INSERT INTO bed_occupancy (ward_code, census_date, occupied) VALUES (?, ?, ?)", w.code(), Date.valueOf(d), occupied);
                }
            }
        }

        private void featured() {
            // Alex Test: discharged yesterday after a heart failure admission.
            String alex = "demo-p-90021";
            patient(alex, "90021", "Alex", "Test", LocalDate.of(today.getYear() - 68, 3, 12));
            consent(alex, "GP_SHARING", "ACTIVE");
            consent(alex, "COMMUNITY_SHARING", "ACTIVE");
            consent(alex, "RESEARCH", "DECLINED");
            careTeam(alex, true);
            member(alex, "l.sample@example.test", "GP", "Dr L. Sample");
            encounter(alex, "DISCHARGED", "CARD", at(day(-120), 14, 0), at(day(-114), 11, 0), null, false);
            encounter(alex, "DISCHARGED", "CARD", at(day(-5), 8, 30), at(day(-1), 11, 0), null, false);
            alert(alex, "Allergy", "Penicillin", "HIGH");
            alert(alex, "Falls risk", "Moderate", "MEDIUM");
            event(alex, -2, "Echocardiogram reported", "Imaging");
            event(alex, -3, "BNP 780 pg/mL (high)", "Pathology");
            event(alex, -67, "Cardiology clinic visit", "Outpatients");
            event(alex, -107, "GP medication review", "GP referral");
            medicine(alex, "Furosemide", "40 mg daily", "CHANGED");
            medicine(alex, "Bisoprolol", "2.5 mg daily", "CONTINUED");
            medicine(alex, "Perindopril", "5 mg daily", "CONTINUED");
            note(id("n"), alex, "Progress note", "Dr P. Example",
                    "Admitted through emergency with fluid overload. Responding to IV diuretics. Weight down 3 kg.", at(day(-4), 9, 0));
            note(id("n"), alex, "Discharge summary", "Dr P. Example",
                    "Admitted with decompensated heart failure. Furosemide increased to 40 mg daily. GP review within 7 days, "
                            + "heart failure nurse call in 2 days, home care assessment referred.", at(day(-1), 10, 30));
            task(alex, "Book GP follow-up", "Discharged yesterday", "Ward 4 East", at(today, 11, 0), "GP_FOLLOW_UP", null);
            task(alex, "Heart failure nurse call", "Due in 2 days", "Community team", at(day(2), 10, 0), null, null);
            task(alex, "Home care assessment", "Referred", "Community team", at(day(3), 9, 0), null, null);
            risk(alex, "cc-readmit", "v6", "HIGH", "0.410", new Factor("Two admissions in 6 months", "Major", 0.8),
                    new Factor("Lives alone", "Moderate", 0.5), new Factor("Eight or more medicines", "Moderate", 0.45));

            // Sam Sample: going home tomorrow.
            String sam = "demo-p-90107";
            patient(sam, "90107", "Sam", "Sample", LocalDate.of(today.getYear() - 79, 11, 2));
            consent(sam, "GP_SHARING", "ACTIVE");
            careTeam(sam, false);
            encounter(sam, "ADMITTED", "MED", at(day(-6), 16, 0), null, at(day(1), 11, 0), false);
            alert(sam, "Falls risk", "High", "HIGH");
            event(sam, -6, "Chest X-ray: small bilateral effusions", "Imaging");
            medicine(sam, "Apixaban", "5 mg twice daily", "NEW");
            medicine(sam, "Metoprolol", "25 mg twice daily", "CONTINUED");
            task(sam, "Confirm home care", "Discharge tomorrow", "Ward 6 West", at(today, 14, 0), null, null);
            task(sam, "Book GP follow-up", "Discharge tomorrow", "Ward 6 West", at(day(8), 11, 0), "GP_FOLLOW_UP", null);
            risk(sam, "cc-readmit", "v6", "HIGH", "0.380", new Factor("Admitted in the last 30 days", "Major", 0.75),
                    new Factor("Aged over 75", "Moderate", 0.5), new Factor("Heart failure diagnosis", "Moderate", 0.45));

            // Jordan Demo: urgent-looking cardiology referral, not yet scored.
            String jordan = "demo-p-90143";
            patient(jordan, "90143", "Jordan", "Demo", LocalDate.of(today.getYear() - 55, 5, 20));
            careTeam(jordan, true);
            task(jordan, "Triage cardiology referral", "Waiting 2 days", "Outpatients", at(today, 15, 0), null, null);

            // Riley Mock: missed clinic visits.
            String riley = "demo-p-90188";
            patient(riley, "90188", "Riley", "Mock", LocalDate.of(today.getYear() - 43, 9, 14));
            careTeam(riley, false);
            event(riley, -30, "Did not attend endocrinology clinic", "Outpatients");
            medicine(riley, "Metformin", "1 g twice daily", "CONTINUED");
            task(riley, "Reminder for clinic visit", "Missed-visit risk", "Outpatients", at(today, 16, 0), null, null);
            risk(riley, "cc-readmit", "v6", "MEDIUM", "0.220", new Factor("Two missed clinic visits this year", "Moderate", 0.5),
                    new Factor("Type 2 diabetes", "Minor", 0.3));
            // Silent-trial model: stored, but never shown to care teams.
            risk(riley, "cc-noshow", "v3", "HIGH", "0.610", new Factor("Two missed clinic visits this year", "Major", 0.8));

            // Casey Trial: pneumonia, discharge summary drafted and waiting for a clinician.
            String casey = "demo-p-90212";
            patient(casey, "90212", "Casey", "Trial", LocalDate.of(today.getYear() - 64, 6, 25));
            consent(casey, "GP_SHARING", "ACTIVE");
            careTeam(casey, false);
            encounter(casey, "ADMITTED", "MED", at(day(-5), 14, 0), null, at(day(1), 14, 0), false);
            medicine(casey, "Doxycycline", "100 mg daily, 5 more days", "NEW");
            medicine(casey, "Metformin", "500 mg twice daily", "CHANGED");
            event(casey, -5, "Chest X-ray: right lower lobe consolidation", "Imaging");
            List<SourceNote> notes = List.of(
                    new SourceNote(id("n"), "Progress note", "Pneumonia improving. Afebrile for 48 hours. Oxygen weaned yesterday. "
                            + "IV ceftriaxone stopped; oral doxycycline started, 5 more days. Mobilising with a frame."),
                    new SourceNote(id("n"), "Pharmacy note", "Metformin withheld during admission, restart on discharge. "
                            + "Reviewed with patient. Has dosette box at home."),
                    new SourceNote(id("n"), "Physio note", "Safe on stairs with supervision. Recommend home safety assessment "
                            + "before or soon after discharge."),
                    new SourceNote(id("n"), "Pathology", "CRP 18 mg/L, falling from 142 mg/L. Follow-up chest X-ray in 6 weeks."));
            String[] authors = {"Dr A. Mock", "M. Fake", "T. Sample", "Lab"};
            for (int i = 0; i < notes.size(); i++) {
                note(notes.get(i).id(), casey, notes.get(i).noteType(), authors[i], notes.get(i).body(), at(day(i == 0 ? -1 : 0), 9 + i, 0));
            }
            var checked = DraftChecker.check(new ExtractiveDraftGenerator().generate(notes), notes);
            add("""
                    INSERT INTO ai_draft (id, patient_id, status, content, statements, generator, created_at, created_by)
                    VALUES (?, ?, 'DRAFT', ?, ?, ?, ?, 'demo-loader')""",
                    id("d"), casey, checked.stream().map(s -> s.section() + ": " + s.text()).collect(Collectors.joining("\n")),
                    json.writeValueAsString(checked), "extractive-v1", at(today, 12, 30));
            task(casey, "Review draft summary", "Awaiting clinician", "Ward 6 West", at(day(1), 10, 0), null, null);
            task(casey, "Book GP follow-up", "Discharge tomorrow", "Ward 6 West", at(day(8), 11, 0), "GP_FOLLOW_UP", null);
            risk(casey, "cc-readmit", "v6", "MEDIUM", "0.190", new Factor("Pneumonia admission", "Moderate", 0.45),
                    new Factor("Type 2 diabetes", "Minor", 0.3));

            // Morgan Fake: needs an interpreter for clinic.
            String morgan = "demo-p-90230";
            patient(morgan, "90230", "Morgan", "Fake", LocalDate.of(today.getYear() - 36, 2, 8));
            careTeam(morgan, false);
            alert(morgan, "Interpreter", "Needed: Vietnamese", "MEDIUM");
            task(morgan, "Arrange interpreter", "Clinic on Thursday", "Community team", at(day(1), 12, 0), null, null);
            risk(morgan, "cc-readmit", "v6", "LOW", "0.060", new Factor("No admissions in 12 months", "Lowers risk", 0.2));
        }

        private void inpatients() {
            int index = 0;
            List<Object[]> admitted = new ArrayList<>();
            for (Ward w : WARDS) {
                int needed = w.occupied() - ("MED".equals(w.code()) ? 2 : 0); // Sam and Casey are on Medicine.
                for (int i = 0; i < needed; i++) {
                    String id = "demo-p-" + (91000 + index);
                    String given = GIVEN[index % GIVEN.length];
                    String family = FAMILY[(index / GIVEN.length + index) % FAMILY.length];
                    int age = "PAED".equals(w.code()) ? 1 + rnd.nextInt(16) : 25 + rnd.nextInt(70);
                    patient(id, String.valueOf(91000 + index), given, family, LocalDate.of(today.getYear() - age, 1 + rnd.nextInt(12), 1 + rnd.nextInt(28)));
                    bulkPatients.add(id);
                    admitted.add(new Object[] {id, w.code()});
                    index++;
                }
            }
            // Spread expected discharges: 47 today (11 waiting for transport), the rest over the next 8 days.
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < admitted.size(); i++) order.add(i);
            java.util.Collections.shuffle(order, rnd);
            for (int k = 0; k < order.size(); k++) {
                Object[] a = admitted.get(order.get(k));
                String id = (String) a[0];
                int offset = k < 47 ? 0 : 1 + rnd.nextInt(8);
                Timestamp expected = at(day(offset), 9 + rnd.nextInt(8), 0);
                encounter(id, "ADMITTED", (String) a[1], at(day(-1 - rnd.nextInt(10)), 8 + rnd.nextInt(10), 0), null,
                        expected, k < 11);
                if (offset <= 2 && rnd.nextInt(4) != 0) {
                    task(id, "Book GP follow-up", "Discharge planned", "Ward 4 East",
                            Timestamp.from(expected.toInstant().plus(7, ChronoUnit.DAYS)), "GP_FOLLOW_UP", null);
                }
            }
            // A coordinator's panel: ten Medicine inpatients with everyday tasks.
            String[] titles = {"Medication reconciliation", "Arrange transport home", "Confirm community nursing",
                    "Book outpatient review", "Update care plan", "Check pathology result", "Organise equipment for home",
                    "Call family about discharge"};
            String[] reasons = {"Discharge planning", "Family request", "New medicine", "Result pending", "Care plan review"};
            for (int i = 0; i < 10; i++) {
                String id = bulkPatients.get(i);
                panel.add(id);
                careTeam(id, false);
            }
            int[] teamWeights = {0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 0, 1, 2, 1, 0, 3};
            for (int i = 0; i < 28; i++) {
                String id = panel.get(i % panel.size());
                int offset = i < 6 ? -1 - (i % 2) : rnd.nextInt(4);
                task(id, titles[i % titles.length], reasons[i % reasons.length], TEAMS[teamWeights[i]],
                        at(day(offset), 9 + rnd.nextInt(8), 0), null, null);
                if (i % 3 == 0) {
                    risk(id, "cc-readmit", "v6", i % 2 == 0 ? "MEDIUM" : "LOW", i % 2 == 0 ? "0.180" : "0.070",
                            new Factor("Length of stay over 7 days", "Moderate", 0.4));
                }
            }
            // Follow-ups due in the last 30 days: 42 of 50 done on time.
            for (int i = 0; i < 50; i++) {
                String id = bulkPatients.get(20 + i);
                Timestamp due = at(day(-1 - rnd.nextInt(28)), 12, 0);
                Timestamp done = Timestamp.from(due.toInstant().plus(i < 42 ? -1 - rnd.nextInt(72) : 6 + rnd.nextInt(48), ChronoUnit.HOURS));
                task(id, "Book GP follow-up within 7 days of discharge", "Discharged", "Ward 4 East", due, "GP_FOLLOW_UP", done);
            }
            // Clinician feedback on the readmission model: 68% accepted, 27% dismissed, 5% reported.
            String[] dismissals = {"Community support already in place", "Recent social work review covers this",
                    "Score driven by an old admission"};
            for (int i = 0; i < 120; i++) {
                String outcome = i < 82 ? "ACCEPTED" : i < 114 ? "DISMISSED" : "REPORTED";
                String reason = "ACCEPTED".equals(outcome) ? null
                        : "REPORTED".equals(outcome) ? "Admission count includes a day procedure" : dismissals[i % 3];
                add("""
                        INSERT INTO ai_feedback (id, feature, model_version, patient_id, output_id, outcome, reason, user_id, created_at)
                        VALUES (?, 'cc-readmit', 'v6', ?, NULL, ?, ?, ?, ?)""",
                        id("f"), bulkPatients.get(100 + i), outcome, reason, "clinician" + (i % 9) + "@example.test",
                        at(day(-1 - rnd.nextInt(80)), 10, 0));
            }
            // Emergency access waiting for privacy review, and one already reviewed.
            add("INSERT INTO emergency_access (id, patient_id, user_id, reason, granted_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                    id("x"), bulkPatients.get(300), "locum.doctor@example.test",
                    "Patient deteriorating in ED and the treating team was unavailable", at(day(-1), 2, 10), at(day(-1), 6, 10));
            add("INSERT INTO emergency_access (id, patient_id, user_id, reason, granted_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                    id("x"), bulkPatients.get(301), "ward.nurse@example.test",
                    "Covering night shift; patient transferred without a handover", at(day(-3), 23, 40), at(day(-2), 3, 40));
            add("""
                    INSERT INTO emergency_access (id, patient_id, user_id, reason, granted_at, expires_at, reviewed_at, reviewed_by,
                                                  review_outcome, review_note) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    id("x"), bulkPatients.get(302), "anaesthetist@example.test", "Unconscious patient in theatre recovery",
                    at(day(-9), 14, 0), at(day(-9), 18, 0), at(day(-8), 10, 0), "privacy.officer@example.test",
                    "APPROPRIATE", "Clinical emergency confirmed with the treating team");
        }

        private void referrals() {
            String sql = """
                    INSERT INTO referral_case (id, patient_id, service, reason, urgency, status, created_at, created_by, source,
                                               suggested_urgency, extracted_summary, extracted_source, triaged_by, triage_reason,
                                               appointment_at, booked_service, booking_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'demo-loader', ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
            // Named referrals from the design mock-ups.
            add(sql, id("ref"), "demo-p-90143", "Cardiology",
                    "Synthetic referral: chest pain on exertion for three weeks. ECG at the practice shows T-wave changes. Please assess.",
                    "UNTRIAGED", "NEW", at(day(-2), 9, 0), "GP referral", "URGENT",
                    "Referral text mentions chest pain on exertion and an abnormal ECG.", "GP letter, page 1", null, null, null, null, null);
            Object[][] named = {
                    {"90301", "Taylor", "Test", "Orthopaedics", "GP referral", "NEW", "ROUTINE", null, null},
                    {"90302", "Drew", "Sample", "Respiratory", "ED", "NEW", "SEMI_URGENT", null, null},
                    {"90303", "Avery", "Mock", "Diabetes education", "GP referral", "NEW", "ROUTINE", null, null},
                    {"90304", "Quinn", "Trial", "Neurology", "GP referral", "TRIAGED", "SEMI_URGENT", null, null},
                    {"90305", "Reese", "Demo", "Cardiac rehab", "Ward", "TRIAGED", "ROUTINE", null, null},
                    {"90306", "Parker", "Fake", "Renal", "GP referral", "TRIAGED", "URGENT", null, null},
                    {"90307", "Skyler", "Test", "Respiratory", "ED", "BOOKED", "SEMI_URGENT", 12, "CONFIRMED"},
                    {"90308", "Rowan", "Sample", "Orthopaedics", "GP referral", "BOOKED", "ROUTINE", 19, "CONFIRMED"},
                    {"90309", "Blake", "Mock", "Pain clinic", "GP referral", "BOOKED", "ROUTINE", 25, "REMINDER_SENT"}};
            for (Object[] r : named) {
                String id = "demo-p-" + r[0];
                patient(id, (String) r[0], (String) r[1], (String) r[2], LocalDate.of(today.getYear() - 30 - rnd.nextInt(45), 4, 9));
                referral(sql, id, (String) r[3], (String) r[4], (String) r[5], (String) r[6], (Integer) r[7], (String) r[8], 1);
            }
            // Unnamed referrals fill the board: 14 new, 9 triaged, 21 booked in total.
            String[] services = {"Cardiology", "Orthopaedics", "Respiratory", "Neurology", "Renal", "Diabetes education", "Pain clinic"};
            String[] urgencies = {"ROUTINE", "ROUTINE", "SEMI_URGENT", "URGENT"};
            int p = 200;
            for (int i = 0; i < 10; i++) referral(sql, bulkPatients.get(p++), services[i % 7], "GP referral", "NEW", urgencies[i % 4], null, null, 0);
            for (int i = 0; i < 6; i++) referral(sql, bulkPatients.get(p++), services[(i + 2) % 7], "GP referral", "TRIAGED", urgencies[i % 4], null, null, 3);
            for (int i = 0; i < 18; i++) {
                referral(sql, bulkPatients.get(p++), services[(i + 4) % 7], i % 3 == 0 ? "ED" : "GP referral", "BOOKED",
                        urgencies[i % 4], 1 + rnd.nextInt(30), i % 4 == 0 ? "REMINDER_SENT" : "CONFIRMED", 5);
            }
            Object[][] catalogue = {
                    {"Rapid-access chest pain clinic", "Cardiology", 3, 6, 1}, {"Cardiology outpatients", "Cardiology", 31, 6, 0},
                    {"Community cardiology", "Cardiology", 17, 14, 0}, {"Orthopaedic outpatients", "Orthopaedics", 26, 6, 0},
                    {"Fracture clinic", "Orthopaedics", 4, 6, 1}, {"Respiratory clinic", "Respiratory", 9, 6, 0},
                    {"Community respiratory team", "Respiratory", 6, 18, 0}, {"Neurology outpatients", "Neurology", 35, 22, 0},
                    {"First seizure clinic", "Neurology", 7, 22, 1}, {"Renal outpatients", "Renal", 21, 6, 0},
                    {"Diabetes education group", "Diabetes education", 12, 9, 0}, {"Persistent pain program", "Pain clinic", 40, 11, 0},
                    {"Cardiac rehab, hospital gym", "Cardiac rehab", 8, 6, 0}, {"Cardiac rehab at home", "Cardiac rehab", 5, 0, 0}};
            for (Object[] s : catalogue) {
                add("INSERT INTO care_service (id, name, specialty, next_slot_at, distance_km, rapid_access) VALUES (?, ?, ?, ?, ?, ?)",
                        id("s"), s[0], s[1], at(day((Integer) s[2]), 9, 30), s[3], s[4]);
            }
        }

        private void referral(String sql, String patientId, String service, String source, String status, String urgency,
                              Integer appointmentDays, String bookingStatus, int minAgeDays) {
            boolean triaged = !"NEW".equals(status);
            add(sql, id("ref"), patientId, service, "Synthetic referral for " + service.toLowerCase() + " assessment.",
                    triaged ? urgency : "UNTRIAGED", status, at(day(-minAgeDays - rnd.nextInt(2)), 8 + rnd.nextInt(9), 0), source,
                    urgency, null, null, triaged ? "triage@example.test" : null, triaged ? "Triaged on referral content" : null,
                    appointmentDays == null ? null : at(day(appointmentDays), 10, 0),
                    appointmentDays == null ? null : service + " clinic", bookingStatus);
        }

        private void models() {
            add("""
                    INSERT INTO ml_model (model, version, purpose, clinical_status, auroc, calibration_error, min_auroc,
                                          max_calibration_error, next_review) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    "cc-readmit", "v6", "30-day readmission risk: low, medium, or high with main factors, for care planning",
                    "LIVE", new BigDecimal("0.76"), new BigDecimal("0.03"), new BigDecimal("0.72"), new BigDecimal("0.05"),
                    Date.valueOf(day(55)));
            add("""
                    INSERT INTO ml_model (model, version, purpose, clinical_status, auroc, calibration_error, min_auroc,
                                          max_calibration_error, next_review) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    "cc-noshow", "v3", "Missed-appointment risk, for extra reminders and support. Never used to lower priority or overbook.",
                    "SILENT_TRIAL", new BigDecimal("0.71"), new BigDecimal("0.04"), new BigDecimal("0.70"), new BigDecimal("0.05"),
                    Date.valueOf(day(40)));
            Object[][] readmit = {{"All patients", "0.76", "0.71", "0.03"}, {"Aged 65 or over", "0.74", "0.73", "0.03"},
                    {"Aboriginal and Torres Strait Islander", "0.71", "0.64", "0.06"}, {"Rural residence", "0.73", "0.67", "0.04"},
                    {"Language other than English", "0.74", "0.69", "0.04"}, {"Women", "0.77", "0.72", "0.03"}, {"Men", "0.75", "0.70", "0.03"}};
            Object[][] noshow = {{"All patients", "0.71", "0.62", "0.04"}, {"Aged 65 or over", "0.70", "0.60", "0.04"},
                    {"Aboriginal and Torres Strait Islander", "0.69", "0.58", "0.05"}, {"Rural residence", "0.70", "0.61", "0.04"}};
            subgroups("cc-readmit", "v6", readmit);
            subgroups("cc-noshow", "v3", noshow);
            String ev = "INSERT INTO ml_evidence (id, model, version, item, owner, status, recorded_on) VALUES (?, ?, ?, ?, ?, ?, ?)";
            add(ev, id("me"), "cc-readmit", "v6", "Clinical safety case", "Clinical Safety Officer", "APPROVED", Date.valueOf(day(-77)));
            add(ev, id("me"), "cc-readmit", "v6", "Privacy impact assessment", "Privacy Officer", "APPROVED", Date.valueOf(day(-90)));
            add(ev, id("me"), "cc-readmit", "v6", "Regulatory classification", "Regulatory lead", "RECORDED", Date.valueOf(day(-95)));
            add(ev, id("me"), "cc-readmit", "v6", "Model card and lineage", "ML engineering", "CURRENT", Date.valueOf(day(-77)));
            add(ev, id("me"), "cc-readmit", "v6", "Silent trial report (8 weeks)", "Clinical safety committee", "APPROVED", Date.valueOf(day(-80)));
            add(ev, id("me"), "cc-noshow", "v3", "Silent trial report", "Clinical safety committee", "IN_PROGRESS", Date.valueOf(day(-20)));
            add(ev, id("me"), "cc-noshow", "v3", "Model card and lineage", "ML engineering", "CURRENT", Date.valueOf(day(-34)));
            String act = "INSERT INTO ml_action (id, model, version, title, detail, status) VALUES (?, ?, ?, ?, ?, 'OPEN')";
            add(act, id("ma"), "cc-readmit", "v6", "Review subgroup gap", "with the safety committee and community advisers");
            add(act, id("ma"), "cc-readmit", "v6", "Check missing rural GP data", "in the feature set");
            add(act, id("ma"), "cc-readmit", "v6", "Investigate lab-code drift", "new local codes for BNP since last month");
            add(act, id("ma"), "cc-noshow", "v3", "Complete the silent trial", "before the safety committee decides on release");
        }

        private void subgroups(String model, String version, Object[][] rows) {
            for (int i = 0; i < rows.length; i++) {
                add("""
                        INSERT INTO ml_subgroup_metric (model, version, group_name, display_order, auroc, sensitivity, calibration_error)
                        VALUES (?, ?, ?, ?, ?, ?, ?)""",
                        model, version, rows[i][0], i, new BigDecimal((String) rows[i][1]), new BigDecimal((String) rows[i][2]),
                        new BigDecimal((String) rows[i][3]));
            }
        }

        private void capacityDetail() {
            for (int d = 0; d < 3; d++) {
                add("INSERT INTO staffing_gap (id, ward_code, shift_date, shift, staff_group, gap_shifts) VALUES (?, ?, ?, ?, ?, ?)",
                        id("g"), "MED", Date.valueOf(day(d)), "NIGHT", "Registered nurses", 3);
            }
            String sql = "INSERT INTO capacity_action (id, action, effect, owner, status, created_at) VALUES (?, ?, ?, ?, 'OPEN', ?)";
            add(sql, id("ca"), "Open 8 surge beds, Medicine", "Keeps peak below 95%", "Bed manager", Timestamp.from(now));
            add(sql, id("ca"), "Add 2 weekend discharge rounds", "About 6 earlier discharges", "Medical lead", Timestamp.from(now.plusSeconds(1)));
            add(sql, id("ca"), "Request 3 night-shift agency nurses", "Closes one-third of the gap", "Nursing manager", Timestamp.from(now.plusSeconds(2)));
        }
    }
}
