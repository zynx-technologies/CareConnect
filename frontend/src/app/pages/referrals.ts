import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, Referral, ServiceSuggestion } from '../api';
import { label, PageTitle, tone } from '../ui';

@Component({
  selector: 'app-referrals',
  imports: [DatePipe, FormsModule, PageTitle],
  template: `
    <app-page-title title="Referral management" />
    @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
    @if (message()) { <p class="badge" role="status">{{ message() }}</p> }
    <div class="cols-board">
      @for (col of columns(); track col.title) {
        <section class="card col" [attr.aria-label]="col.title">
          <h2>{{ col.title }} ({{ col.items.length }})</h2>
          @for (r of col.items; track r.id) {
            <button class="rcard" [class.sel]="selected()?.id === r.id" (click)="select(r)" [attr.aria-pressed]="selected()?.id === r.id">
              <strong>{{ r.givenName }} {{ r.familyName }}</strong>
              <span class="sub">{{ r.bookedService && r.status === 'BOOKED' ? r.bookedService : r.service }}{{ subline(r) }}</span>
              @if (r.status === 'BOOKED') {
                <span [class]="tone(r.bookingStatus)">{{ label(r.bookingStatus) }}</span>
              } @else if (r.status === 'INFO_REQUESTED') {
                <span class="badge amber">Info requested</span>
              } @else {
                <span [class]="tone(urgencyOf(r))">{{ label(urgencyOf(r)) }}</span>
                @if (r.status === 'NEW' && r.suggestedUrgency) { <span class="sr">(suggested)</span> }
              }
            </button>
          } @empty { <p class="muted">None.</p> }
        </section>
      }

      <section class="card" aria-live="polite">
        @if (selected(); as r) {
          <h2>{{ r.givenName }} {{ r.familyName }}: suggested services</h2>
          <p class="muted">MRN {{ r.mrn }} · received {{ r.createdAt | date: 'd MMM' }} · {{ r.source ?? 'Referral' }}</p>
          @if (r.extractedSummary) {
            <div class="ai">
              <div class="ai-label">AI extraction</div>
              {{ r.extractedSummary }}
              @if (r.extractedSource) { <div class="cite">Source: {{ r.extractedSource }}</div> }
            </div>
          }
          <details><summary>Referral text</summary><p>{{ r.reason }}</p></details>
          @if (r.status === 'BOOKED') {
            <p>Booked: <strong>{{ r.bookedService }}</strong>, {{ r.appointmentAt | date: 'd MMM, HH:mm' }}.</p>
            @if (r.triageReason) { <p class="muted">Triage: {{ label(r.urgency) }}. {{ r.triageReason }}</p> }
          } @else {
            <table>
              <thead><tr><th>Service</th><th>Next slot</th><th>Distance</th></tr></thead>
              <tbody>
                @for (s of suggestions(); track s.id) {
                  <tr [class.chosen]="service() === s.id">
                    <td><label class="check"><input type="radio" name="svc" [value]="s.id" [(ngModel)]="service" /> {{ s.name }}</label></td>
                    <td>{{ s.nextSlotAt ? (s.nextSlotAt | date: 'd MMM') : 'No slot' }}</td>
                    <td>{{ s.distanceKm }} km</td>
                  </tr>
                } @empty { <tr><td colspan="3">No matching services.</td></tr> }
              </tbody>
            </table>
            @if (mode() === 'accept') {
              <div class="actions-row">
                <button class="pri" [disabled]="!service() || !urgencyOf(r) || urgencyOf(r) === 'UNTRIAGED'" (click)="acceptAndBook(r)">Accept {{ label(urgencyOf(r)).toLowerCase() }} and book</button>
                <button (click)="mode.set('change')">Change</button>
              </div>
              <button (click)="mode.set('info')">Request information</button>
            } @else if (mode() === 'change') {
              <label for="urg">Urgency</label>
              <select id="urg" [(ngModel)]="urgency"><option value="URGENT">Urgent</option><option value="SEMI_URGENT">Semi-urgent</option><option value="ROUTINE">Routine</option></select>
              <label for="why">Clinical reason (required)</label>
              <input id="why" [(ngModel)]="why" />
              <div class="actions-row">
                <button class="pri" [disabled]="!urgency() || why().trim().length < 5 || !service()" (click)="changeAndBook(r)">Save and book</button>
                <button [disabled]="!urgency() || why().trim().length < 5" (click)="triageOnly(r)">Save triage only</button>
                <button (click)="mode.set('accept')">Cancel</button>
              </div>
            } @else {
              <label for="q">What do you need from the referrer? (required)</label>
              <textarea id="q" rows="3" [(ngModel)]="question"></textarea>
              <div class="actions-row">
                <button class="pri" [disabled]="question().trim().length < 5" (click)="requestInfo(r)">Send request</button>
                <button (click)="mode.set('accept')">Cancel</button>
              </div>
            }
            <p class="footnote">Suggestions come from eligibility rules and wait times. A clinician decides.</p>
          }
        } @else {
          <h2>Suggested services</h2>
          <p class="muted">Select a referral to see its details and suggested services.</p>
        }
      </section>
    </div>
  `,
  styles: `tr.chosen td { background: #f0f8f7; } label.check { margin: 0; }`,
})
export class ReferralsPage {
  private api = inject(Api);
  protected referrals = signal<Referral[]>([]);
  protected selected = signal<Referral | null>(null);
  protected suggestions = signal<ServiceSuggestion[]>([]);
  protected service = signal('');
  protected mode = signal<'accept' | 'change' | 'info'>('accept');
  protected urgency = signal('');
  protected why = signal('');
  protected question = signal('');
  protected error = signal('');
  protected message = signal('');
  protected tone = tone;
  protected label = label;

  protected columns = computed(() => {
    const all = this.referrals();
    const booked = all.filter((r) => r.status === 'BOOKED').sort((a, b) => (a.appointmentAt ?? '').localeCompare(b.appointmentAt ?? ''));
    return [
      { title: 'New', items: all.filter((r) => r.status === 'NEW' || r.status === 'INFO_REQUESTED') },
      { title: 'Triaged', items: all.filter((r) => r.status === 'TRIAGED') },
      { title: 'Booked', items: booked },
    ];
  });

  constructor() { this.load(); }

  private load(keep?: string): void {
    this.api.referrals().subscribe({
      next: (r) => {
        this.referrals.set(r);
        this.error.set('');
        const next = r.find((x) => x.id === keep) ?? this.columns()[0].items[0] ?? null;
        if (next) this.select(next);
      },
      error: () => this.error.set('Referrals could not be loaded, or your role does not include them.'),
    });
  }

  protected urgencyOf(r: Referral): string {
    return r.urgency === 'UNTRIAGED' ? (r.suggestedUrgency ?? 'UNTRIAGED') : r.urgency;
  }

  protected subline(r: Referral): string {
    if (r.status === 'BOOKED' && r.appointmentAt) {
      return ', ' + new Date(r.appointmentAt).toLocaleDateString('en-AU', { day: 'numeric', month: 'short' });
    }
    return r.source ? ', ' + r.source : '';
  }

  protected select(r: Referral): void {
    this.selected.set(r);
    this.mode.set('accept');
    this.urgency.set(this.urgencyOf(r) === 'UNTRIAGED' ? '' : this.urgencyOf(r));
    this.why.set('');
    this.question.set('');
    this.service.set('');
    this.suggestions.set([]);
    if (r.status !== 'BOOKED') {
      this.api.suggestions(r.id).subscribe({
        next: (s) => { this.suggestions.set(s); this.service.set(s.find((x) => x.nextSlotAt)?.id ?? ''); },
        error: () => this.error.set('Suggested services could not be loaded.'),
      });
    }
  }

  protected acceptAndBook(r: Referral): void {
    const urgency = this.urgencyOf(r);
    const reason = r.urgency === 'UNTRIAGED' ? 'Accepted suggested urgency after reviewing the referral' : r.triageReason ?? 'Accepted triage urgency';
    this.book(r, urgency, reason);
  }

  protected changeAndBook(r: Referral): void { this.book(r, this.urgency(), this.why().trim()); }

  private book(r: Referral, urgency: string, reason: string): void {
    this.api.acceptAndBook(r.id, this.service(), urgency, reason).subscribe({
      next: () => { this.message.set(`${r.givenName} ${r.familyName} is booked.`); this.load(r.id); },
      error: () => this.error.set('The booking was not saved. The slot may have gone; refresh and try again.'),
    });
  }

  protected triageOnly(r: Referral): void {
    this.api.triage(r.id, this.urgency(), this.why().trim()).subscribe({
      next: () => { this.message.set('Triage saved.'); this.load(r.id); },
      error: () => this.error.set('The triage decision was not saved. Try again.'),
    });
  }

  protected requestInfo(r: Referral): void {
    this.api.requestInformation(r.id, this.question().trim()).subscribe({
      next: () => { this.message.set('Request sent to the referrer.'); this.load(r.id); },
      error: () => this.error.set('The request was not saved.'),
    });
  }
}
