# CareConnect Slack messages

Draft messages for the CareConnect team, ready to paste into Slack. Each one names who should send it and when.

Replace anything in `[square brackets]` before posting. Statuses match the backlog on 10 November 2026: the first release is built and tested locally, but nothing is deployed yet.

**Never post patient information in Slack**, including names, MRNs, or message content. Use Jira IDs, control IDs, and synthetic examples only.

## Channels

| Channel | Purpose | Who joins |
|---|---|---|
| `#careconnect` | Announcements, weekly status, and decisions | Whole team and sponsors |
| `#careconnect-dev` | Engineering, pipelines, code reviews, and stand-ups | Developers, testers, architects, DevOps |
| `#careconnect-integration` | EMR and HL7 feeds, and vendor coordination | Integration, network, ServiceNow, coordinator |
| `#careconnect-releases` | Deployment notices for dev, test, and production | Whole team |
| `#careconnect-incidents` | Live incidents only, one thread each | On-call, support, project officer |
| `#careconnect-clinical-safety` | Safety concerns, hazard log, and AI decisions | Clinical lead, safety and privacy officers, tech lead |

---

## 1. Project kickoff

**Channel:** `#careconnect` · **From:** Sakunthala (Project Officer) · **When:** Friday 13 November

> :wave: *Welcome to CareConnect*
>
> CareConnect helps care teams coordinate discharges and referrals using one consented patient record. Our first goal is a pilot on one ward by *late February 2027*.
>
> *Where we are*
> • The first release is built and passes its tests locally: patient record, worklist, referrals, consent checks, audit, HL7 admissions and discharges, and AI draft summaries that a clinician must sign.
> • Nothing is deployed yet. Sprint 1 gets us to our first deployment in dev.
> • We work with *synthetic data only* until the safety case and privacy impact assessment are approved.
>
> *Sprint 1: 16 to 27 November*
> Goal: Azure access, Entra sign-in, and the first deployment to dev.
>
> *Please do by Monday*
> • Join the channels in the pinned channel guide.
> • Check you can open the Jira project `CC`.
> • Read the README in the repository, especially "Before real patient data".
>
> Sprint planning is Monday at [10:00]. Questions here or to me directly. :rocket:

---

## 2. Channel guide

**Channel:** `#careconnect` · **From:** Yeshwanth (Project Coordinator) · **When:** With the kickoff. Pin it.

> :pushpin: *CareConnect channel guide*
>
> • `#careconnect`: announcements, weekly status, decisions
> • `#careconnect-dev`: engineering, pipelines, reviews, daily stand-up
> • `#careconnect-integration`: EMR feeds, HL7, vendor coordination
> • `#careconnect-releases`: deployment notices
> • `#careconnect-incidents`: live incidents only, one thread each
> • `#careconnect-clinical-safety`: safety concerns and AI decisions
>
> *Ground rules*
> • *No patient information in Slack*, ever. Use Jira IDs and control IDs.
> • Decisions made in a thread get a :white_check_mark: and a one-line summary in Jira.
> • Use threads, so channels stay readable.

---

## 3. Sprint 1 plan

**Channel:** `#careconnect-dev` · **From:** Sakunthala (Project Officer) · **When:** After planning on Monday 16 November

> :dart: *Sprint 1 plan (16 to 27 November)*
> *Goal:* deploy the first release to dev, with Entra sign-in working.
>
> *Owners*
> • @Sakunthala: CC-2, choose GitHub Actions or Azure DevOps (*decision needed by Wednesday*). CC-60, appoint the clinical safety and privacy officers.
> • @Anoop: CC-3, Azure bootstrap. CC-12, Entra app registrations. CC-4, pipeline identity and approvals. CC-5, first deployment to dev.
> • @Priyanka J: CC-6, run the backend tests against SQL Server in CI.
> • @Yeshwanth: CC-21, agree EMR test feed access with the vendor.
>
> *Watch out for*
> • The pipelines, Dockerfiles, and Azure SQL connection have *never run*. CC-5 has room for fixes, so tell us early if it grows.
> • CC-21 and CC-60 are blockers for later sprints. If they slip, we'll know by the end of next week.
>
> *Open roles:* security engineer, clinical lead, and the clinical safety and privacy officers. Work for these roles is unassigned and labelled `role-...` in Jira.
> *Ticket questions:* @Sreehari raises every CareConnect ticket, so ask him about scope or priority.

---

## 4. Daily stand-up prompt

**Channel:** `#careconnect-dev` · **From:** Slack Workflow Builder · **When:** Weekdays at 9:30

> :sunrise: *Stand-up for [date]*
> Reply in this thread with:
> 1. What you finished yesterday (Jira IDs)
> 2. What you're doing today
> 3. Anything blocking you
>
> Blockers are picked up at [10:00].

---

## 5. Pipeline decision request

**Channel:** `#careconnect-dev` · **From:** Sakunthala (Project Officer) · **When:** Monday 16 November, with a deadline of Wednesday

> :scales: *Decision needed by Wednesday 18 November: GitHub Actions or Azure DevOps (CC-2)*
>
> We have pipelines for both, but we can only keep one. They deploy to the same environments and can't see each other's locks.
>
> *GitHub Actions*
> • Separate infrastructure and app workflows, with the Terraform plan shown before approval
> • Needs the code hosted in GitHub
>
> *Azure DevOps*
> • One pipeline that deploys infrastructure and app together
> • Fits if the organisation already uses Azure Boards and Repos
>
> @Anoop, which do you recommend? Please reply in this thread. If there's no objection by Wednesday, we'll go with [recommended option].

---

## 6. EMR vendor request

**Channel:** `#careconnect-integration` (or email to the vendor) · **From:** Yeshwanth (Project Coordinator) · **When:** Monday 16 November

> Hi [vendor contact],
>
> We're preparing CareConnect to receive admission and discharge messages from [EMR name]. To start integration testing in late January, could you help with:
>
> 1. *A test feed* of HL7 v2 ADT A01 and A03 messages to our test environment
> 2. *Your HL7 specification* for those messages, including how times and time zones are sent
> 3. *About 50 synthetic sample messages*, including some unusual cases, such as missing fields and repeated messages
> 4. *Network details* for the connection. @Sri Gowthami will coordinate this with your network team.
>
> Could we have a 30-minute call this week to agree the timeline? We need the samples by [11 December] to stay on plan.
>
> Thanks, Yeshwanth

---

## 7. Weekly status

**Channel:** `#careconnect` · **From:** Sakunthala (Project Officer) · **When:** Every Friday. This is the first one, for Friday 20 November.

> :bar_chart: *CareConnect weekly status: week ending 20 November*
>
> *Overall:* :large_yellow_circle: Amber. The plan is on track, but key roles are still unfilled.
>
> *Done this week*
> • [Azure shared resources created (CC-3)]
> • [Pipeline decision made: X (CC-2)]
>
> *Next week*
> • First deployment to dev (CC-5)
> • Entra sign-in working end to end (CC-12)
>
> *Risks and help needed*
> • :red_circle: *Clinical safety and privacy officers not yet appointed (CC-60).* Without them, the safety case and privacy assessment can't start in Sprint 3. *Sponsor help needed.*
> • :large_yellow_circle: *EMR test feed not yet agreed (CC-21).* System integration testing in Sprint 5 depends on it.
> • :large_yellow_circle: *Open engineering roles.* About a fifth of planned points have no owner yet.
>
> *Decisions this week:* [none / list]

---

## 8. Hiring request to the sponsor

**Channel:** Direct message to the sponsor, or `#careconnect` · **From:** Sakunthala (Project Officer) · **When:** Week of 16 November

> Hi [sponsor name], we need decisions on four roles to hold the February pilot date. About a fifth of the planned work in sprints 1 to 6 has no owner yet.
>
> *Needed before Sprint 3 (14 December)*
> • *Clinical safety officer:* owns the safety case. Required before any shared testing.
> • *Privacy officer:* owns the privacy impact assessment and emergency access reviews.
> • *Security engineer:* audit export, penetration test, and permissions.
>
> *Needed before Sprint 4 (11 January)*
> • *Clinical lead:* reviews clinical screens and wording.
>
> Without the two officers, the pilot moves to at least [date]. Could we discuss this at [meeting]?

---

## 9. Release notice for dev

**Channel:** `#careconnect-releases` · **From:** Anoop (Cloud Data Architect) · **When:** After CC-5 is done. *Don't post this before the deployment succeeds.*

> :package: *Deployed to dev: CareConnect 0.1.0*
> Image tag: `[commit hash]` · Pipeline run: [link]
>
> *What's in it*
> • Worklist, patient record, and task completion
> • Referral triage and booking
> • Consent and care-team access checks, emergency access, and audit
> • HL7 admission and discharge messages
> • AI draft summaries: *turned off* in dev until the clinical lead reviews them
>
> *Known limits*
> • *Synthetic data only.* Not for real patient information.
> • Nobody can open patient records through normal access until the care-team feed is built (CC-13).
> • The portal is reachable only from allow-listed networks.
>
> *Testers:* @Pradip @Priyanka J, the environment is ready for smoke tests. Please report defects in Jira.

---

## 10. Incident notice

**Channel:** `#careconnect-incidents` · **From:** On-call engineer · **When:** When an incident starts. Post one message per incident, and keep updates in its thread.

> :rotating_light: *Incident: HL7 messages failing in [environment]*
> *Status:* Investigating · *Priority:* [P2] · *ServiceNow:* [INC number]
> *Lead:* @[name] · *Started:* [time]
>
> *Impact:* [N] messages failed since [time]. Admissions and discharges from the EMR are not appearing in CareConnect. Failed messages are stored and can be reprocessed.
>
> *Next update:* [time]
>
> _No patient information in this thread. Use control IDs only._

**Resolution reply in the same thread:**

> :white_check_mark: *Resolved at [time].* Cause: [short cause]. [N] failed messages reprocessed and checked against the EMR. Follow-up: [Jira ID]. A short review is booked for [date].

---

## 11. Clinical safety channel introduction

**Channel:** `#careconnect-clinical-safety` · **From:** [Clinical Safety Officer], or Sakunthala until appointed · **When:** Once the clinical safety officer is appointed

> :hospital: *About this channel*
>
> Use this channel for anything that could affect patient safety, including what clinicians see, sign, or rely on.
>
> *Current safety decisions*
> • AI draft summaries are *off* in every environment. They stay off until the safety case and regulatory classification are approved (CC-61, CC-63).
> • When enabled, drafts can't enter the record until a clinician reviews and signs them.
> • Testing uses *synthetic data only*.
>
> *To raise a concern:* post here, or message me directly if it's sensitive. Every concern is logged in the hazard log, including ones that turn out to be fine.

---

## 12. Sprint review invitation

**Channel:** `#careconnect` · **From:** Yeshwanth (Project Coordinator) · **When:** Two days before each sprint review

> :tv: *Sprint 1 review: Friday 27 November, [14:00 to 15:00]*
>
> We'll show:
> • The first deployment in dev, if CC-5 is complete
> • Sign-in with Entra ID across the different staff roles
>
> Everyone's welcome, especially ward staff. Questions and ideas go in this thread beforehand.

---

## 13. UAT volunteer request

**Channel:** `#careconnect` and the pilot ward's channel · **From:** Pradip (Software Tester) · **When:** Start of Sprint 5 (25 January 2027)

> :raising_hand: *Help us test CareConnect before the pilot*
>
> We're looking for *4 to 6 coordinators, nurses, and doctors* from [pilot ward] for user acceptance testing on *[dates in Sprint 6]*.
>
> • About 2 hours each, in [location or Teams]
> • Synthetic patients only, no real data
> • You'll run everyday scenarios, such as discharge follow-up, referral triage, and signing a summary
>
> Reply here or message me to take part. Your feedback decides whether we're ready. :pray:
