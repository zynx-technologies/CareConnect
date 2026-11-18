import { Component, inject, Injectable, input, signal } from '@angular/core';
import { Api, Patient } from './api';

/** Knows whether the API is serving synthetic demo data. The API has no demo endpoint otherwise. */
@Injectable({ providedIn: 'root' })
export class DemoService {
  private api = inject(Api);
  readonly synthetic = signal(false);
  readonly onCareTeam = signal(true);
  private checked = false;

  check(): void {
    if (this.checked) return;
    this.checked = true;
    this.api.demo().subscribe({
      next: (s) => { this.synthetic.set(s.syntheticData); this.onCareTeam.set(s.onDemoCareTeam); },
      error: () => this.synthetic.set(false),
    });
  }

  refresh(): void {
    this.checked = false;
    this.check();
  }
}

/** Page heading, with the synthetic-data label the design shows on every screen. */
@Component({
  selector: 'app-page-title',
  template: `
    <div class="page-head">
      <h1>{{ title() }}</h1>
      @if (demo.synthetic()) { <span class="tag">Synthetic demo data</span> }
      <ng-content />
    </div>
  `,
})
export class PageTitle {
  readonly title = input.required<string>();
  protected demo = inject(DemoService);
}

/** Name, MRN, and age stay visible on every patient screen, to prevent wrong-patient errors. */
@Component({
  selector: 'app-patient-banner',
  template: `
    <div class="banner" aria-label="Patient identity">
      <strong>{{ patient().givenName }} {{ patient().familyName }}</strong>
      <span class="chip">MRN {{ patient().mrn }} · {{ age(patient().birthDate) }}</span>
      <ng-content />
    </div>
  `,
})
export class PatientBanner {
  readonly patient = input.required<Patient>();
  protected age = age;
}

export function age(birthDate: string | null): string {
  if (!birthDate) return 'age not recorded';
  const born = new Date(birthDate);
  const now = new Date();
  let years = now.getFullYear() - born.getFullYear();
  if (now.getMonth() < born.getMonth() || (now.getMonth() === born.getMonth() && now.getDate() < born.getDate())) years--;
  return `${years} y`;
}

/** Badge class for a level or status. Status text is always shown too, never colour alone. */
export function tone(value: string | null | undefined): string {
  switch (value) {
    case 'HIGH': case 'URGENT': case 'REVIEW': case 'DELAYED': case 'SUSPENDED': case 'INAPPROPRIATE': case 'DECLINED': case 'WITHDRAWN': case 'DENIED':
      return 'badge red';
    case 'MEDIUM': case 'SEMI_URGENT': case 'WATCH': case 'CHANGED': case 'SILENT_TRIAL': case 'INFO_REQUESTED': case 'IN_PROGRESS': case 'PENDING':
      return 'badge amber';
    case 'LOW': case 'OK': case 'LIVE': case 'ACTIVE': case 'APPROVED': case 'CONFIRMED': case 'CURRENT': case 'RECORDED': case 'APPROPRIATE': case 'NEW': case 'ALLOWED':
      return 'badge';
    case 'REMINDER_SENT':
      return 'badge teal';
    default:
      return 'badge grey';
  }
}

const LABELS: Record<string, string> = {
  HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low', URGENT: 'Urgent', SEMI_URGENT: 'Semi-urgent', ROUTINE: 'Routine',
  UNTRIAGED: 'Not triaged', REVIEW: 'Review', WATCH: 'Watch', OK: 'OK', LIVE: 'Live', DELAYED: 'Delayed', NO_DATA: 'No messages',
  SUSPENDED: 'Suspended', SILENT_TRIAL: 'Silent trial', ACTIVE: 'Consented', DECLINED: 'Declined', WITHDRAWN: 'Withdrawn',
  CHANGED: 'Changed', CONTINUED: 'Continued', NEW: 'New', STOPPED: 'Stopped', CONFIRMED: 'Confirmed', REMINDER_SENT: 'Reminder sent',
  APPROVED: 'Approved', RECORDED: 'Recorded', CURRENT: 'Current', IN_PROGRESS: 'In progress', INFO_REQUESTED: 'Info requested',
  APPROPRIATE: 'Appropriate', INAPPROPRIATE: 'Inappropriate', PENDING: 'Pending review', OPEN: 'Open', PLANNED: 'Planned',
  NO_LIMIT: 'No limit set', NOT_RECORDED: 'Not recorded', TRIAGED: 'Triaged', BOOKED: 'Booked',
};

export function label(value: string | null | undefined): string {
  if (!value) return 'Not scored';
  return LABELS[value] ?? value.charAt(0) + value.slice(1).toLowerCase().replaceAll('_', ' ');
}

export function pct(value: number | null | undefined): string {
  return value === null || value === undefined ? '–' : `${Math.round(value * 100)}%`;
}
