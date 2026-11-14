"""Generates jira-import.csv and BACKLOG.md from backlog_data.py."""
import csv
import os
from collections import Counter, defaultdict

from backlog_data import EMAILS, EPICS, ISSUES, OPEN_ROLES, REPORTER, ROLES, SPRINTS
from future_backlog import FUTURE_EPICS, FUTURE_ISSUES

HERE = os.path.dirname(os.path.abspath(__file__))
EPIC_NAMES = {e[0]: e[1] for e in EPICS}
JIRA_TYPE = {"Story": "Story", "Task": "Task", "Spike": "Task", "Decision": "Task", "Epic task": "Story"}


def account(person):
    return EMAILS.get(person, person)


def assignee(owner):
    return "" if owner in OPEN_ROLES else account(owner)


def labels(issue):
    owner, extra = issue[4], issue[9]
    result = [x for x in extra.split(",") if x]
    if issue[2] in ("Spike", "Decision"):
        result.append(issue[2].lower())
    if owner in OPEN_ROLES:
        result.append("role-" + owner.lower().replace(" and ", "-").replace(" ", "-"))
    return result


def description(issue):
    text = issue[10]
    if issue[4] in OPEN_ROLES:
        text += f"\n\nOwner: {issue[4]} (not yet in the team)."
    if issue[11]:
        text += "\n\nAcceptance criteria:\n" + "\n".join(f"* {a}" for a in issue[11])
    return text


def write_csv(filename, epics, issues):
    max_labels = max(len(labels(i)) for i in issues)
    header = ["Issue Id", "Parent", "Issue Type", "Summary", "Description", "Assignee", "Reporter", "Priority", "Status",
              "Story Points", "Sprint"] + ["Labels"] * max_labels
    with open(os.path.join(HERE, filename), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(header)
        for key, name, text in epics:
            w.writerow([key, "", "Epic", name, text, "", account(REPORTER), "Medium", "To Do", "", ""] + [""] * max_labels)
        for i in issues:
            status = "Done" if i[8] == "Done" else "To Do"
            lab = labels(i)
            w.writerow([i[0], i[1], JIRA_TYPE[i[2]], i[3], description(i), assignee(i[4]), account(REPORTER), i[6], status,
                        i[5] or "", i[7]] + lab + [""] * (max_labels - len(lab)))


def write_markdown():
    lines = ["# CareConnect backlog", "",
             "Generated from `backlog_data.py`. Edit that file, then run `python generate_backlog.py`.", "",
             "Status reflects the repository on 10 November 2026. **Done** means built and tested locally; nothing has been deployed.", ""]

    planned = [i for i in ISSUES if i[7]]
    lines += ["## Summary", "", "| Measure | Value |", "|---|---|",
              f"| Epics | {len(EPICS)} |",
              f"| Issues | {len(ISSUES)} ({sum(1 for i in ISSUES if i[8] == 'Done')} done, {len(FUTURE_ISSUES)} future-phase) |",
              f"| Points planned in sprints 1 to 6 | {sum(i[5] for i in planned)} |",
              f"| Planned work with no owner yet | {sum(i[5] for i in planned if i[4] in OPEN_ROLES)} points |", ""]

    lines += [f"**Reporter on every ticket:** {REPORTER} ({ROLES[REPORTER]}). Assignees are matched by Jira account email.", ""]
    lines += ["## Sprints", "", "| Sprint | Dates | Goal | Points |", "|---|---|---|---|"]
    for name, start, end, goal in SPRINTS:
        pts = sum(i[5] for i in ISSUES if i[7] == name)
        lines.append(f"| {name} | {start} to {end} | {goal} | {pts} |")
    lines += ["", "Sprint 3 is short for the Christmas shutdown. Australian public holidays are not otherwise deducted.", ""]

    lines += ["## Load by person", "", "Points in sprints 1 to 6. Use this to rebalance at sprint planning.", "",
              "| Person | Role | Jira account | Points | Issues |", "|---|---|---|---|---|"]
    load = Counter()
    count = Counter()
    for i in planned:
        load[i[4]] += i[5]
        count[i[4]] += 1
    for person, role in ROLES.items():
        mail = EMAILS.get(person, "*no email: map by hand*")
        lines.append(f"| {person} | {role} | {mail} | {load[person]} | {count[person]} |")
    for role in OPEN_ROLES:
        if count[role]:
            lines.append(f"| **Not yet in the team** | {role} | unassigned | {load[role]} | {count[role]} |")
    lines.append("")

    lines += ["## Blockers", "", "These stop other work if they slip.", ""]
    for i in ISSUES:
        if "blocker" in i[9]:
            lines.append(f"- **CC-{i[0]}** {i[3]} ({i[4]}, {i[7]})")
    lines.append("")

    by_epic = defaultdict(list)
    for i in ISSUES:
        by_epic[i[1]].append(i)
    lines += ["## Backlog by epic", ""]
    for key, name, text in EPICS:
        lines += [f"### {key}: {name}", "", text, "", "| ID | Type | Summary | Owner | Points | Sprint | Status |", "|---|---|---|---|---|---|---|"]
        for i in by_epic[key]:
            owner = f"{i[4]} *(to fill)*" if i[4] in OPEN_ROLES else i[4]
            lines.append(f"| CC-{i[0]} | {i[2]} | {i[3]} | {owner} | {i[5] or ''} | {i[7] or ''} | {i[8]} |")
        lines.append("")

    phases = defaultdict(list)
    for i in FUTURE_ISSUES:
        phase = next(x for x in i[9].split(",") if x.startswith("phase-"))
        phases[phase].append(i)
    lines += ["## Future roadmap", "",
              "Work after the pilot, from phases 2 to 6 of the design. Points are rough estimates and assume the February 2027 pilot holds.", "",
              "| Phase | Focus | Items | Rough points | Target |", "|---|---|---|---|---|"]
    focus = {"phase-2": "FHIR store, clinical feeds, identity, terminology", "phase-3": "Referral matching and patient reminders",
             "phase-4": "Analytics zone, reporting, and risk models", "phase-5": "Silent trials, model governance, language-model drafting",
             "phase-6": "Hospital-wide rollout, partners, and resilience"}
    for phase in sorted(phases):
        items = phases[phase]
        targets = sorted({x for i in items for x in i[9].split(",") if x.startswith("target-")})
        quarter = lambda t: t[7:].replace("-q", " Q")  # noqa: E731
        span = quarter(targets[0]) if len(targets) == 1 else f"{quarter(targets[0])} to {quarter(targets[-1])}"
        lines.append(f"| {phase[-1]} | {focus[phase]} | {len(items)} | {sum(i[5] for i in items)} | {span} |")
    lines += ["", f"Future work total: {len(FUTURE_ISSUES)} items, about {sum(i[5] for i in FUTURE_ISSUES)} points, "
              f"of which {sum(i[5] for i in FUTURE_ISSUES if i[4] in OPEN_ROLES)} points need roles not yet in the team.", ""]

    lines += ["## Definition of done", "",
              "- Code reviewed by someone other than the author",
              "- Automated tests added, and CI passes",
              "- Deployed to dev, and checked by the tester",
              "- No patient data in logs, errors, or tickets",
              "- README or runbook updated where behaviour changes",
              "- Accessibility checked for portal changes",
              "- Clinical safety officer consulted for any change that affects what clinicians see or sign", ""]
    lines += ["## Importing into Jira", "",
              "1. In Jira, go to **Settings > System > External system import > CSV**. Choose `jira-import.csv` for everything, or `jira-import-future.csv` if you already imported the earlier file. The future file has its own epics, so its links work on their own.",
              "2. Map **Issue Id** to Issue Id and **Parent** to Parent, so stories link to their epics.",
              "3. Map **Story Points** to your story point field. Map **Sprint** only if the sprints already exist; otherwise skip it and plan sprints on the board.",
              "4. Assignee and Reporter hold Jira account emails where known. Map any remaining names, including the reporter, to their accounts. Work for roles not yet filled is unassigned and labelled `role-...`.",
              "5. Check the status mapping: Done and To Do must match your workflow.", ""]
    with open(os.path.join(HERE, "BACKLOG.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


if __name__ == "__main__":
    ids = [i[0] for i in ISSUES]
    assert len(ids) == len(set(ids)), "Duplicate issue ID"
    assert all(i[1] in EPIC_NAMES for i in ISSUES), "Unknown epic"
    owners = set(ROLES) | set(OPEN_ROLES)
    assert REPORTER in ROLES and set(EMAILS) <= set(ROLES), "Unknown person in EMAILS or REPORTER"
    assert all(i[4] in owners for i in ISSUES), [i[4] for i in ISSUES if i[4] not in owners]
    assert all(i[7] in {s[0] for s in SPRINTS} | {""} for i in ISSUES), "Unknown sprint"
    future_epics = {e[0] for e in FUTURE_EPICS} | {i[1] for i in FUTURE_ISSUES}
    assert not any(i[1] in future_epics and i[8] != "Backlog" for i in ISSUES), "Future epics must hold only future items"
    write_csv("jira-import.csv", EPICS, ISSUES)
    write_csv("jira-import-future.csv", [e for e in EPICS if e[0] in future_epics], FUTURE_ISSUES)
    write_markdown()
    print(f"{len(EPICS)} epics, {len(ISSUES)} issues written")
