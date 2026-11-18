-- Detail for the patient record, worklist, referral board, capacity, and model governance screens.
-- Flags use INT 0/1 rather than BIT so the same SQL runs on SQL Server and the H2 test database.

ALTER TABLE care_team_member ADD display_name NVARCHAR(200) NULL;

ALTER TABLE care_task ADD task_type VARCHAR(30) NULL;
ALTER TABLE care_task ADD reason NVARCHAR(200) NULL;
ALTER TABLE care_task ADD team NVARCHAR(100) NULL;

ALTER TABLE encounter ADD ward_code VARCHAR(20) NULL;
ALTER TABLE encounter ADD expected_discharge_at DATETIME2 NULL;
ALTER TABLE encounter ADD awaiting_transport INT NOT NULL DEFAULT 0;
CREATE INDEX ix_encounter_status_ward ON encounter (status, ward_code);

ALTER TABLE referral_case ADD source NVARCHAR(100) NULL;
ALTER TABLE referral_case ADD suggested_urgency VARCHAR(20) NULL;
ALTER TABLE referral_case ADD extracted_summary NVARCHAR(500) NULL;
ALTER TABLE referral_case ADD extracted_source NVARCHAR(200) NULL;
ALTER TABLE referral_case ADD booked_service NVARCHAR(100) NULL;
ALTER TABLE referral_case ADD booking_status VARCHAR(20) NULL;

ALTER TABLE emergency_access ADD review_outcome VARCHAR(20) NULL;
ALTER TABLE emergency_access ADD review_note NVARCHAR(500) NULL;

CREATE TABLE patient_alert (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    patient_id    VARCHAR(36)   NOT NULL REFERENCES patient (id),
    category      NVARCHAR(50)  NOT NULL,
    detail        NVARCHAR(100) NOT NULL,
    severity      VARCHAR(10)   NOT NULL,
    recorded_at   DATETIME2     NOT NULL
);
CREATE INDEX ix_alert_patient ON patient_alert (patient_id);

CREATE TABLE medication (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    patient_id    VARCHAR(36)   NOT NULL REFERENCES patient (id),
    name          NVARCHAR(100) NOT NULL,
    dose          NVARCHAR(100) NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    updated_at    DATETIME2     NOT NULL
);
CREATE INDEX ix_medication_patient ON medication (patient_id);

CREATE TABLE clinical_event (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    patient_id    VARCHAR(36)   NOT NULL REFERENCES patient (id),
    occurred_at   DATETIME2     NOT NULL,
    description   NVARCHAR(300) NOT NULL,
    source        NVARCHAR(50)  NOT NULL
);
CREATE INDEX ix_event_patient ON clinical_event (patient_id, occurred_at);

CREATE TABLE care_service (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    name          NVARCHAR(100) NOT NULL,
    specialty     NVARCHAR(100) NOT NULL,
    next_slot_at  DATETIME2     NULL,
    distance_km   INT           NOT NULL,
    rapid_access  INT           NOT NULL DEFAULT 0
);
CREATE INDEX ix_service_specialty ON care_service (specialty);

CREATE TABLE ward (
    code          VARCHAR(20)   NOT NULL PRIMARY KEY,
    name          NVARCHAR(100) NOT NULL,
    beds          INT           NOT NULL,
    display_order INT           NOT NULL
);

-- One row per ward per day: the midnight census, or the latest count for today.
CREATE TABLE bed_occupancy (
    ward_code     VARCHAR(20)   NOT NULL REFERENCES ward (code),
    census_date   DATE          NOT NULL,
    occupied      INT           NOT NULL,
    PRIMARY KEY (ward_code, census_date)
);

-- ward_code ALL holds the hospital total. Older as_of runs are kept, so accuracy can be measured.
CREATE TABLE capacity_forecast (
    ward_code     VARCHAR(20)   NOT NULL,
    as_of         DATE          NOT NULL,
    forecast_date DATE          NOT NULL,
    p10           DECIMAL(8,1)  NOT NULL,
    p50           DECIMAL(8,1)  NOT NULL,
    p90           DECIMAL(8,1)  NOT NULL,
    model_version VARCHAR(60)   NOT NULL,
    generated_at  DATETIME2     NOT NULL,
    PRIMARY KEY (ward_code, as_of, forecast_date)
);

CREATE TABLE staffing_gap (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    ward_code     VARCHAR(20)   NOT NULL REFERENCES ward (code),
    shift_date    DATE          NOT NULL,
    shift         VARCHAR(10)   NOT NULL,
    staff_group   NVARCHAR(50)  NOT NULL,
    gap_shifts    INT           NOT NULL
);

CREATE TABLE capacity_action (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    action        NVARCHAR(200) NOT NULL,
    effect        NVARCHAR(200) NOT NULL,
    owner         NVARCHAR(100) NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    planned_by    NVARCHAR(200) NULL,
    planned_at    DATETIME2     NULL,
    created_at    DATETIME2     NOT NULL
);

-- Written by the daily scoring job through the controlled API. Factors are JSON: label, impact, strength.
CREATE TABLE risk_score (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    patient_id    VARCHAR(36)   NOT NULL REFERENCES patient (id),
    model         VARCHAR(40)   NOT NULL,
    model_version VARCHAR(20)   NOT NULL,
    risk_level    VARCHAR(10)   NOT NULL,
    score         DECIMAL(5,3)  NOT NULL,
    factors       NVARCHAR(MAX) NOT NULL,
    scored_at     DATETIME2     NOT NULL
);
CREATE INDEX ix_risk_patient ON risk_score (patient_id, model, scored_at);

CREATE TABLE ai_feedback (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    feature       VARCHAR(40)   NOT NULL,
    model_version VARCHAR(60)   NOT NULL,
    patient_id    VARCHAR(36)   NULL REFERENCES patient (id),
    output_id     VARCHAR(36)   NULL,
    outcome       VARCHAR(20)   NOT NULL,
    reason        NVARCHAR(500) NULL,
    user_id       NVARCHAR(200) NOT NULL,
    created_at    DATETIME2     NOT NULL
);
CREATE INDEX ix_feedback_feature ON ai_feedback (feature, model_version);

-- Model registry as the clinical safety committee sees it. Only LIVE models are shown to care teams.
CREATE TABLE ml_model (
    model                 VARCHAR(40)   NOT NULL,
    version               VARCHAR(60)   NOT NULL,
    purpose               NVARCHAR(200) NOT NULL,
    clinical_status       VARCHAR(20)   NOT NULL,
    auroc                 DECIMAL(4,3)  NULL,
    calibration_error     DECIMAL(4,3)  NULL,
    min_auroc             DECIMAL(4,3)  NULL,
    max_calibration_error DECIMAL(4,3)  NULL,
    next_review           DATE          NULL,
    status_changed_by     NVARCHAR(200) NULL,
    status_changed_at     DATETIME2     NULL,
    status_reason         NVARCHAR(500) NULL,
    PRIMARY KEY (model, version)
);

CREATE TABLE ml_subgroup_metric (
    model             VARCHAR(40)   NOT NULL,
    version           VARCHAR(60)   NOT NULL,
    group_name        NVARCHAR(100) NOT NULL,
    display_order     INT           NOT NULL,
    auroc             DECIMAL(4,3)  NOT NULL,
    sensitivity       DECIMAL(4,3)  NOT NULL,
    calibration_error DECIMAL(4,3)  NOT NULL,
    PRIMARY KEY (model, version, group_name)
);

CREATE TABLE ml_evidence (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    model         VARCHAR(40)   NOT NULL,
    version       VARCHAR(60)   NOT NULL,
    item          NVARCHAR(100) NOT NULL,
    owner         NVARCHAR(100) NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    recorded_on   DATE          NOT NULL
);

CREATE TABLE ml_action (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    model         VARCHAR(40)   NOT NULL,
    version       VARCHAR(60)   NOT NULL,
    title         NVARCHAR(200) NOT NULL,
    detail        NVARCHAR(300) NULL,
    status        VARCHAR(20)   NOT NULL
);

CREATE TABLE interface_feed (
    code              VARCHAR(20)   NOT NULL PRIMARY KEY,
    name              NVARCHAR(100) NOT NULL,
    max_delay_minutes INT           NOT NULL,
    last_message_at   DATETIME2     NULL
);

-- One row per day the synthetic demo data was loaded. Used only when demo data is turned on.
CREATE TABLE demo_seed (
    load_date     DATE          NOT NULL PRIMARY KEY,
    loaded_at     DATETIME2     NOT NULL
);

INSERT INTO interface_feed (code, name, max_delay_minutes) VALUES
 ('EMR', 'Hospital EMR', 5),
 ('PATHOLOGY', 'Pathology', 5),
 ('GP', 'GP referrals', 5);

-- Features built into this release. Their measures are calculated from live data, not entered by hand.
INSERT INTO ml_model (model, version, purpose, clinical_status) VALUES
 ('cc-capacity', 'baseline-1', 'Bed demand by ward: 7-day forecast with an 80% range', 'LIVE'),
 ('cc-summary', 'extractive-v1', 'Discharge summary drafts with citations, for clinician editing and signing', 'LIVE');
