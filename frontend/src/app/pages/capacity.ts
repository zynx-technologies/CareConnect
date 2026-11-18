import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { Api, CapacityView, SeriesPoint } from '../api';
import { label, PageTitle, pct, tone } from '../ui';

const W = 720, H = 260, L = 44, R = 64, T = 12, B = 28;

/** Occupied beds: actual (solid), forecast median (dashed), and the 80% range (band), with a capacity line. */
@Component({
  selector: 'app-forecast-chart',
  imports: [DatePipe, DecimalPipe],
  template: `
    <div class="rel">
      <svg class="chart" [attr.viewBox]="'0 0 ' + W + ' ' + H" role="img" tabindex="0"
           [attr.aria-label]="'Occupied beds, last ' + actualCount() + ' days and ' + forecastCount() + '-day forecast. Use left and right arrow keys to read values.'"
           (pointermove)="move($event)" (pointerleave)="hover.set(-1)" (keydown)="key($event)" (blur)="hover.set(-1)">
        @for (t of yTicks(); track t) {
          <line [attr.x1]="L" [attr.x2]="W - R" [attr.y1]="y(t)" [attr.y2]="y(t)" class="grid" />
          <text [attr.x]="L - 6" [attr.y]="y(t) + 4" text-anchor="end" class="axis">{{ t }}</text>
        }
        @for (i of xTicks(); track i) {
          <text [attr.x]="x(i)" [attr.y]="H - 8" text-anchor="middle" class="axis">{{ points()[i].date | date: 'd MMM' }}</text>
        }
        <line [attr.x1]="L" [attr.x2]="W - R" [attr.y1]="y(beds())" [attr.y2]="y(beds())" class="cap" />
        <text [attr.x]="W - R + 4" [attr.y]="y(beds()) + 4" class="axis">{{ beds() }} beds</text>
        <path [attr.d]="bandPath()" class="band" />
        <path [attr.d]="actualPath()" class="actual" />
        <path [attr.d]="forecastPath()" class="forecast" />
        @if (hover() >= 0) {
          <line [attr.x1]="x(hover())" [attr.x2]="x(hover())" [attr.y1]="T" [attr.y2]="H - B" class="cross" />
          <circle [attr.cx]="x(hover())" [attr.cy]="y(value(points()[hover()]))" r="4" class="dot" />
        }
      </svg>
      @if (hover() >= 0) {
        <div class="tip" [style.left.%]="x(hover()) / W * 100" [style.top.px]="4"
             [style.transform]="hover() > points().length / 2 ? 'translateX(-105%)' : 'translateX(8px)'">
          <div>{{ points()[hover()].date | date: 'EEE d MMM' }}</div>
          @if (points()[hover()].actual !== null) {
            <div><strong>{{ points()[hover()].actual }}</strong> occupied</div>
          } @else {
            <div><strong>{{ points()[hover()].p50 | number: '1.0-0' }}</strong> forecast</div>
            <div>80% range {{ points()[hover()].p10 | number: '1.0-0' }} to {{ points()[hover()].p90 | number: '1.0-0' }}</div>
          }
        </div>
      }
    </div>
    <div class="legend" aria-hidden="true">
      <span><i style="border-color: var(--series)"></i>Actual</span>
      <span><i style="border-color: var(--series); border-top-style: dashed"></i>Forecast</span>
      <span><i class="band"></i>80% range</span>
    </div>
  `,
  styles: `
    :host { --series: #2a78d6; display: block; }
    .grid { stroke: #e6ebf0; stroke-width: 1; }
    .axis { fill: #5b6472; font-size: 11px; }
    .cap { stroke: #8a94a1; stroke-width: 1; stroke-dasharray: 2 3; }
    .band { fill: rgba(42, 120, 214, .18); stroke: none; }
    .actual { fill: none; stroke: var(--series); stroke-width: 2; stroke-linejoin: round; }
    .forecast { fill: none; stroke: var(--series); stroke-width: 2; stroke-dasharray: 6 4; }
    .cross { stroke: #8a94a1; stroke-width: 1; }
    .dot { fill: var(--series); stroke: #fff; stroke-width: 2; }
    svg:focus-visible { outline: 2px solid #1d4ed8; }
  `,
})
export class ForecastChart {
  readonly points = input.required<SeriesPoint[]>();
  readonly beds = input.required<number>();
  protected readonly W = W; protected readonly H = H; protected readonly L = L; protected readonly R = R; protected readonly T = T; protected readonly B = B;
  protected hover = signal(-1);

  protected actualCount = computed(() => this.points().filter((p) => p.actual !== null).length);
  protected forecastCount = computed(() => this.points().length - this.actualCount());
  private domain = computed(() => {
    const values = this.points().flatMap((p) => [p.actual, p.p10, p.p90].filter((v): v is number => v !== null));
    values.push(this.beds());
    const lo = Math.floor((Math.min(...values) - 10) / 20) * 20;
    const hi = Math.ceil((Math.max(...values) + 10) / 20) * 20;
    return [lo, hi] as const;
  });
  protected yTicks = computed(() => {
    const [lo, hi] = this.domain();
    const step = Math.max(10, Math.ceil((hi - lo) / 4 / 10) * 10);
    const ticks: number[] = [];
    for (let t = lo; t <= hi; t += step) ticks.push(t);
    return ticks;
  });
  protected xTicks = computed(() => {
    const n = this.points().length;
    const every = Math.max(1, Math.round(n / 6));
    return Array.from({ length: n }, (_, i) => i).filter((i) => i % every === 0);
  });

  protected x(i: number): number {
    const n = Math.max(1, this.points().length - 1);
    return L + (i / n) * (W - L - R);
  }

  protected y(v: number): number {
    const [lo, hi] = this.domain();
    return T + (1 - (v - lo) / (hi - lo)) * (H - T - B);
  }

  protected value(p: SeriesPoint): number { return p.actual ?? p.p50 ?? 0; }

  private lastActual = computed(() => this.actualCount() - 1);

  protected actualPath = computed(() => this.points()
    .map((p, i) => (p.actual === null ? null : `${i === 0 ? 'M' : 'L'}${this.x(i)},${this.y(p.actual)}`)).filter(Boolean).join(''));

  protected forecastPath = computed(() => {
    const start = this.lastActual();
    if (start < 0) return '';
    const parts = [`M${this.x(start)},${this.y(this.points()[start].actual!)}`];
    this.points().forEach((p, i) => { if (p.p50 !== null) parts.push(`L${this.x(i)},${this.y(p.p50)}`); });
    return parts.join('');
  });

  protected bandPath = computed(() => {
    const start = this.lastActual();
    const f = this.points().map((p, i) => ({ p, i })).filter(({ p }) => p.p10 !== null);
    if (start < 0 || !f.length) return '';
    const anchor = `${this.x(start)},${this.y(this.points()[start].actual!)}`;
    const upper = f.map(({ p, i }) => `L${this.x(i)},${this.y(p.p90!)}`).join('');
    const lower = [...f].reverse().map(({ p, i }) => `L${this.x(i)},${this.y(p.p10!)}`).join('');
    return `M${anchor}${upper}${lower}Z`;
  });

  protected move(event: PointerEvent): void {
    const svg = event.currentTarget as SVGSVGElement;
    const box = svg.getBoundingClientRect();
    const px = ((event.clientX - box.left) / box.width) * W;
    const n = this.points().length - 1;
    this.hover.set(Math.max(0, Math.min(n, Math.round(((px - L) / (W - L - R)) * n))));
  }

  protected key(event: KeyboardEvent): void {
    const n = this.points().length - 1;
    if (event.key === 'ArrowRight') { this.hover.set(Math.min(n, this.hover() + 1)); event.preventDefault(); }
    if (event.key === 'ArrowLeft') { this.hover.set(Math.max(0, this.hover() < 0 ? n : this.hover() - 1)); event.preventDefault(); }
  }
}

@Component({
  selector: 'app-capacity',
  imports: [DatePipe, DecimalPipe, PageTitle, ForecastChart],
  template: `
    <app-page-title title="Bed and staffing forecast" />
    @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
    @if (view(); as v) {
      <section class="kpis">
        <div class="kpi"><div class="k-label">Beds occupied</div><div class="k-value">{{ v.summary.occupied }} / {{ v.summary.beds }}</div>
          <div class="k-note">{{ pct(v.summary.beds ? v.summary.occupied / v.summary.beds : null) }} occupancy</div></div>
        <div class="kpi"><div class="k-label">Forecast peak, 7 days</div>
          @if (v.summary.peak; as p) {
            <div class="k-value">{{ p.p50 | number: '1.0-0' }}</div>
            <div class="k-note">{{ p.date | date: 'EEEE' }}, range {{ p.p10 | number: '1.0-0' }} to {{ p.p90 | number: '1.0-0' }}</div>
          } @else { <div class="k-value">–</div><div class="k-note">Needs 14 days of census</div> }
        </div>
        <div class="kpi"><div class="k-label">Expected discharges today</div><div class="k-value">{{ v.summary.expectedDischargesToday }}</div>
          <div class="k-note">{{ v.summary.awaitingTransport }} awaiting transport</div></div>
        <div class="kpi"><div class="k-label">Nursing gap, next 3 days</div><div class="k-value">{{ v.summary.nursingGapShifts }} shifts</div>
          <div class="k-note">{{ v.summary.nursingGapLabel }}</div></div>
      </section>

      <div class="cols">
        <section class="card">
          <h2>Occupied beds: actual and forecast</h2>
          @if (v.series.length > 1) {
            <app-forecast-chart [points]="v.series" [beds]="v.summary.beds" />
            <details>
              <summary>Show as a table</summary>
              <table>
                <thead><tr><th>Date</th><th>Actual</th><th>Forecast</th><th>80% range</th></tr></thead>
                <tbody>
                  @for (p of v.series; track p.date) {
                    <tr><td>{{ p.date | date: 'EEE d MMM' }}</td><td>{{ p.actual ?? '' }}</td><td>{{ p.p50 === null ? '' : (p.p50 | number: '1.0-0') }}</td>
                      <td>{{ p.p10 === null ? '' : (p.p10 | number: '1.0-0') + ' to ' + (p.p90 | number: '1.0-0') }}</td></tr>
                  }
                </tbody>
              </table>
            </details>
          } @else { <p class="muted">No census history yet.</p> }
          <p class="footnote">
            {{ v.modelVersion ?? 'No forecast yet' }}{{ v.generatedAt ? ', updated ' + (v.generatedAt | date: 'd MMM HH:mm') : '' }}.
            @if (v.accuracy.samples) {
              Last 14 days: average error {{ v.accuracy.meanAbsoluteError }} beds; {{ pct(v.accuracy.coverage) }} of actual values inside the 80% range.
            }
          </p>
        </section>
        <section class="card">
          <h2>Ward pressure{{ v.summary.peak ? ', ' + (v.summary.peak.date | date: 'EEEE') : '' }}</h2>
          <table>
            <thead><tr><th>Ward</th><th>Forecast</th><th>Status</th></tr></thead>
            <tbody>
              @for (w of v.wards; track w.code) {
                <tr><td>{{ w.name }}</td><td>{{ pct(w.forecastShare) }}</td><td><span [class]="tone(w.status)">{{ label(w.status) }}</span></td></tr>
              }
            </tbody>
          </table>
          <p class="footnote">High at 95% or more; watch at 92% or more.</p>
        </section>
      </div>

      <section class="card">
        <h2>Suggested actions</h2>
        <table>
          <thead><tr><th>Action</th><th>Effect</th><th>Owner</th><th><span class="sr">Plan</span></th></tr></thead>
          <tbody>
            @for (a of v.actions; track a.id) {
              <tr>
                <td>{{ a.action }}</td><td>{{ a.effect }}</td><td>{{ a.owner }}</td>
                <td>@if (a.status === 'OPEN') { <button (click)="plan(a.id)">Plan</button> }
                    @else { <span class="badge">Planned</span> <span class="muted">{{ a.plannedBy }}</span> }</td>
              </tr>
            } @empty { <tr><td colspan="4">No suggested actions.</td></tr> }
          </tbody>
        </table>
      </section>
    } @else if (!error()) { <p>Loading forecast.</p> }
  `,
})
export class CapacityPage {
  private api = inject(Api);
  protected view = signal<CapacityView | null>(null);
  protected error = signal('');
  protected pct = pct;
  protected tone = tone;
  protected label = label;

  constructor() { this.load(); }

  private load(): void {
    this.api.capacity().subscribe({
      next: (v) => this.view.set(v),
      error: () => this.error.set('The forecast could not be loaded, or your role does not include capacity planning.'),
    });
  }

  protected plan(id: string): void {
    this.api.planAction(id).subscribe({ next: () => this.load(), error: () => this.error.set('The action was not saved.') });
  }
}
