import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, WorklistItem, WorklistSummary } from '../api';
import { label, PageTitle, pct, tone } from '../ui';

@Component({
  selector: 'app-worklist',
  imports: [RouterLink, PageTitle],
  template: `
    <app-page-title title="Care coordination worklist" />
    @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
    @if (summary(); as s) {
      <section class="kpis" aria-label="Today at a glance">
        <div class="kpi"><div class="k-label">Open tasks</div><div class="k-value">{{ s.openTasks }}</div><div class="k-note">{{ s.overdueTasks }} overdue</div></div>
        <div class="kpi"><div class="k-label">Referrals waiting triage</div><div class="k-value">{{ s.referralsAwaitingTriage }}</div>
          <div class="k-note">{{ s.oldestReferralDays === null ? 'None waiting' : 'Oldest ' + s.oldestReferralDays + (s.oldestReferralDays === 1 ? ' day' : ' days') }}</div></div>
        <div class="kpi"><div class="k-label">Discharges in next 24 h</div><div class="k-value">{{ s.dischargesNext24h }}</div><div class="k-note">{{ s.dischargesWithoutFollowUpPlan }} need a follow-up plan</div></div>
        <div class="kpi"><div class="k-label">Follow-up within 7 days</div><div class="k-value">{{ pct(s.followUpWithin7Days) }}</div>
          <div class="k-note">Target {{ pct(s.followUpTarget) }}{{ s.followUpSample ? ', last 30 days (' + s.followUpSample + ')' : '' }}</div></div>
      </section>
    }
    <div class="cols">
      <section class="card">
        <h2>Today's priorities</h2>
        @if (loaded() && items().length === 0) { <p>No open tasks for your patients.</p> }
        @if (items().length) {
          <table>
            <thead><tr><th>Patient</th><th>Task</th><th>Reason</th><th>Readmission risk</th><th>Due</th><th><span class="sr">Actions</span></th></tr></thead>
            <tbody>
              @for (t of items(); track t.id) {
                <tr>
                  <td>{{ t.givenName }} {{ t.familyName }} <span class="muted">(MRN {{ t.mrn }})</span></td>
                  <td>{{ t.title }}</td>
                  <td>{{ t.reason ?? '' }}</td>
                  <td><span [class]="tone(t.readmissionRisk)">{{ label(t.readmissionRisk) }}</span></td>
                  <td [class.overdue]="isOverdue(t)">{{ due(t.dueAt) }}@if (isOverdue(t)) {<span class="sr"> (overdue)</span><span class="badge red" aria-hidden="true">Overdue</span>}</td>
                  <td class="actions-row">
                    <a class="btn" [routerLink]="['/patients', t.patientId]">Open</a>
                    <button (click)="complete(t)">Done</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
          <p class="footnote">Risk levels support clinical judgement and do not replace it.</p>
        }
      </section>
      <aside>
        @if (summary(); as s) {
          <section class="card">
            <h2>Team load</h2>
            @for (t of s.teamLoad; track t.team) {
              <div class="kv"><span>{{ t.team }}</span><strong>{{ t.tasks }} tasks</strong></div>
              <div class="bar" aria-hidden="true"><span [style.width.%]="t.tasks / maxLoad() * 100"></span></div>
            } @empty { <p class="muted">No open tasks.</p> }
            <h2>Interfaces</h2>
            @for (f of s.interfaces; track f.code) {
              <div class="kv"><span>{{ f.name }}</span>
                <span [class]="tone(f.status)">{{ f.status === 'DELAYED' ? f.delayMinutes + ' min delay' : label(f.status) }}</span></div>
            }
          </section>
        }
      </aside>
    </div>
  `,
  styles: `.btn { display: inline-block; border: 1px solid #bdc6d1; border-radius: 5px; padding: 5px 12px; font-weight: 600; color: inherit; text-decoration: none; margin: 4px 4px 4px 0; }
    td.overdue { color: #a11f1f; } td .badge { margin-left: 6px; }`,
})
export class WorklistPage {
  private api = inject(Api);
  protected items = signal<WorklistItem[]>([]);
  protected summary = signal<WorklistSummary | null>(null);
  protected loaded = signal(false);
  protected error = signal('');
  protected maxLoad = computed(() => Math.max(1, ...(this.summary()?.teamLoad.map((t) => t.tasks) ?? [1])));
  protected tone = tone;
  protected label = label;
  protected pct = pct;
  private datePipe = new DatePipe('en-US');

  constructor() { this.load(); }

  private load(): void {
    this.api.worklist().subscribe({
      next: (items) => { this.items.set(items); this.loaded.set(true); this.error.set(''); },
      error: () => this.error.set('The worklist could not be loaded, or your role does not include it.'),
    });
    this.api.worklistSummary().subscribe({ next: (s) => this.summary.set(s), error: () => this.summary.set(null) });
  }

  protected isOverdue(t: WorklistItem): boolean { return new Date(t.dueAt).getTime() < Date.now(); }

  protected due(at: string): string {
    const d = new Date(at);
    const today = new Date();
    const tomorrow = new Date(); tomorrow.setDate(today.getDate() + 1);
    const time = this.datePipe.transform(d, 'HH:mm');
    if (d.toDateString() === today.toDateString()) return `Today ${time}`;
    if (d.toDateString() === tomorrow.toDateString()) return `Tomorrow ${time}`;
    return this.datePipe.transform(d, 'd MMM, HH:mm') ?? '';
  }

  protected complete(task: WorklistItem): void {
    this.api.completeTask(task.id).subscribe({
      next: () => this.load(),
      error: () => this.error.set('The task could not be completed. Refresh and try again.'),
    });
  }
}
