import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, AuditEvent, AuditSummary, EmergencyAccess } from '../api';
import { label, PageTitle, tone } from '../ui';

@Component({
  selector: 'app-audit',
  imports: [DatePipe, FormsModule, PageTitle],
  template: `
    <app-page-title title="Audit and emergency access" />
    @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
    @if (summary(); as s) {
      <section class="kpis">
        <div class="kpi"><div class="k-label">Record views, 7 days</div><div class="k-value">{{ s.recordReads }}</div><div class="k-note">Every view is logged</div></div>
        <div class="kpi"><div class="k-label">Denied access, 7 days</div><div class="k-value">{{ s.denials }}</div><div class="k-note">{{ s.unusualAccess.length }} people flagged</div></div>
        <div class="kpi"><div class="k-label">Emergency access to review</div><div class="k-value">{{ s.emergencyPending }}</div><div class="k-note">{{ s.emergencyLast7Days }} in the last 7 days</div></div>
      </section>
    }
    <section class="card">
      <h2>Emergency access</h2>
      <table>
        <thead><tr><th>Patient</th><th>Staff member</th><th>Reason given</th><th>When</th><th>Review</th></tr></thead>
        <tbody>
          @for (e of access(); track e.id) {
            <tr>
              <td>{{ e.givenName }} {{ e.familyName }} <span class="muted">MRN {{ e.mrn }}</span></td>
              <td><button class="link" (click)="filterUser(e.userId)">{{ e.userId }}</button></td>
              <td>{{ e.reason }}</td>
              <td>{{ e.grantedAt | date: 'd MMM, HH:mm' }}</td>
              <td>
                @if (e.reviewedAt) {
                  <span [class]="tone(e.reviewOutcome)">{{ label(e.reviewOutcome) }}</span>
                  <div class="muted">{{ e.reviewNote }} ({{ e.reviewedBy }}, {{ e.reviewedAt | date: 'd MMM' }})</div>
                } @else {
                  <label [for]="'n' + e.id">Review note</label>
                  <input [id]="'n' + e.id" [(ngModel)]="notes[e.id]" />
                  <div class="actions-row">
                    <button class="pri" [disabled]="(notes[e.id] || '').trim().length < 5" (click)="review(e, 'APPROPRIATE')">Appropriate</button>
                    <button class="danger" [disabled]="(notes[e.id] || '').trim().length < 5" (click)="review(e, 'INAPPROPRIATE')">Inappropriate</button>
                  </div>
                }
              </td>
            </tr>
          } @empty { <tr><td colspan="5">No emergency access recorded.</td></tr> }
        </tbody>
      </table>
      <p class="footnote">An inappropriate use goes to the privacy breach runbook, including notifiable data breach steps.</p>
    </section>

    @if (summary()?.unusualAccess?.length) {
      <section class="card">
        <h2>Unusual access</h2>
        <ul>@for (u of summary()!.unusualAccess; track u.userId) { <li><button class="link" (click)="filterUser(u.userId)">{{ u.userId }}</button>: {{ u.denials }} denied attempts in 7 days</li> }</ul>
      </section>
    }

    <section class="card">
      <h2>Audit trail</h2>
      <div class="actions-row">
        <label for="fu">Staff member</label><input id="fu" [(ngModel)]="userFilter" />
        <label for="fp">Patient ID</label><input id="fp" [(ngModel)]="patientFilter" />
        <button (click)="loadEvents()">Search</button>
      </div>
      <table>
        <thead><tr><th>When</th><th>Staff member</th><th>Action</th><th>Patient</th><th>Outcome</th><th>Detail</th></tr></thead>
        <tbody>
          @for (e of events(); track e.id) {
            <tr><td>{{ e.occurredAt | date: 'd MMM, HH:mm:ss' }}</td><td>{{ e.userId }}</td><td>{{ e.action }}</td><td>{{ e.patientId ?? '' }}</td>
              <td><span [class]="tone(e.outcome)">{{ e.outcome }}</span></td><td>{{ e.detail ?? '' }}</td></tr>
          } @empty { <tr><td colspan="6">No events match.</td></tr> }
        </tbody>
      </table>
      <p class="footnote">Showing the latest 200 events. Viewing the audit trail is itself logged.</p>
    </section>
  `,
  styles: `button.link { border: 0; background: none; padding: 0; margin: 0; color: #1d4ed8; font-weight: 400; text-decoration: underline; }
    .actions-row label { margin: 0; }`,
})
export class AuditPage {
  private api = inject(Api);
  protected summary = signal<AuditSummary | null>(null);
  protected access = signal<EmergencyAccess[]>([]);
  protected events = signal<AuditEvent[]>([]);
  protected error = signal('');
  protected notes: Record<string, string> = {};
  protected userFilter = signal('');
  protected patientFilter = signal('');
  protected tone = tone;
  protected label = label;

  constructor() {
    this.load();
    this.loadEvents();
  }

  private load(): void {
    this.api.auditSummary().subscribe({ next: (s) => this.summary.set(s), error: () => this.error.set('Audit data could not be loaded, or your role does not include it.') });
    this.api.emergencyAccessList().subscribe({ next: (a) => this.access.set(a) });
  }

  protected loadEvents(): void {
    this.api.auditEvents({ userId: this.userFilter().trim(), patientId: this.patientFilter().trim() }).subscribe({ next: (e) => this.events.set(e) });
  }

  protected filterUser(userId: string): void {
    this.userFilter.set(userId);
    this.loadEvents();
  }

  protected review(e: EmergencyAccess, outcome: 'APPROPRIATE' | 'INAPPROPRIATE'): void {
    this.api.reviewEmergencyAccess(e.id, outcome, this.notes[e.id].trim()).subscribe({
      next: () => this.load(),
      error: () => this.error.set('The review was not saved.'),
    });
  }
}
