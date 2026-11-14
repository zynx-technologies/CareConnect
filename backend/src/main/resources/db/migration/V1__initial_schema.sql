CREATE TABLE patient (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    mrn           VARCHAR(40)   NOT NULL,
    family_name   NVARCHAR(100) NOT NULL,
    given_name    NVARCHAR(100) NOT NULL,
    birth_date    DATE          NULL,
    created_at    DATETIME2     NOT NULL,
    CONSTRAINT uq_patient_mrn UNIQUE (mrn)
);

CREATE TABLE encounter (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    patient_id    VARCHAR(36)   NOT NULL REFERENCES patient (id),
    visit_number  VARCHAR(40)   NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    admitted_at   DATETIME2     NULL,
    discharged_at DATETIME2     NULL,
    CONSTRAINT uq_encounter_visit UNIQUE (visit_number)
);

CREATE TABLE care_team_member (
    patient_id    VARCHAR(36)   NOT NULL REFERENCES patient (id),
    user_id       NVARCHAR(200) NOT NULL,
    team_role     NVARCHAR(50)  NOT NULL,
    PRIMARY KEY (patient_id, user_id)
);
CREATE INDEX ix_care_team_user ON care_team_member (user_id);

CREATE TABLE consent_policy (
    patient_id    VARCHAR(36)   NOT NULL REFERENCES patient (id),
    purpose       VARCHAR(30)   NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    updated_at    DATETIME2     NOT NULL,
    updated_by    NVARCHAR(200) NOT NULL,
    PRIMARY KEY (patient_id, purpose)
);

CREATE TABLE emergency_access (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    patient_id    VARCHAR(36)   NOT NULL REFERENCES patient (id),
    user_id       NVARCHAR(200) NOT NULL,
    reason        NVARCHAR(500) NOT NULL,
    granted_at    DATETIME2     NOT NULL,
    expires_at    DATETIME2     NOT NULL,
    reviewed_at   DATETIME2     NULL,
    reviewed_by   NVARCHAR(200) NULL
);
CREATE INDEX ix_emergency_user_patient ON emergency_access (user_id, patient_id);

CREATE TABLE clinical_note (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    patient_id    VARCHAR(36)   NOT NULL REFERENCES patient (id),
    note_type     NVARCHAR(50)  NOT NULL,
    author        NVARCHAR(200) NOT NULL,
    body          NVARCHAR(MAX) NOT NULL,
    written_at    DATETIME2     NOT NULL
);
CREATE INDEX ix_note_patient ON clinical_note (patient_id, written_at);

CREATE TABLE care_task (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    patient_id    VARCHAR(36)   NOT NULL REFERENCES patient (id),
    title         NVARCHAR(200) NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    due_at        DATETIME2     NOT NULL,
    assigned_to   NVARCHAR(200) NULL,
    created_at    DATETIME2     NOT NULL,
    completed_at  DATETIME2     NULL,
    completed_by  NVARCHAR(200) NULL
);
CREATE INDEX ix_task_status_due ON care_task (status, due_at);

CREATE TABLE referral_case (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    patient_id    VARCHAR(36)   NOT NULL REFERENCES patient (id),
    service       NVARCHAR(100) NOT NULL,
    reason        NVARCHAR(1000) NOT NULL,
    urgency       VARCHAR(20)   NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    created_at    DATETIME2     NOT NULL,
    created_by    NVARCHAR(200) NOT NULL,
    triaged_by    NVARCHAR(200) NULL,
    triage_reason NVARCHAR(500) NULL,
    appointment_at DATETIME2    NULL
);
CREATE INDEX ix_referral_status ON referral_case (status, created_at);

CREATE TABLE ai_draft (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    patient_id    VARCHAR(36)   NOT NULL REFERENCES patient (id),
    status        VARCHAR(20)   NOT NULL,
    content       NVARCHAR(MAX) NOT NULL,
    statements    NVARCHAR(MAX) NOT NULL,
    generator     NVARCHAR(100) NOT NULL,
    created_at    DATETIME2     NOT NULL,
    created_by    NVARCHAR(200) NOT NULL,
    signed_at     DATETIME2     NULL,
    signed_by     NVARCHAR(200) NULL,
    signed_note_id VARCHAR(36)  NULL REFERENCES clinical_note (id)
);
CREATE INDEX ix_draft_patient ON ai_draft (patient_id, created_at);

CREATE TABLE hl7_message (
    control_id    VARCHAR(100)  NOT NULL PRIMARY KEY,
    message_type  VARCHAR(20)   NULL,
    raw_message   NVARCHAR(MAX) NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    error         NVARCHAR(500) NULL,
    received_at   DATETIME2     NOT NULL
);

CREATE TABLE audit_event (
    id            VARCHAR(36)   NOT NULL PRIMARY KEY,
    occurred_at   DATETIME2     NOT NULL,
    user_id       NVARCHAR(200) NOT NULL,
    action        VARCHAR(60)   NOT NULL,
    patient_id    VARCHAR(36)   NULL,
    resource_id   VARCHAR(100)  NULL,
    outcome       VARCHAR(20)   NOT NULL,
    detail        NVARCHAR(500) NULL
);
CREATE INDEX ix_audit_patient ON audit_event (patient_id, occurred_at);
CREATE INDEX ix_audit_user ON audit_event (user_id, occurred_at);
