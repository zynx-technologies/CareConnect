-- SQL Server needs each CREATE VIEW in its own batch, so views are separated by GO.
-- Operational reporting model for Power BI, following the fact tables in section 6 of the design.
-- These views hold identifiable keys and stay in the clinical zone. The de-identified Gold marts in the
-- analytics platform replace them for research and model training.

CREATE VIEW rpt_fact_encounter AS
SELECT e.id AS encounter_id, e.patient_id, e.ward_code, e.status, e.admitted_at, e.discharged_at,
       e.expected_discharge_at,
       CASE WHEN e.admitted_at IS NOT NULL AND e.discharged_at IS NOT NULL
            THEN DATEDIFF(HOUR, e.admitted_at, e.discharged_at) / 24.0 END AS length_of_stay_days
FROM encounter e
GO

CREATE VIEW rpt_fact_referral AS
SELECT r.id AS referral_id, r.patient_id, r.service, r.source, r.urgency, r.suggested_urgency, r.status,
       r.created_at, r.appointment_at, r.booked_service,
       CASE WHEN r.appointment_at IS NOT NULL THEN DATEDIFF(DAY, r.created_at, r.appointment_at) END AS wait_to_appointment_days
FROM referral_case r
GO

CREATE VIEW rpt_fact_followup AS
SELECT t.id AS task_id, t.patient_id, t.created_at, t.due_at, t.completed_at, t.status,
       CASE WHEN t.completed_at IS NOT NULL AND t.completed_at <= t.due_at THEN 1 ELSE 0 END AS completed_on_time
FROM care_task t
WHERE t.task_type = 'GP_FOLLOW_UP'
GO

CREATE VIEW rpt_fact_bed_occupancy AS
SELECT o.ward_code, w.name AS ward_name, o.census_date, o.occupied, w.beds
FROM bed_occupancy o JOIN ward w ON w.code = o.ward_code
GO

CREATE VIEW rpt_fact_ai_output AS
SELECT CAST(d.id AS VARCHAR(36)) AS output_id, CAST('cc-summary' AS VARCHAR(40)) AS feature,
       CAST(d.generator AS NVARCHAR(100)) AS model_version, CAST(d.status AS VARCHAR(20)) AS review_result,
       d.created_at, d.patient_id
FROM ai_draft d
UNION ALL
SELECT CAST(f.id AS VARCHAR(36)), CAST(f.feature AS VARCHAR(40)), CAST(f.model_version AS NVARCHAR(100)),
       CAST(f.outcome AS VARCHAR(20)), f.created_at, f.patient_id
FROM ai_feedback f
GO
