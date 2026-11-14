# CareConnect backlog

Generated from `backlog_data.py`. Edit that file, then run `python generate_backlog.py`.

Status reflects the repository on 10 November 2026. **Done** means built and tested locally; nothing has been deployed.

## Summary

| Measure | Value |
|---|---|
| Epics | 20 |
| Issues | 105 (7 done, 52 future-phase) |
| Points planned in sprints 1 to 6 | 197 |
| Planned work with no owner yet | 37 points |

**Reporter on every ticket:** Sreehari Veeramreddy (Reporting Manager, Cloud and Java UI Developer). Assignees are matched by Jira account email.

## Sprints

| Sprint | Dates | Goal | Points |
|---|---|---|---|
| Sprint 1 | 16 Nov 2026 to 27 Nov 2026 | Ready to deploy dev: Azure access, Entra registrations, first pipeline run | 27 |
| Sprint 2 | 30 Nov 2026 to 11 Dec 2026 | Dev and test running, with care-team data and HL7 failure alerts | 40 |
| Sprint 3 | 14 Dec 2026 to 23 Dec 2026 | Security hardening before any shared testing | 42 |
| Sprint 4 | 11 Jan 2027 to 22 Jan 2027 | Privacy officer workflow and HL7 reprocessing | 34 |
| Sprint 5 | 25 Jan 2027 to 5 Feb 2027 | System integration testing with the EMR test feed | 32 |
| Sprint 6 | 8 Feb 2027 to 19 Feb 2027 | UAT, governance sign-offs, and pilot-ward readiness | 22 |

Sprint 3 is short for the Christmas shutdown. Australian public holidays are not otherwise deducted.

## Load by person

Points in sprints 1 to 6. Use this to rebalance at sprint planning.

| Person | Role | Jira account | Points | Issues |
|---|---|---|---|---|
| Anoop Diddigi Kulkarni | Cloud Data Architect | anoop.kulkarni@zynxtechnologies.com | 23 | 5 |
| Chandan Sai Varma Konduru | IT Consultant | chandan.konduru@zynxtechnologies.com | 13 | 3 |
| Dilip Kommanapalli | Data Analyst | *no email: map by hand* | 6 | 2 |
| Pradip Shrestha | Software Tester | pradip.shrestha@zynxtechnologies.com | 24 | 4 |
| Sakunthala Panchapakesan | Project Officer | sakunthala.panchapakesan@zynxtechnologies.com | 6 | 3 |
| Satishkumar Kudikala | ServiceNow and SAP Consultant | *no email: map by hand* | 10 | 2 |
| Sravanthi Nagabelli | UI and UX Designer | sravanthi.nagabelli@zynxtechnologies.com | 13 | 3 |
| Sri Gowthami Seelam | Senior Network Engineer | srigowthami.seelam@zynxtechnologies.com | 21 | 3 |
| Yeshwanth Chemakuri | Project Coordinator and Consultant | yeshwanth.chemakuri@zynxtechnologies.com | 6 | 3 |
| Priyanka Pothuraju | Data Analyst and Software Engineer | priyanka.pothuraju@zynxtechnologies.com | 13 | 2 |
| Sreehari Veeramreddy | Reporting Manager, Cloud and Java UI Developer | *no email: map by hand* | 12 | 4 |
| Priyanka Janumpally | ETL Test Engineer | priyanka.janumpally@zynxtechnologies.com | 13 | 3 |
| **Not yet in the team** | Security Engineer | unassigned | 11 | 3 |
| **Not yet in the team** | Clinical Lead | unassigned | 2 | 1 |
| **Not yet in the team** | Clinical Safety Officer | unassigned | 11 | 2 |
| **Not yet in the team** | Privacy Officer | unassigned | 13 | 3 |

## Blockers

These stop other work if they slip.

- **CC-13** Load care-team membership from the EMR or rostering system (Priyanka Pothuraju, Sprint 2)
- **CC-21** Agree EMR test feed access and message samples (Yeshwanth Chemakuri, Sprint 1)
- **CC-60** Appoint the Clinical Safety Officer and Privacy Officer (Sakunthala Panchapakesan, Sprint 1)

## Backlog by epic

### E1: Platform and delivery pipeline

Azure environments, CI/CD, and operational basics for dev, test, and production.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-1 | Story | Build the API, portal, Terraform, and pipelines for the first release | Priyanka Pothuraju | 13 |  | Done |
| CC-2 | Decision | Choose GitHub Actions or Azure DevOps, and delete the other | Sakunthala Panchapakesan | 1 | Sprint 1 | To Do |
| CC-3 | Task | Run the Azure bootstrap script and create the state storage and registry | Anoop Diddigi Kulkarni | 2 | Sprint 1 | To Do |
| CC-4 | Task | Set up pipeline identity, environments, and approvals | Anoop Diddigi Kulkarni | 5 | Sprint 1 | To Do |
| CC-5 | Task | First deployment to dev, and fix what the first pipeline run finds | Anoop Diddigi Kulkarni | 8 | Sprint 1 | To Do |
| CC-6 | Task | Run the backend test suite against SQL Server in CI | Priyanka Janumpally | 3 | Sprint 1 | To Do |
| CC-7 | Task | Deploy the test environment | Sreehari Veeramreddy | 3 | Sprint 2 | To Do |
| CC-8 | Task | Narrow pipeline permissions from subscription-wide roles | Security Engineer *(to fill)* | 3 | Sprint 3 | To Do |

### E2: Identity, access, and consent

Entra sign-in, role and care-relationship checks, consent, emergency access, and audit.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-10 | Story | Role, care-relationship, and consent checks on every patient read | Priyanka Pothuraju | 8 |  | Done |
| CC-11 | Story | Emergency access with a required reason | Priyanka Pothuraju | 3 |  | Done |
| CC-12 | Task | Create the Entra app registrations and app roles | Anoop Diddigi Kulkarni | 3 | Sprint 1 | To Do |
| CC-13 | Story | Load care-team membership from the EMR or rostering system | Priyanka Pothuraju | 8 | Sprint 2 | To Do |
| CC-14 | Story | Privacy officer screen for emergency access review | Sravanthi Nagabelli | 5 | Sprint 4 | To Do |
| CC-15 | Story | Privacy officer screen for patient audit trails | Sreehari Veeramreddy | 3 | Sprint 4 | To Do |
| CC-16 | Spike | Confirm consent rules for each use of patient data | Privacy Officer *(to fill)* | 3 | Sprint 2 | To Do |

### E3: Clinical integration

HL7 and FHIR feeds, patient matching, terminology, and message operations.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-20 | Story | Receive HL7 admission and discharge messages | Priyanka Pothuraju | 8 |  | Done |
| CC-21 | Task | Agree EMR test feed access and message samples | Yeshwanth Chemakuri | 3 | Sprint 1 | To Do |
| CC-22 | Story | Alert on failed HL7 messages | Satishkumar Kudikala | 5 | Sprint 2 | To Do |
| CC-23 | Story | Reprocess failed HL7 messages | Priyanka Pothuraju | 5 | Sprint 4 | To Do |
| CC-24 | Task | Confirm the site time zone for HL7 times | Yeshwanth Chemakuri | 1 | Sprint 2 | To Do |
| CC-25 | Story | HL7 conformance tests with real message samples | Priyanka Janumpally | 5 | Sprint 5 | To Do |

### E4: Patient record and care coordination

Patient record, worklist, tasks, and discharge follow-up.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-30 | Story | Worklist, patient record, and task completion | Priyanka Pothuraju | 8 |  | Done |
| CC-31 | Story | Usability test the worklist and patient record with coordinators | Sravanthi Nagabelli | 5 | Sprint 2 | To Do |
| CC-32 | Story | Create and assign tasks from the patient record | Sreehari Veeramreddy | 3 | Sprint 4 | To Do |
| CC-33 | Task | Accessibility review against WCAG 2.2 AA | Sravanthi Nagabelli | 3 | Sprint 5 | To Do |

### E5: Referral management

Referral intake, triage, booking, and service matching.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-40 | Story | Referral triage and booking | Priyanka Pothuraju | 5 |  | Done |
| CC-41 | Story | Create referrals from the portal | Sreehari Veeramreddy | 3 | Sprint 4 | To Do |

### E6: AI discharge drafting

Cited draft summaries that a clinician must review and sign.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-50 | Story | Cited discharge drafts that need a clinician's signature | Priyanka Pothuraju | 8 |  | Done |
| CC-51 | Task | Clinical review of the draft review screen and wording | Clinical Lead *(to fill)* | 2 | Sprint 4 | To Do |

### E7: Clinical safety, privacy, and compliance

Safety case, privacy impact assessment, regulatory classification, and approvals.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-60 | Task | Appoint the Clinical Safety Officer and Privacy Officer | Sakunthala Panchapakesan | 2 | Sprint 1 | To Do |
| CC-61 | Story | Clinical safety case and hazard log | Clinical Safety Officer *(to fill)* | 8 | Sprint 3 | To Do |
| CC-62 | Story | Privacy impact assessment | Privacy Officer *(to fill)* | 8 | Sprint 3 | To Do |
| CC-63 | Spike | Regulatory classification of each AI feature | Clinical Safety Officer *(to fill)* | 3 | Sprint 4 | To Do |
| CC-64 | Task | Confirm health record retention rules by state | Privacy Officer *(to fill)* | 2 | Sprint 4 | To Do |

### E8: Security and network hardening

Front Door and WAF, network design, database roles, audit export, and penetration testing.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-70 | Story | Add Front Door and WAF in place of IP allow-listing | Sri Gowthami Seelam | 8 | Sprint 3 | To Do |
| CC-71 | Story | Separate database roles for migrations and the app | Anoop Diddigi Kulkarni | 5 | Sprint 3 | To Do |
| CC-72 | Story | Export the audit log to immutable storage and Sentinel | Security Engineer *(to fill)* | 5 | Sprint 3 | To Do |
| CC-73 | Task | Hospital network connectivity design | Sri Gowthami Seelam | 5 | Sprint 2 | To Do |
| CC-74 | Task | Independent penetration test | Security Engineer *(to fill)* | 3 | Sprint 6 | To Do |
| CC-75 | Story | Disaster recovery design and rehearsal | Sri Gowthami Seelam | 8 | Sprint 6 | To Do |

### E9: Testing and quality

Test strategy, SIT, UAT, performance, and accessibility.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-80 | Task | Test plan for the pilot release | Pradip Shrestha | 3 | Sprint 2 | To Do |
| CC-81 | Task | Clinical safety test scenarios | Pradip Shrestha | 5 | Sprint 4 | To Do |
| CC-82 | Task | System integration testing with the EMR test feed | Pradip Shrestha | 8 | Sprint 5 | To Do |
| CC-83 | Task | Performance test at peak message and user volume | Priyanka Janumpally | 5 | Sprint 5 | To Do |
| CC-84 | Task | User acceptance testing with the pilot ward | Pradip Shrestha | 8 | Sprint 6 | To Do |

### E10: Operations and support

Monitoring, ServiceNow incidents, runbooks, and service handover.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-90 | Story | ServiceNow incidents from Azure Monitor alerts | Satishkumar Kudikala | 5 | Sprint 3 | To Do |
| CC-91 | Task | Monitoring dashboard and alert rules | Dilip Kommanapalli | 3 | Sprint 4 | To Do |
| CC-92 | Task | Runbooks for on-call support | Chandan Sai Varma Konduru | 3 | Sprint 5 | To Do |

### E11: Change, training, and pilot rollout

Operating model, SOPs, training, and the pilot ward.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-100 | Task | Current-state review of discharge and referral processes | Chandan Sai Varma Konduru | 5 | Sprint 2 | To Do |
| CC-101 | Task | Choose the pilot ward and clinical champions | Yeshwanth Chemakuri | 2 | Sprint 2 | To Do |
| CC-102 | Task | SOPs and training material for the pilot | Chandan Sai Varma Konduru | 5 | Sprint 5 | To Do |
| CC-103 | Task | Release and cutover plan for the pilot | Sakunthala Panchapakesan | 3 | Sprint 6 | To Do |
| CC-104 | Task | Pilot success measures and baseline | Dilip Kommanapalli | 3 | Sprint 5 | To Do |

### E12: Data platform and analytics

De-identified analytics zone, reporting, and KPIs. Phase 4 of the design.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-240 | Story | Isolated analytics zone in Azure | Anoop Diddigi Kulkarni | 8 |  | Backlog |
| CC-241 | Story | De-identification service | Priyanka Pothuraju | 13 |  | Backlog |
| CC-242 | Story | Bulk FHIR export into Bronze | Priyanka Pothuraju | 5 |  | Backlog |
| CC-243 | Story | Silver and Gold models for encounters, referrals, and beds | Priyanka Pothuraju | 8 |  | Backlog |
| CC-244 | Task | Data quality and equity checks | Priyanka Janumpally | 5 |  | Backlog |
| CC-245 | Task | Register datasets in Purview with lineage | Anoop Diddigi Kulkarni | 3 |  | Backlog |
| CC-246 | Story | Operational reports in Power BI | Dilip Kommanapalli | 8 |  | Backlog |

### E13: Risk models and MLOps

Readmission and capacity models with clinical governance. Phase 4 and 5 of the design.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-250 | Spike | Ethics and governance approval for model development | Clinical Safety Officer *(to fill)* | 3 |  | Backlog |
| CC-251 | Story | MLOps pipeline with MLflow and Unity Catalog | ML Engineer *(to fill)* | 8 |  | Backlog |
| CC-252 | Story | Readmission risk model | ML Engineer *(to fill)* | 13 |  | Backlog |
| CC-253 | Story | Missed-appointment risk model | ML Engineer *(to fill)* | 8 |  | Backlog |
| CC-254 | Story | Bed and staffing forecasts | ML Engineer *(to fill)* | 8 |  | Backlog |
| CC-255 | Story | Eight-week silent trial of risk models | ML Engineer *(to fill)* | 5 |  | Backlog |
| CC-256 | Story | Show risk levels and reasons on worklists | Sreehari Veeramreddy | 5 |  | Backlog |
| CC-257 | Story | Model governance screen | Sreehari Veeramreddy | 8 |  | Backlog |
| CC-258 | Story | Model monitoring and per-ward kill switch | ML Engineer *(to fill)* | 5 |  | Backlog |

### E14: FHIR store and clinical feeds

Azure Health Data Services FHIR store, and pathology, imaging, GP, pharmacy, and rostering feeds. Phase 2 of the design.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-200 | Story | Provision the FHIR store with AU Base profiles | HL7 and FHIR Integration Engineer *(to fill)* | 8 |  | Backlog |
| CC-201 | Story | Convert admissions and discharges into FHIR | HL7 and FHIR Integration Engineer *(to fill)* | 8 |  | Backlog |
| CC-202 | Story | Receive pathology results | HL7 and FHIR Integration Engineer *(to fill)* | 8 |  | Backlog |
| CC-203 | Story | Receive imaging reports | HL7 and FHIR Integration Engineer *(to fill)* | 5 |  | Backlog |
| CC-204 | Story | Receive GP referral letters | HL7 and FHIR Integration Engineer *(to fill)* | 8 |  | Backlog |
| CC-205 | Story | Receive discharge medicines from pharmacy | HL7 and FHIR Integration Engineer *(to fill)* | 5 |  | Backlog |
| CC-206 | Task | Load rosters and shifts for staffing forecasts | Priyanka Pothuraju | 3 |  | Backlog |
| CC-207 | Task | FHIR conformance testing | Priyanka Janumpally | 5 |  | Backlog |

### E15: Patient identity and terminology

Matching beyond exact MRN, national identifiers, and clinical terminology mapping. Phase 2 of the design.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-210 | Story | Look up national healthcare identifiers | HL7 and FHIR Integration Engineer *(to fill)* | 8 |  | Backlog |
| CC-211 | Story | Match patients by identifier and demographics | Sreehari Veeramreddy | 13 |  | Backlog |
| CC-212 | Story | Data steward screen for uncertain matches | Sreehari Veeramreddy | 8 |  | Backlog |
| CC-213 | Story | Unlink records that were wrongly merged | Sreehari Veeramreddy | 5 |  | Backlog |
| CC-214 | Story | Map clinical codes to national terminologies | HL7 and FHIR Integration Engineer *(to fill)* | 8 |  | Backlog |

### E16: Referral service matching

Service catalogue, eligibility rules, wait times, and status updates for referrers. Phase 3 of the design.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-220 | Story | Service catalogue with eligibility rules | Sreehari Veeramreddy | 8 |  | Backlog |
| CC-221 | Story | Suggest services during triage | Sreehari Veeramreddy | 8 |  | Backlog |
| CC-222 | Task | Load wait times and next available slots | Priyanka Pothuraju | 5 |  | Backlog |
| CC-223 | Story | Send referral status updates to the referring GP | HL7 and FHIR Integration Engineer *(to fill)* | 5 |  | Backlog |

### E17: Patient reminders

SMS appointment reminders and contact preferences. Phase 3 of the design.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-230 | Story | Send SMS appointment reminders | Sreehari Veeramreddy | 5 |  | Backlog |
| CC-231 | Story | Record contact preferences and interpreter needs | Sreehari Veeramreddy | 3 |  | Backlog |
| CC-232 | Task | Privacy review of patient messaging | Privacy Officer *(to fill)* | 2 |  | Backlog |

### E18: Language-model drafting

Discharge drafts and referral extraction with a language model, after safety approval. Phase 5 of the design.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-260 | Story | Clinician-reviewed evaluation set for discharge drafts | Clinical Lead *(to fill)* | 5 |  | Backlog |
| CC-261 | Story | Deploy a private language model in an Australian region | Anoop Diddigi Kulkarni | 5 |  | Backlog |
| CC-262 | Story | Automated evaluation in the pipeline | ML Engineer *(to fill)* | 8 |  | Backlog |
| CC-263 | Task | Prompt injection and data leakage testing | Security Engineer *(to fill)* | 5 |  | Backlog |
| CC-264 | Story | Extract referral details from GP letters with a language model | ML Engineer *(to fill)* | 8 |  | Backlog |
| CC-265 | Task | Safety approval and ward-by-ward enablement | Clinical Safety Officer *(to fill)* | 3 |  | Backlog |

### E19: Hospital-wide rollout and partners

Scale from the pilot ward to the hospital, then GP and community partners. Phase 6 of the design.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-270 | Task | Pilot benefits and safety review | Dilip Kommanapalli | 3 |  | Backlog |
| CC-271 | Task | Rollout plan for remaining wards | Sakunthala Panchapakesan | 3 |  | Backlog |
| CC-272 | Task | Training and champions for each rollout wave | Chandan Sai Varma Konduru | 8 |  | Backlog |
| CC-273 | Story | Secure access for GP and community partners | Sreehari Veeramreddy | 8 |  | Backlog |
| CC-274 | Task | Data sharing agreements with partners | Yeshwanth Chemakuri | 3 |  | Backlog |
| CC-275 | Task | Hand over to business-as-usual support | Satishkumar Kudikala | 5 |  | Backlog |

### E20: Production scale and resilience

Second-region recovery, scale testing, and cost control for full production. Phase 6 of the design.

| ID | Type | Summary | Owner | Points | Sprint | Status |
|---|---|---|---|---|---|---|
| CC-280 | Story | Second-region recovery for the clinical zone | Sri Gowthami Seelam | 8 |  | Backlog |
| CC-281 | Task | Load test at hospital-wide volume | Priyanka Janumpally | 5 |  | Backlog |
| CC-282 | Spike | Decide whether to split the API into separate services | Sreehari Veeramreddy | 3 |  | Backlog |
| CC-283 | Task | Monthly cloud cost review and budgets | Anoop Diddigi Kulkarni | 2 |  | Backlog |

## Future roadmap

Work after the pilot, from phases 2 to 6 of the design. Points are rough estimates and assume the February 2027 pilot holds.

| Phase | Focus | Items | Rough points | Target |
|---|---|---|---|---|
| 2 | FHIR store, clinical feeds, identity, terminology | 13 | 92 | 2027 Q2 to 2027 Q4 |
| 3 | Referral matching and patient reminders | 7 | 36 | 2027 Q3 to 2027 Q4 |
| 4 | Analytics zone, reporting, and risk models | 12 | 90 | 2027 Q3 to 2028 Q1 |
| 5 | Silent trials, model governance, language-model drafting | 10 | 57 | 2027 Q4 to 2028 Q1 |
| 6 | Hospital-wide rollout, partners, and resilience | 10 | 48 | 2027 Q2 to 2028 Q2 |

Future work total: 52 items, about 323 points, of which 144 points need roles not yet in the team.

## Definition of done

- Code reviewed by someone other than the author
- Automated tests added, and CI passes
- Deployed to dev, and checked by the tester
- No patient data in logs, errors, or tickets
- README or runbook updated where behaviour changes
- Accessibility checked for portal changes
- Clinical safety officer consulted for any change that affects what clinicians see or sign

## Importing into Jira

1. In Jira, go to **Settings > System > External system import > CSV**. Choose `jira-import.csv` for everything, or `jira-import-future.csv` if you already imported the earlier file. The future file has its own epics, so its links work on their own.
2. Map **Issue Id** to Issue Id and **Parent** to Parent, so stories link to their epics.
3. Map **Story Points** to your story point field. Map **Sprint** only if the sprints already exist; otherwise skip it and plan sprints on the board.
4. Assignee and Reporter hold Jira account emails where known. Map any remaining names, including the reporter, to their accounts. Work for roles not yet filled is unassigned and labelled `role-...`.
5. Check the status mapping: Done and To Do must match your workflow.
