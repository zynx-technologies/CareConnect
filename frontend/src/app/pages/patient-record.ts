import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Api, PatientRecord } from '../api';
import { AuthService } from '../auth';
import { label, PageTitle, PatientBanner, tone } from '../ui';

const PURPOSES: Record<string, string> = {
  CARE: 'Care team access', GP_SHARING: 'Sharing with GP', COMMUNITY_SHARING: 'Community care sharing', RESEARCH: 'Use in research',
};

@Component({
  selector: 'app-patient-record',
  imports: [DatePipe, FormsModule, PageTitle, PatientBanner],
  template: `
    @if (denied()) {
      <app-page-title title="Access restricted" />
      <section class="card" role="alert">
        <p>You do not have current access to this record. Access attempts are logged.</p>
        @if (auth.hasRole('CLINICIAN')) {
          <h2>Emergency access</h2>
          <p>Use this only for a clinical emergency. The privacy officer reviews each use.</p>
          <label for="reason">Reason (required)</label>
          <textarea id="reason" rows="3" [(ngModel)]="reason"></textarea>
          <button class="danger" [disabled]="reason().trim().length < 10" (click)="requestEmergency()">Open record in an emergency</button>
        }
      </section>
    } @else if (record(); as r) {
      <app-page-title [title]="'Patient record: ' + r.patient.givenName + ' ' + r.patient.familyName" />
      <app-patient-banner [patient]="r.patient">
        @if (current(); as e) { <span>{{ e.status === 'ADMITTED' ? 'Admitted, ' + (e.wardCode ?? 'ward not recorded') : 'Not admitted' }}</span> }
      </app-patient-banner>
      @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
      @if (message()) { <p class="badge" role="status">{{ message() }}</p> }

      <div class="cols3">
        <section class="card">
          <h2>Alerts and consent</h2>
          @for (a of r.alerts; track a.category + a.detail) {
            <div class="kv"><span>{{ a.category }}</span><span [class]="tone(a.severity)">{{ a.detail }}</span></div>
          } @empty { <p class="muted">No alerts recorded.</p> }
          @for (c of r.consents; track c.purpose) {
            <div class="kv"><span>{{ purpose(c.purpose) }}</span><span [class]="tone(c.status)">{{ label(c.status) }}</span></div>
          }
          <h2 class="gap">Care team</h2>
          <ul>@for (c of r.careTeam; track c.userId) { <li>{{ c.displayName ?? c.userId }}, <span class="muted">{{ c.teamRole }}</span></li> }</ul>
        </section>

        <section class="card">
          <h2>Timeline</h2>
          <table>
            <thead><tr><th>Date</th><th>Event</th><th>Source</th></tr></thead>
            <tbody>
              @for (t of r.timeline; track $index) {
                <tr><td>{{ t.occurredAt | date: 'd MMM' }}</td><td>{{ t.description }}</td><td>{{ t.source }}</td></tr>
              } @empty { <tr><td colspan="3">No events recorded.</td></tr> }
            </tbody>
          </table>
        </section>

        <section class="card ai-card" aria-labelledby="risk-h">
          <h2 id="risk-h">Readmission risk: 30 days</h2>
          @if (r.readmissionRisk; as k) {
            <div class="big" [class.red]="k.level === 'HIGH'" [class.amber]="k.level === 'MEDIUM'" [class.green]="k.level === 'LOW'">{{ label(k.level) }}</div>
            <p class="muted">Model {{ k.model }} {{ k.modelVersion }}. Scored {{ k.scoredAt | date: 'd MMM' }}.</p>
            @for (f of k.factors; track f.label) {
              <div class="kv"><span>{{ f.label }}</span><strong>{{ f.impact }}</strong></div>
              <div class="bar" aria-hidden="true"><span [style.width.%]="f.strength * 100"></span></div>
            }
            @if (feedbackSent()) {
              <p class="muted">Thank you. Your feedback goes to the model governance committee.</p>
            } @else if (dismissing()) {
              <label for="why">Why does this not apply? (required)</label>
              <textarea id="why" rows="2" [(ngModel)]="dismissReason"></textarea>
              <div class="actions-row">
                <button [disabled]="dismissReason().trim().length < 5" (click)="feedback('DISMISSED')">Dismiss</button>
                <button [disabled]="dismissReason().trim().length < 5" (click)="feedback('REPORTED')">Report as incorrect</button>
                <button (click)="dismissing.set(false)">Cancel</button>
              </div>
            } @else {
              <div class="actions-row">
                <button (click)="feedback('ACCEPTED')">Agree</button>
                <button (click)="dismissing.set(true)">Dismiss with reason</button>
              </div>
            }
            <p class="footnote">Risk supports clinical judgement and does not replace it. It is never used to reduce care.</p>
          } @else {
            <p>Not scored. Scores refresh daily for admitted and recently discharged patients.</p>
          }
        </section>

        <section class="card">
          <h2>Current medicines</h2>
          <table>
            <thead><tr><th>Medicine</th><th>Dose</th><th>Status</th></tr></thead>
            <tbody>
              @for (m of r.medicines; track m.name) {
                <tr><td>{{ m.name }}</td><td>{{ m.dose }}</td><td>@if (m.status === 'CHANGED' || m.status === 'NEW') {<span [class]="tone(m.status)">{{ label(m.status) }}</span>} @else { <span>{{ label(m.status) }}</span> }</td></tr>
              } @empty { <tr><td colspan="3">No medicines recorded.</td></tr> }
            </tbody>
          </table>
        </section>

        <section class="card">
          <h2>Next steps</h2>
          <ul>
            @for (t of openTasks(); track t.id) {
              <li>{{ t.title }}: <strong>{{ t.reason ?? 'due ' + (t.dueAt | date: 'd MMM') }}</strong>
                <span class="muted">{{ isOverdue(t.dueAt) ? 'overdue' : 'due ' + (t.dueAt | date: 'd MMM') }}</span></li>
            } @empty { <li>No open tasks.</li> }
          </ul>
          @if (adding()) {
            <label for="nt">Task</label><input id="nt" [(ngModel)]="newTitle" />
            <label for="nd">Due</label><input id="nd" type="datetime-local" [(ngModel)]="newDue" />
            <div class="actions-row">
              <button class="pri" [disabled]="!newTitle().trim() || !newDue()" (click)="addTask()">Add task</button>
              <button (click)="adding.set(false)">Cancel</button>
            </div>
          } @else {
            <div class="actions-row">
              <button class="pri" (click)="startFollowUp()">Book follow-up</button>
              <button (click)="adding.set(true)">Add task</button>
            </div>
          }
        </section>

        <section class="card">
          <h2>Latest summary</h2>
          @if (r.latestSummary; as s) {
            <div class="ai signed">
              Discharge summary signed by {{ s.author }} on {{ s.writtenAt | date: 'd MMM' }}.
              <details><summary>View signed document</summary><p class="pre">{{ s.body }}</p></details>
            </div>
          } @else { <p class="muted">No signed discharge summary yet.</p> }
          @if (auth.hasRole('CLINICIAN')) {
            <button class="pri" (click)="draftSummary()" [disabled]="noNotes()">Draft discharge summary</button>
          }
          <p class="footnote">Access is logged. Emergency access needs a reason.</p>
        </section>
      </div>

      <section class="card">
        <h2>Clinical notes</h2>
        @for (n of r.notes; track n.id) {
          <article class="note"><header><strong>{{ n.noteType }}</strong> <span class="muted">{{ n.author }}, {{ n.writtenAt | date: 'd MMM y, HH:mm' }}</span></header><p>{{ n.body }}</p></article>
        } @empty { <p>No notes recorded.</p> }
      </section>
    } @else if (error()) {
      <p class="alert" role="alert">{{ error() }}</p>
    } @else { <p>Loading patient record.</p> }
  `,
  styles: `.gap { margin-top: 12px; } .ai.signed { background: #eef4fc; margin-bottom: 8px; }`,
})
export class PatientRecordPage {
  private api = inject(Api);
  private router = inject(Router);
  private id = inject(ActivatedRoute).snapshot.paramMap.get('id')!;
  protected auth = inject(AuthService);
  protected record = signal<PatientRecord | null>(null);
  protected denied = signal(false);
  protected error = signal('');
  protected message = signal('');
  protected reason = signal('');
  protected dismissing = signal(false);
  protected dismissReason = signal('');
  protected feedbackSent = signal(false);
  protected adding = signal(false);
  protected newTitle = signal('');
  protected newDue = signal('');
  protected noNotes = computed(() => (this.record()?.notes.length ?? 0) === 0);
  protected current = computed(() => this.record()?.encounters[0] ?? null);
  protected openTasks = computed(() => (this.record()?.tasks ?? []).filter((t) => t.status === 'OPEN'));
  protected tone = tone;
  protected label = label;

  constructor() { this.load(); }

  protected purpose(p: string): string { return PURPOSES[p] ?? p; }

  protected isOverdue(at: string): boolean { return new Date(at).getTime() < Date.now(); }

  private load(): void {
    this.api.patient(this.id).subscribe({
      next: (r) => { this.record.set(r); this.denied.set(false); },
      error: (e: HttpErrorResponse) => {
        if (e.status === 403) this.denied.set(true);
        else this.error.set(e.status === 404 ? 'Patient not found.' : 'The record could not be loaded.');
      },
    });
  }

  protected requestEmergency(): void {
    this.api.emergencyAccess(this.id, this.reason().trim()).subscribe({
      next: () => this.load(),
      error: () => this.error.set('Emergency access could not be recorded, so the record stays closed.'),
    });
  }

  protected feedback(outcome: 'ACCEPTED' | 'DISMISSED' | 'REPORTED'): void {
    const risk = this.record()?.readmissionRisk;
    if (!risk) return;
    this.api.riskFeedback(risk.id, outcome, outcome === 'ACCEPTED' ? null : this.dismissReason().trim()).subscribe({
      next: () => { this.feedbackSent.set(true); this.dismissing.set(false); },
      error: () => this.error.set('Your feedback was not saved. Try again.'),
    });
  }

  protected startFollowUp(): void {
    const due = new Date(Date.now() + 7 * 86400000);
    due.setHours(11, 0, 0, 0);
    this.newTitle.set('Book GP follow-up within 7 days');
    this.newDue.set(new Date(due.getTime() - due.getTimezoneOffset() * 60000).toISOString().slice(0, 16));
    this.adding.set(true);
  }

  protected addTask(): void {
    this.api.createTask(this.id, this.newTitle().trim(), new Date(this.newDue()).toISOString(), 'Added from patient record').subscribe({
      next: () => { this.adding.set(false); this.message.set('Task added to the worklist.'); this.load(); },
      error: () => this.error.set('The task was not added.'),
    });
  }

  protected draftSummary(): void {
    this.api.createDraft(this.id).subscribe({
      next: (d) => void this.router.navigate(['/drafts', d.id]),
      error: (e: HttpErrorResponse) =>
        this.error.set(e.status === 503 ? 'AI drafting is currently turned off. Write the summary in the usual way.' : 'The draft could not be created.'),
    });
  }
}
