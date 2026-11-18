import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api, ModelSummary, ModelView } from '../api';
import { label, PageTitle, pct, tone } from '../ui';

@Component({
  selector: 'app-governance',
  imports: [DatePipe, DecimalPipe, FormsModule, PageTitle],
  template: `
    <app-page-title [title]="'Model governance' + (view() ? ': ' + view()!.model + ' ' + view()!.version : '')">
      <label class="pick">Model
        <select [ngModel]="key()" (ngModelChange)="open($event)">
          @for (m of models(); track m.model + m.version) { <option [value]="m.model + '|' + m.version">{{ m.model }} {{ m.version }} ({{ label(m.clinicalStatus) }})</option> }
        </select>
      </label>
    </app-page-title>
    @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
    @if (view(); as v) {
      <p class="muted">{{ v.purpose }}</p>
      <section class="kpis">
        <div class="kpi"><div class="k-label">Clinical status</div>
          <div class="k-value">{{ v.featureSwitchedOff ? 'Off' : label(v.clinicalStatus) }}</div>
          <div class="k-note">{{ v.featureSwitchedOff ? 'Kill switch is off in this environment' : statusNote(v.clinicalStatus) }}</div></div>
        @if (v.auroc !== null) {
          <div class="kpi"><div class="k-label">AUROC, last 90 days</div><div class="k-value">{{ v.auroc | number: '1.2-2' }}</div><div class="k-note">Minimum {{ v.minAuroc | number: '1.2-2' }}</div></div>
          <div class="kpi"><div class="k-label">Calibration error</div><div class="k-value">{{ v.calibrationError | number: '1.2-2' }}</div><div class="k-note">Maximum {{ v.maxCalibrationError | number: '1.2-2' }}</div></div>
        }
        @if (v.forecastAccuracy; as a) {
          <div class="kpi"><div class="k-label">Average error, last 14 days</div><div class="k-value">{{ a.samples ? a.meanAbsoluteError + ' beds' : '–' }}</div><div class="k-note">{{ a.samples }} forecasts checked</div></div>
          <div class="kpi"><div class="k-label">Actuals inside 80% range</div><div class="k-value">{{ a.samples ? pct(a.coverage) : '–' }}</div><div class="k-note">Should be close to 80%</div></div>
        }
        @if (v.drafts; as d) {
          <div class="kpi"><div class="k-label">Drafts signed</div><div class="k-value">{{ d.signed }}</div><div class="k-note">{{ d.discarded }} discarded, {{ d.awaitingReview }} awaiting review</div></div>
        }
        <div class="kpi"><div class="k-label">Next safety review</div><div class="k-value">{{ v.nextReview ? (v.nextReview | date: 'd MMM') : 'Not set' }}</div><div class="k-note">Quarterly committee</div></div>
      </section>

      <div class="cols">
        <section class="card">
          <h2>Performance across patient groups</h2>
          @if (v.subgroups.length) {
            <table>
              <thead><tr><th>Group</th><th>AUROC</th><th>Sensitivity</th><th>Calibration</th><th>Status</th></tr></thead>
              <tbody>
                @for (g of v.subgroups; track g.groupName) {
                  <tr><td>{{ g.groupName }}</td><td>{{ g.auroc | number: '1.2-2' }}</td><td>{{ pct(g.sensitivity) }}</td>
                    <td>{{ g.calibrationError | number: '1.2-2' }}</td><td><span [class]="tone(g.status)">{{ label(g.status) }}</span></td></tr>
                }
              </tbody>
            </table>
            <p class="footnote">Review: below the AUROC minimum or above the calibration maximum. Watch: within 0.02 AUROC or 0.01 calibration of a limit.</p>
          } @else { <p class="muted">No subgroup evaluation recorded for this version.</p> }
        </section>
        <aside class="card">
          <h2>Open actions</h2>
          <ul>
            @for (a of v.actions; track a.title) { <li><strong>{{ a.title }}</strong> {{ a.detail }}</li> }
            @empty { <li class="muted">None.</li> }
          </ul>
          <h2>Clinician feedback</h2>
          @if (v.feedback.total) {
            <div class="kv"><span>Risk accepted</span><strong>{{ pct(v.feedback.accepted) }}</strong></div>
            <div class="kv"><span>Dismissed with reason</span><strong>{{ pct(v.feedback.dismissed) }}</strong></div>
            <div class="kv"><span>Reported as incorrect</span><strong>{{ pct(v.feedback.reported) }}</strong></div>
            <p class="footnote">{{ v.feedback.total }} responses.</p>
          } @else { <p class="muted">No feedback recorded.</p> }
        </aside>
      </div>

      <section class="card">
        <h2>Approvals and evidence</h2>
        <table>
          <thead><tr><th>Item</th><th>Owner</th><th>Status</th><th>Date</th></tr></thead>
          <tbody>
            @for (e of v.evidence; track e.item) {
              <tr><td>{{ e.item }}</td><td>{{ e.owner }}</td><td><span [class]="tone(e.status)">{{ label(e.status) }}</span></td><td>{{ e.recordedOn | date: 'd MMM y' }}</td></tr>
            } @empty { <tr><td colspan="4">No evidence recorded yet.</td></tr> }
          </tbody>
        </table>
      </section>

      @if (v.clinicalStatus === 'LIVE' || v.clinicalStatus === 'SUSPENDED') {
        <section class="card">
          <h2>Safety kill switch</h2>
          @if (v.statusChangedBy) { <p class="muted">Last changed by {{ v.statusChangedBy }} on {{ v.statusChangedAt | date: 'd MMM y, HH:mm' }}: {{ v.statusReason }}</p> }
          <p>{{ v.clinicalStatus === 'LIVE' ? 'Suspending hides this model\\'s output from every care screen at once. Care workflows keep working.' : 'Restoring shows this model\\'s output to care teams again.' }}</p>
          <label for="why">Reason (required)</label>
          <input id="why" [(ngModel)]="reason" />
          <button [class.danger]="v.clinicalStatus === 'LIVE'" [class.pri]="v.clinicalStatus !== 'LIVE'" [disabled]="reason().trim().length < 10" (click)="toggle(v)">
            {{ v.clinicalStatus === 'LIVE' ? 'Suspend model' : 'Restore model' }}</button>
        </section>
      }
    }
  `,
  styles: `.pick { display: flex; gap: 6px; align-items: center; margin: 0 0 0 auto; font-weight: 400; }`,
})
export class GovernancePage {
  private api = inject(Api);
  protected models = signal<ModelSummary[]>([]);
  protected view = signal<ModelView | null>(null);
  protected key = signal('');
  protected reason = signal('');
  protected error = signal('');
  protected pct = pct;
  protected tone = tone;
  protected label = label;

  constructor() {
    this.api.models().subscribe({
      next: (m) => {
        this.models.set(m);
        const first = m.find((x) => x.model === 'cc-readmit') ?? m[0];
        if (first) this.open(first.model + '|' + first.version);
      },
      error: () => this.error.set('Models could not be loaded, or your role does not include model governance.'),
    });
  }

  protected statusNote(status: string): string {
    return ({ LIVE: 'Decision support only', SILENT_TRIAL: 'Runs live, hidden from care teams', SUSPENDED: 'Hidden from care teams' } as Record<string, string>)[status] ?? '';
  }

  protected open(key: string): void {
    this.key.set(key);
    const [model, version] = key.split('|');
    this.api.model(model, version).subscribe({
      next: (v) => { this.view.set(v); this.error.set(''); this.reason.set(''); },
      error: () => this.error.set('This model could not be loaded.'),
    });
  }

  protected toggle(v: ModelView): void {
    this.api.changeModelStatus(v.model, v.version, v.clinicalStatus === 'LIVE' ? 'SUSPENDED' : 'LIVE', this.reason().trim()).subscribe({
      next: () => { this.open(this.key()); this.api.models().subscribe((m) => this.models.set(m)); },
      error: () => this.error.set('The status was not changed.'),
    });
  }
}
