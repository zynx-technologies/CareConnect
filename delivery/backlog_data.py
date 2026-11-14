"""Single source for the CareConnect backlog. Run generate_backlog.py after editing this file."""

PROJECT_KEY = "CC"

# Two-week sprints. Sprint 3 is short for the Christmas shutdown, and the team returns on 11 January 2027.
SPRINTS = [
    ("Sprint 1", "16 Nov 2026", "27 Nov 2026", "Ready to deploy dev: Azure access, Entra registrations, first pipeline run"),
    ("Sprint 2", "30 Nov 2026", "11 Dec 2026", "Dev and test running, with care-team data and HL7 failure alerts"),
    ("Sprint 3", "14 Dec 2026", "23 Dec 2026", "Security hardening before any shared testing"),
    ("Sprint 4", "11 Jan 2027", "22 Jan 2027", "Privacy officer workflow and HL7 reprocessing"),
    ("Sprint 5", "25 Jan 2027", "5 Feb 2027", "System integration testing with the EMR test feed"),
    ("Sprint 6", "8 Feb 2027", "19 Feb 2027", "UAT, governance sign-offs, and pilot-ward readiness"),
]

ROLES = {
    "Anoop Diddigi Kulkarni": "Cloud Data Architect",
    "Chandan Sai Varma Konduru": "IT Consultant",
    "Dilip Kommanapalli": "Data Analyst",
    "Pradip Shrestha": "Software Tester",
    "Sakunthala Panchapakesan": "Project Officer",
    "Satishkumar Kudikala": "ServiceNow and SAP Consultant",
    "Sravanthi Nagabelli": "UI and UX Designer",
    "Sri Gowthami Seelam": "Senior Network Engineer",
    "Yeshwanth Chemakuri": "Project Coordinator and Consultant",
    "Priyanka Pothuraju": "Data Analyst and Software Engineer",
    "Sreehari Veeramreddy": "Reporting Manager, Cloud and Java UI Developer",
    "Priyanka Janumpally": "ETL Test Engineer",
}

# Reporter for every ticket.
REPORTER = "Sreehari Veeramreddy"

# Jira account emails. People without an email here are imported by name, and must be mapped during import.
EMAILS = {
    "Anoop Diddigi Kulkarni": "anoop.kulkarni@zynxtechnologies.com",
    "Chandan Sai Varma Konduru": "chandan.konduru@zynxtechnologies.com",
    "Pradip Shrestha": "pradip.shrestha@zynxtechnologies.com",
    "Sakunthala Panchapakesan": "sakunthala.panchapakesan@zynxtechnologies.com",
    "Sravanthi Nagabelli": "sravanthi.nagabelli@zynxtechnologies.com",
    "Sri Gowthami Seelam": "srigowthami.seelam@zynxtechnologies.com",
    "Yeshwanth Chemakuri": "yeshwanth.chemakuri@zynxtechnologies.com",
    "Priyanka Pothuraju": "priyanka.pothuraju@zynxtechnologies.com",
    "Priyanka Janumpally": "priyanka.janumpally@zynxtechnologies.com",
}

# Roles the design says are needed but nobody holds yet. Work for them is left unassigned and labelled.
OPEN_ROLES = [
    "HL7 and FHIR Integration Engineer",
    "ML Engineer", "Security Engineer", "Clinical Lead", "Clinical Safety Officer", "Privacy Officer",
]

EPICS = [
    ("E1", "Platform and delivery pipeline", "Azure environments, CI/CD, and operational basics for dev, test, and production."),
    ("E2", "Identity, access, and consent", "Entra sign-in, role and care-relationship checks, consent, emergency access, and audit."),
    ("E3", "Clinical integration", "HL7 and FHIR feeds, patient matching, terminology, and message operations."),
    ("E4", "Patient record and care coordination", "Patient record, worklist, tasks, and discharge follow-up."),
    ("E5", "Referral management", "Referral intake, triage, booking, and service matching."),
    ("E6", "AI discharge drafting", "Cited draft summaries that a clinician must review and sign."),
    ("E7", "Clinical safety, privacy, and compliance", "Safety case, privacy impact assessment, regulatory classification, and approvals."),
    ("E8", "Security and network hardening", "Front Door and WAF, network design, database roles, audit export, and penetration testing."),
    ("E9", "Testing and quality", "Test strategy, SIT, UAT, performance, and accessibility."),
    ("E10", "Operations and support", "Monitoring, ServiceNow incidents, runbooks, and service handover."),
    ("E11", "Change, training, and pilot rollout", "Operating model, SOPs, training, and the pilot ward."),
    ("E12", "Data platform and analytics", "De-identified analytics zone, reporting, and KPIs. Phase 4 of the design."),
    ("E13", "Risk models and MLOps", "Readmission and capacity models with clinical governance. Phase 4 and 5 of the design."),
]

# Fields: id, epic, type, summary, assignee or open role, points, priority, sprint, status, labels, description, acceptance
# Status reflects the code in this repository on 10 Nov 2026. "Done" means built and tested locally; nothing is deployed.
ISSUES = [
    # E1 Platform and delivery pipeline
    ("1", "E1", "Story", "Build the API, portal, Terraform, and pipelines for the first release", "Priyanka Pothuraju", 13, "High", "", "Done", "first-release",
     "First release code is in the repository: Spring Boot API, Angular portal, Terraform, Azure DevOps and GitHub Actions pipelines.",
     ["Backend and frontend tests pass locally", "Terraform validates", "Pipeline YAML parses"]),
    ("2", "E1", "Decision", "Choose GitHub Actions or Azure DevOps, and delete the other", "Sakunthala Panchapakesan", 1, "Highest", "Sprint 1", "To Do", "decision",
     "Both pipeline sets deploy to the same environments and cannot see each other's locks, so only one can stay.",
     ["Decision recorded with reason", "Unused pipeline folder deleted", "README updated"]),
    ("3", "E1", "Task", "Run the Azure bootstrap script and create the state storage and registry", "Anoop Diddigi Kulkarni", 2, "Highest", "Sprint 1", "To Do", "azure",
     "Run infra/bootstrap.sh in the chosen subscription, in an Australian region.",
     ["State storage and registry exist", "Values recorded as pipeline variables", "No admin user on the registry"]),
    ("4", "E1", "Task", "Set up pipeline identity, environments, and approvals", "Anoop Diddigi Kulkarni", 5, "Highest", "Sprint 1", "To Do", "azure",
     "Create the federated identity, environments, variables, and production approvers as the README describes. Hand over to Sreehari after the first deployment.",
     ["Federated sign-in works with no stored secrets", "Production needs a named approver", "Deployments are limited to main"]),
    ("5", "E1", "Task", "First deployment to dev, and fix what the first pipeline run finds", "Anoop Diddigi Kulkarni", 8, "Highest", "Sprint 1", "To Do", "first-run",
     "The Dockerfiles, nginx setup, pipelines, and Azure SQL connection have never run. Expect fixes.",
     ["Images build in the registry", "Both apps healthy in dev", "Portal reachable from allowed networks only", "Flyway migrations ran on Azure SQL"]),
    ("6", "E1", "Task", "Run the backend test suite against SQL Server in CI", "Priyanka Janumpally", 3, "High", "Sprint 1", "To Do", "first-run,sql-server",
     "Local tests used H2. The CI job against SQL Server has never run.",
     ["CI job passes on SQL Server", "Any SQL dialect issues fixed in the migrations"]),
    ("7", "E1", "Task", "Deploy the test environment", "Sreehari Veeramreddy", 3, "High", "Sprint 2", "To Do", "",
     "Promote the dev images to test using the pipeline.",
     ["Same image tag runs in dev and test", "Test uses masked or synthetic data only"]),
    ("8", "E1", "Task", "Narrow pipeline permissions from subscription-wide roles", "Security Engineer", 3, "Medium", "Sprint 3", "To Do", "needs-hire,security",
     "The pipeline identity holds Contributor and User Access Administrator on the whole subscription.",
     ["Roles scoped to the CareConnect resource groups", "Role assignment limited to AcrPull for the app identity"]),

    # E2 Identity, access, and consent
    ("10", "E2", "Story", "Role, care-relationship, and consent checks on every patient read", "Priyanka Pothuraju", 8, "Highest", "", "Done", "first-release",
     "AccessService allows a read only with a care relationship and active care consent, or a current emergency grant.",
     ["Denied reads return 403 and are audited", "Consent withdrawal blocks the next read"]),
    ("11", "E2", "Story", "Emergency access with a required reason", "Priyanka Pothuraju", 3, "High", "", "Done", "first-release",
     "Clinicians can open a record in an emergency for 4 hours after giving a reason.",
     ["Reason of at least 10 characters", "Grant is audited for privacy review"]),
    ("12", "E2", "Task", "Create the Entra app registrations and app roles", "Anoop Diddigi Kulkarni", 3, "Highest", "Sprint 1", "To Do", "entra",
     "Create the API and portal registrations, the access_as_user scope, and the five app roles.",
     ["Version 2 access tokens", "Test users assigned for each role", "Portal redirect URI added after first deployment"]),
    ("13", "E2", "Story", "Load care-team membership from the EMR or rostering system", "Priyanka Pothuraju", 8, "Highest", "Sprint 2", "To Do", "blocker",
     "Nothing fills care_team_member, so staff cannot open records through normal access. This blocks every clinical test.",
     ["Source and refresh frequency agreed", "Members added and removed automatically", "Changes audited"]),
    ("14", "E2", "Story", "Privacy officer screen for emergency access review", "Sravanthi Nagabelli", 5, "High", "Sprint 4", "To Do", "privacy",
     "The API records emergency grants, but there is no review screen.",
     ["List of unreviewed grants with reason and user", "Reviewer records an outcome and comment", "Overdue reviews are highlighted"]),
    ("15", "E2", "Story", "Privacy officer screen for patient audit trails", "Sreehari Veeramreddy", 3, "Medium", "Sprint 4", "To Do", "privacy",
     "Show the existing /patients/{id}/audit API in the portal.",
     ["Filter by user, action, and date", "Viewing an audit trail is itself audited"]),
    ("16", "E2", "Spike", "Confirm consent rules for each use of patient data", "Privacy Officer", 3, "High", "Sprint 2", "To Do", "needs-appointment,privacy",
     "The design assumes care sharing is permitted by policy and research needs opt-in. Legal and privacy must confirm.",
     ["Written rules for care, research, and GP sharing", "Code changes raised for any difference"]),

    # E3 Clinical integration
    ("20", "E3", "Story", "Receive HL7 admission and discharge messages", "Priyanka Pothuraju", 8, "High", "", "Done", "first-release",
     "ADT A01 and A03 over HTTPS, with raw message storage, duplicate detection, and a follow-up task on discharge.",
     ["Duplicates acknowledged without reprocessing", "Failed messages stored as FAILED"]),
    ("21", "E3", "Task", "Agree EMR test feed access and message samples", "Yeshwanth Chemakuri", 3, "Highest", "Sprint 1", "To Do", "vendor,blocker",
     "The EMR vendor must provide a test feed, message specifications, and synthetic samples.",
     ["Contact and timeline agreed with the vendor", "Specification and 50 synthetic samples received"]),
    ("22", "E3", "Story", "Alert on failed HL7 messages", "Satishkumar Kudikala", 5, "Highest", "Sprint 2", "To Do", "servicenow",
     "Failed messages are stored, but nobody is told.",
     ["ServiceNow incident within 5 minutes of a failure", "Incident has the control ID and no patient data"]),
    ("23", "E3", "Story", "Reprocess failed HL7 messages", "Priyanka Pothuraju", 5, "High", "Sprint 4", "To Do", "",
     "Operators need to replay a stored message after a mapping fix.",
     ["Integration role can replay by control ID", "Replay is audited and idempotent"]),
    ("24", "E3", "Task", "Confirm the site time zone for HL7 times", "Yeshwanth Chemakuri", 1, "High", "Sprint 2", "To Do", "",
     "Times without a zone are treated as UTC, which would shift Australian times.",
     ["Zone confirmed with hospital IT", "Code updated and tested with daylight saving dates"]),
    ("25", "E3", "Story", "HL7 conformance tests with real message samples", "Priyanka Janumpally", 5, "High", "Sprint 5", "To Do", "testing",
     "Test against the vendor's synthetic samples, including unusual and malformed messages.",
     ["All sample types processed or rejected correctly", "Results report shared with the vendor"]),
    # E4 Patient record and care coordination
    ("30", "E4", "Story", "Worklist, patient record, and task completion", "Priyanka Pothuraju", 8, "High", "", "Done", "first-release",
     "Worklist for the care team, patient record with encounters, notes, tasks, and care team.", ["Only care-team patients are shown"]),
    ("31", "E4", "Story", "Usability test the worklist and patient record with coordinators", "Sravanthi Nagabelli", 5, "High", "Sprint 2", "To Do", "ux",
     "Test with at least five coordinators and nurses, using synthetic data in dev.",
     ["Findings ranked by severity", "Changes raised as stories"]),
    ("32", "E4", "Story", "Create and assign tasks from the patient record", "Sreehari Veeramreddy", 3, "Medium", "Sprint 4", "To Do", "",
     "The API supports creating tasks, but the portal does not.",
     ["Task title, due date, and assignee", "Validation messages are clear"]),
    ("33", "E4", "Task", "Accessibility review against WCAG 2.2 AA", "Sravanthi Nagabelli", 3, "Medium", "Sprint 5", "To Do", "accessibility",
     "Automated and manual review of all portal screens.",
     ["No serious axe issues", "Keyboard-only walkthrough passes"]),

    # E5 Referral management
    ("40", "E5", "Story", "Referral triage and booking", "Priyanka Pothuraju", 5, "High", "", "Done", "first-release",
     "Triage clinicians set urgency with a reason, then book.", ["Booking requires triage first"]),
    ("41", "E5", "Story", "Create referrals from the portal", "Sreehari Veeramreddy", 3, "Medium", "Sprint 4", "To Do", "",
     "The API supports creating referrals, but the portal does not.", ["Service and reason required"]),
    # E6 AI discharge drafting
    ("50", "E6", "Story", "Cited discharge drafts that need a clinician's signature", "Priyanka Pothuraju", 8, "High", "", "Done", "first-release",
     "Drafts quote source notes, flag unsupported numbers, and file nothing until signed. Off by default in deployed environments.",
     ["Unsigned drafts are not part of the record", "Kill switch tested"]),
    ("51", "E6", "Task", "Clinical review of the draft review screen and wording", "Clinical Lead", 2, "High", "Sprint 4", "To Do", "needs-appointment,clinical",
     "Confirm the labels, warnings, and signing step meet clinical expectations.", ["Clinical lead sign-off recorded"]),
    # E7 Clinical safety, privacy, and compliance
    ("60", "E7", "Task", "Appoint the Clinical Safety Officer and Privacy Officer", "Sakunthala Panchapakesan", 2, "Highest", "Sprint 1", "To Do", "governance,blocker",
     "Both roles must be in place before design approval and any shared testing.", ["Named people with agreed time commitment"]),
    ("61", "E7", "Story", "Clinical safety case and hazard log", "Clinical Safety Officer", 8, "Highest", "Sprint 3", "To Do", "needs-appointment,governance",
     "Record hazards such as wrong-patient display, failed messages, and AI errors, with controls.",
     ["Hazard log reviewed by the clinical lead", "Safety case approved before pilot"]),
    ("62", "E7", "Story", "Privacy impact assessment", "Privacy Officer", 8, "Highest", "Sprint 3", "To Do", "needs-appointment,governance",
     "Assess data flows, consent, emergency access, retention, and AI processing.", ["Actions tracked as Jira issues", "Approved before pilot"]),
    ("63", "E7", "Spike", "Regulatory classification of each AI feature", "Clinical Safety Officer", 3, "High", "Sprint 4", "To Do", "needs-appointment,regulatory",
     "Get specialist advice on whether any feature counts as a medical device under TGA rules.", ["Written classification for each feature"]),
    ("64", "E7", "Task", "Confirm health record retention rules by state", "Privacy Officer", 2, "Medium", "Sprint 4", "To Do", "needs-appointment",
     "Includes longer periods for children's records.", ["Retention rules documented and applied in design"]),

    # E8 Security and network hardening
    ("70", "E8", "Story", "Add Front Door and WAF in place of IP allow-listing", "Sri Gowthami Seelam", 8, "Highest", "Sprint 3", "To Do", "security,network",
     "The portal is currently reachable only from allow-listed networks.", ["WAF in prevention mode", "Container app reachable only through Front Door"]),
    ("71", "E8", "Story", "Separate database roles for migrations and the app", "Anoop Diddigi Kulkarni", 5, "Highest", "Sprint 3", "To Do", "security",
     "The app identity is the SQL administrator, so a compromised app could alter the audit table and its trigger.",
     ["App runs with read and write only", "Migrations use a separate identity", "Audit table cannot be changed by the app"]),
    ("72", "E8", "Story", "Export the audit log to immutable storage and Sentinel", "Security Engineer", 5, "High", "Sprint 3", "To Do", "needs-hire,security",
     "Audit records must survive a database compromise.", ["Export within 15 minutes", "Alerts on unusual access patterns"]),
    ("73", "E8", "Task", "Hospital network connectivity design", "Sri Gowthami Seelam", 5, "High", "Sprint 2", "To Do", "network",
     "Design the hub network and private connection to the hospital for HL7 and staff access.", ["Design approved by hospital network team", "Address ranges agreed"]),
    ("74", "E8", "Task", "Independent penetration test", "Security Engineer", 3, "High", "Sprint 6", "To Do", "needs-hire,security",
     "Book an external tester, including AI-specific tests.", ["No open critical or high findings at pilot"]),
    ("75", "E8", "Story", "Disaster recovery design and rehearsal", "Sri Gowthami Seelam", 8, "Medium", "Sprint 6", "To Do", "network,dr",
     "Meet a 5-minute recovery point and 1-hour recovery time.", ["Rehearsal completed and results recorded"]),

    # E9 Testing and quality
    ("80", "E9", "Task", "Test plan for the pilot release", "Pradip Shrestha", 3, "High", "Sprint 2", "To Do", "testing",
     "Cover functional, access, consent, HL7, AI drafting, and downtime scenarios.", ["Plan reviewed by clinical lead and tech lead"]),
    ("81", "E9", "Task", "Clinical safety test scenarios", "Pradip Shrestha", 5, "High", "Sprint 4", "To Do", "testing,clinical",
     "Wrong-patient, failed message, withdrawn consent, and AI warning scenarios from the hazard log.", ["Each hazard has a linked test"]),
    ("82", "E9", "Task", "System integration testing with the EMR test feed", "Pradip Shrestha", 8, "High", "Sprint 5", "To Do", "testing,sit",
     "End-to-end tests from EMR message to worklist.", ["All critical scenarios pass", "Defects triaged daily"]),
    ("83", "E9", "Task", "Performance test at peak message and user volume", "Priyanka Janumpally", 5, "Medium", "Sprint 5", "To Do", "testing,performance",
     "1 million messages a day and 500 concurrent users, from the design targets.", ["95% of record loads under 2 seconds"]),
    ("84", "E9", "Task", "User acceptance testing with the pilot ward", "Pradip Shrestha", 8, "High", "Sprint 6", "To Do", "testing,uat",
     "Scenarios run by coordinators, nurses, and clinicians.", ["Signed UAT acceptance"]),

    # E10 Operations and support
    ("90", "E10", "Story", "ServiceNow incidents from Azure Monitor alerts", "Satishkumar Kudikala", 5, "High", "Sprint 3", "To Do", "servicenow",
     "Route app health, failed deployments, and HL7 failures to the right support group.", ["Priority set by severity", "No patient data in incidents"]),
    ("91", "E10", "Task", "Monitoring dashboard and alert rules", "Dilip Kommanapalli", 3, "Medium", "Sprint 4", "To Do", "monitoring",
     "Service health, HL7 backlog, failed messages, and emergency access counts.", ["Shared with operations and on-call"]),
    ("92", "E10", "Task", "Runbooks for on-call support", "Chandan Sai Varma Konduru", 3, "Medium", "Sprint 5", "To Do", "operations",
     "Failed deployment, HL7 failure, AI kill switch, and access complaints.", ["Reviewed in a tabletop exercise"]),

    # E11 Change, training, and pilot rollout
    ("100", "E11", "Task", "Current-state review of discharge and referral processes", "Chandan Sai Varma Konduru", 5, "High", "Sprint 2", "To Do", "change",
     "Map how wards work today, to plan the pilot and training.", ["Process maps reviewed by the pilot ward"]),
    ("101", "E11", "Task", "Choose the pilot ward and clinical champions", "Yeshwanth Chemakuri", 2, "High", "Sprint 2", "To Do", "stakeholders",
     "Work with the clinical lead and nursing management.", ["Ward and champions named"]),
    ("102", "E11", "Task", "SOPs and training material for the pilot", "Chandan Sai Varma Konduru", 5, "Medium", "Sprint 5", "To Do", "change,training",
     "Include downtime procedures and how to report a safety concern.", ["Training delivered to all pilot staff"]),
    ("103", "E11", "Task", "Release and cutover plan for the pilot", "Sakunthala Panchapakesan", 3, "High", "Sprint 6", "To Do", "release",
     "Go and no-go criteria, rollback, communications, and hypercare roster.", ["Go and no-go meeting held"]),
    ("104", "E11", "Task", "Pilot success measures and baseline", "Dilip Kommanapalli", 3, "Medium", "Sprint 5", "To Do", "kpi",
     "Follow-up within 7 days, referral wait, and documentation time.", ["Baseline measured before go-live"]),

]

# Future-phase work. See future_backlog.py.
from future_backlog import FUTURE_EPICS, FUTURE_ISSUES  # noqa: E402

EPICS += FUTURE_EPICS
ISSUES += FUTURE_ISSUES
