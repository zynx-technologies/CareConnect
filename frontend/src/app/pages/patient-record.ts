import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Api, PatientRecord } from '../api';
import { AuthService } from '../auth';

@Component({
  selector: 'app-patient-record',
  imports: [DatePipe, FormsModule],
  template: `
    @if (denied()) {
      <h1>Access restricted</h1>
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
      <div class="banner" aria-label="Patient identity">
        <strong>{{ r.patient.givenName }} {{ r.patient.familyName }}</strong>
        <span>MRN {{ r.patient.mrn }}</span>
        <span>Born {{ r.patient.birthDate ?? 'not recorded' }}</span>
        <span class="badge" [class.amber]="r.careConsent !== 'ACTIVE'">Care sharing: {{ r.careConsent }}</span>
      </div>
      @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
      <div class="grid">
        <section class="card">
          <h2>Encounters</h2>
          <table>
            <thead><tr><th>Visit</th><th>Status</th><th>Admitted</th><th>Discharged</th></tr></thead>
            <tbody>
              @for (e of r.encounters; track e.id) {
                <tr><td>{{ e.visitNumber }}</td><td>{{ e.status }}</td><td>{{ e.admittedAt | date: 'd MMM y' }}</td><td>{{ e.dischargedAt | date: 'd MMM y' }}</td></tr>
              } @empty { <tr><td colspan="4">None recorded.</td></tr> }
            </tbody>
          </table>
        </section>
        <section class="card">
          <h2>Tasks</h2>
          <ul>
            @for (t of r.tasks; track t.id) { <li>{{ t.title }} <span class="muted">{{ t.status }}, due {{ t.dueAt | date: 'd MMM' }}</span></li> }
            @empty { <li>None recorded.</li> }
          </ul>
          <h2>Care team</h2>
          <ul>@for (c of r.careTeam; track c.userId) { <li>{{ c.userId }} <span class="muted">{{ c.teamRole }}</span></li> }</ul>
        </section>
      </div>
      <section class="card">
        <div class="row">
          <h2>Clinical notes</h2>
          @if (auth.hasRole('CLINICIAN')) { <button class="pri" (click)="draftSummary()" [disabled]="noNotes()">Draft discharge summary</button> }
        </div>
        @for (n of r.notes; track n.id) {
          <article class="note"><header><strong>{{ n.noteType }}</strong> <span class="muted">{{ n.author }}, {{ n.writtenAt | date: 'd MMM y, HH:mm' }}</span></header><p>{{ n.body }}</p></article>
        } @empty { <p>No notes recorded.</p> }
      </section>
    } @else if (error()) {
      <p class="alert" role="alert">{{ error() }}</p>
    } @else { <p>Loading patient record.</p> }
  `,
})
export class PatientRecordPage {
  private api = inject(Api);
  private router = inject(Router);
  private id = inject(ActivatedRoute).snapshot.paramMap.get('id')!;
  protected auth = inject(AuthService);
  protected record = signal<PatientRecord | null>(null);
  protected denied = signal(false);
  protected error = signal('');
  protected reason = signal('');
  protected noNotes = computed(() => (this.record()?.notes.length ?? 0) === 0);

  constructor() { this.load(); }

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

  protected draftSummary(): void {
    this.api.createDraft(this.id).subscribe({
      next: (d) => void this.router.navigate(['/drafts', d.id]),
      error: (e: HttpErrorResponse) =>
        this.error.set(e.status === 503 ? 'AI drafting is currently turned off. Write the summary in the usual way.' : 'The draft could not be created.'),
    });
  }
}
