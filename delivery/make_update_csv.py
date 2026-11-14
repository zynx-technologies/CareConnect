"""Builds an update-only CSV for tickets already in Jira, so a re-import changes them instead of duplicating them.

Usage: python make_update_csv.py <jira-export.csv>

Export the existing tickets from Jira first (filter: project = CC, then Export > CSV with all fields). Tickets are
matched to the backlog by exact summary. Only Assignee and Reporter are written, so nothing else on the ticket changes.
"""
import csv
import os
import sys
from collections import defaultdict

from backlog_data import EPICS, ISSUES, OPEN_ROLES, REPORTER
from generate_backlog import account, assignee

HERE = os.path.dirname(os.path.abspath(__file__))


def column(header, *names):
    for name in names:
        if name in header:
            return header.index(name)
    sys.exit(f"The export has no {names[0]!r} column. Export with all fields.")


def main(export_path):
    with open(export_path, newline="", encoding="utf-8-sig") as f:
        rows = list(csv.reader(f))
    header, rows = rows[0], rows[1:]
    key_col = column(header, "Issue key", "Key")
    id_col = column(header, "Issue id", "Issue ID")
    summary_col = column(header, "Summary")

    wanted = {name: ("", account(REPORTER)) for _, name, _ in EPICS}
    wanted.update({i[3]: (assignee(i[4]), account(REPORTER)) for i in ISSUES})

    in_jira = defaultdict(list)
    for row in rows:
        in_jira[row[summary_col].strip()].append(row)

    out_path = os.path.join(HERE, "jira-update-existing.csv")
    updated, duplicates = 0, []
    with open(out_path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["Issue key", "Issue id", "Summary", "Assignee", "Reporter"])
        for summary, (owner, reporter) in wanted.items():
            matches = in_jira.get(summary, [])
            if len(matches) > 1:
                duplicates.append((summary, [m[key_col] for m in matches]))
            for m in matches:
                w.writerow([m[key_col], m[id_col], summary, owner, reporter])
                updated += 1

    missing = [s for s in wanted if s not in in_jira]
    extra = [s for s in in_jira if s not in wanted]
    print(f"{updated} existing tickets written to {out_path}")
    print(f"{len(missing)} backlog items are not in Jira yet. Import those from the main file, or skip them.")
    if duplicates:
        print("These summaries appear more than once in Jira, which suggests an earlier duplicate import:")
        for summary, keys in duplicates:
            print(f"  {', '.join(keys)}: {summary}")
    if extra:
        print(f"{len(extra)} Jira tickets don't match any backlog summary and were left out, for example: {extra[0]!r}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
