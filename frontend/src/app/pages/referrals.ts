import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, Referral } from '../api';

@Component({
  selector: 'app-referrals',
  imports: [DatePipe, FormsModule],
  template: `
    <h1>Referral management</h1>
    @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
    <section class="card">
      <table>
        <thead><tr><th>Patient</th><th>Service and reason</th><th>Received</th><th>Status</th><th>Decision</th></tr></thead>
        <tbody>
          @for (r of referrals(); track r.id) {
            <tr>
              <td>{{ r.givenName }} {{ r.familyName }} <span class="muted">{{ r.mrn }}</span></td>
              <td><strong>{{ r.service }}</strong><br />{{ r.reason }}</td>
              <td>{{ r.createdAt | date: 'd MMM' }}</td>
              <td><span class="badge" [class.red]="r.urgency === 'URGENT'">{{ r.status }}, {{ r.urgency }}</span></td>
              <td>
                @if (r.status === 'NEW') {
                  <label [for]="'u' + r.id">Urgency</label>
                  <select [id]="'u' + r.id" [(ngModel)]="urgency[r.id]"><option value="URGENT">Urgent</option><option value="SEMI_URGENT">Semi-urgent</option><option value="ROUTINE">Routine</option></select>
                  <label [for]="'w' + r.id">Clinical reason</label>
                  <input [id]="'w' + r.id" [(ngModel)]="why[r.id]" />
                  <button class="pri" [disabled]="!urgency[r.id] || (why[r.id] || '').trim().length < 5" (click)="triage(r)">Save triage</button>
                } @else {
                  <label [for]="'a' + r.id">Appointment</label>
                  <input [id]="'a' + r.id" type="datetime-local" [(ngModel)]="when[r.id]" />
                  <button class="pri" [disabled]="!when[r.id]" (click)="book(r)">Book</button>
                }
              </td>
            </tr>
          } @empty { <tr><td colspan="5">No referrals are waiting.</td></tr> }
        </tbody>
      </table>
    </section>
  `,
})
export class ReferralsPage {
  private api = inject(Api);
  protected referrals = signal<Referral[]>([]);
  protected error = signal('');
  protected urgency: Record<string, string> = {};
  protected why: Record<string, string> = {};
  protected when: Record<string, string> = {};

  constructor() { this.load(); }

  private load(): void {
    this.api.referrals().subscribe({
      next: (r) => { this.referrals.set(r); this.error.set(''); },
      error: () => this.error.set('Referrals could not be loaded, or your role does not include them.'),
    });
  }

  protected triage(r: Referral): void {
    this.api.triage(r.id, this.urgency[r.id], this.why[r.id].trim()).subscribe({
      next: () => this.load(),
      error: () => this.error.set('The triage decision was not saved. Try again.'),
    });
  }

  protected book(r: Referral): void {
    this.api.book(r.id, new Date(this.when[r.id]).toISOString()).subscribe({
      next: () => this.load(),
      error: () => this.error.set('The booking was not saved. Check that the time is in the future.'),
    });
  }
}
