-- Synthetic people for local development. This migration location is never enabled outside the local profile.
INSERT INTO patient (id, mrn, family_name, given_name, birth_date, created_at) VALUES
 ('p-1', 'MRN90021', 'Test', 'Alex', '1958-03-12', CURRENT_TIMESTAMP),
 ('p-2', 'MRN90107', 'Sample', 'Sam', '1947-11-02', CURRENT_TIMESTAMP),
 ('p-3', 'MRN90212', 'Trial', 'Casey', '1962-06-25', CURRENT_TIMESTAMP);

INSERT INTO encounter (id, patient_id, visit_number, status, admitted_at, discharged_at) VALUES
 ('e-1', 'p-1', 'V1001', 'DISCHARGED', '2026-10-26 08:00:00', '2026-10-30 11:00:00'),
 ('e-3', 'p-3', 'V1003', 'ADMITTED', '2026-10-25 14:00:00', NULL);

INSERT INTO care_team_member (patient_id, user_id, team_role) VALUES
 ('p-1', 'r.coordinator@example.test', 'Care coordinator'),
 ('p-3', 'r.coordinator@example.test', 'Care coordinator'),
 ('p-1', 'p.example@example.test', 'Cardiologist'),
 ('p-3', 'p.example@example.test', 'Physician');

INSERT INTO consent_policy (patient_id, purpose, status, updated_at, updated_by) VALUES
 ('p-1', 'CARE', 'ACTIVE', CURRENT_TIMESTAMP, 'seed'),
 ('p-2', 'CARE', 'ACTIVE', CURRENT_TIMESTAMP, 'seed'),
 ('p-3', 'CARE', 'ACTIVE', CURRENT_TIMESTAMP, 'seed');

INSERT INTO clinical_note (id, patient_id, note_type, author, body, written_at) VALUES
 ('n-1', 'p-3', 'Progress note', 'Dr A. Mock', 'Pneumonia improving. Afebrile for 48 hours. Oral doxycycline started, 5 more days.', '2026-10-29 09:00:00'),
 ('n-2', 'p-3', 'Pharmacy note', 'M. Fake', 'Metformin withheld during admission, restart on discharge.', '2026-10-30 10:00:00'),
 ('n-3', 'p-3', 'Physio note', 'T. Sample', 'Safe on stairs with supervision. Recommend home safety assessment.', '2026-10-30 11:00:00');

INSERT INTO care_task (id, patient_id, title, status, due_at, assigned_to, created_at) VALUES
 ('t-1', 'p-1', 'Book GP follow-up within 7 days', 'OPEN', '2026-11-06 11:00:00', 'r.coordinator@example.test', CURRENT_TIMESTAMP),
 ('t-2', 'p-3', 'Review discharge summary draft', 'OPEN', '2026-11-03 15:00:00', NULL, CURRENT_TIMESTAMP);

INSERT INTO referral_case (id, patient_id, service, reason, urgency, status, created_at, created_by) VALUES
 ('r-1', 'p-2', 'Cardiology', 'Synthetic referral: chest pain on exertion.', 'UNTRIAGED', 'NEW', CURRENT_TIMESTAMP, 'seed'),
 ('r-2', 'p-1', 'Heart failure clinic', 'Synthetic referral: follow-up after admission.', 'UNTRIAGED', 'NEW', CURRENT_TIMESTAMP, 'seed');
