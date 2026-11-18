import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { authInterceptor } from './auth';
import { APP_SETTINGS } from './settings';
import { Api, Draft } from './api';
import { DraftReviewPage } from './pages/draft-review';
import { ForecastChart } from './pages/capacity';
import { NAV } from './nav';
import { age, label, tone } from './ui';

const draft: Draft = {
  id: 'd1', patientId: 'p1', status: 'DRAFT', content: 'Summary', generator: 'test', signedBy: null, signedAt: null,
  statements: [{ section: 'Medicines', text: 'Doxycycline for 50 days', sourceIds: ['n1'], warnings: ['Number 50 does not appear in the cited notes'] }],
};

describe('auth interceptor', () => {
  it('adds local identity to API calls only', () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: APP_SETTINGS, useValue: { authMode: 'dev' } },
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    const http = TestBed.inject(HttpClient);
    const mock = TestBed.inject(HttpTestingController);
    http.get('/api/v1/worklist').subscribe();
    http.get('/config.json').subscribe();
    expect(mock.expectOne('/api/v1/worklist').request.headers.get('X-Dev-User')).toBe('r.coordinator@example.test');
    expect(mock.expectOne('/config.json').request.headers.has('X-Dev-User')).toBe(false);
  });
});

describe('draft review', () => {
  function create(signDraft = vi.fn(() => of({ ...draft, status: 'SIGNED', signedBy: 'dr' }))) {
    TestBed.configureTestingModule({
      imports: [DraftReviewPage],
      providers: [
        provideRouter([]),
        { provide: Api, useValue: { draft: () => of(draft), signDraft, patient: () => of({ notes: [] }) } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'd1' }) } } },
      ],
    });
    const fixture = TestBed.createComponent(DraftReviewPage);
    fixture.detectChanges();
    return { fixture, signDraft, el: fixture.nativeElement as HTMLElement };
  }

  it('labels the draft, and shows check warnings', () => {
    const { el } = create();
    expect(el.textContent).toContain('not yet part of the record');
    expect(el.textContent).toContain('Number 50 does not appear');
  });

  it('cannot be signed until the clinician confirms review', async () => {
    const { fixture, el, signDraft } = create();
    await fixture.whenStable();
    fixture.detectChanges();
    const sign = el.querySelector<HTMLButtonElement>('button.pri')!;
    expect(sign.disabled).toBe(true);
    el.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(sign.disabled).toBe(false);
    sign.click();
    expect(signDraft).toHaveBeenCalledWith('d1', 'Summary', true);
  });
});

describe('screen helpers', () => {
  it('always pairs a colour with a text label', () => {
    expect(label('HIGH')).toBe('High');
    expect(label(null)).toBe('Not scored');
    expect(tone('HIGH')).toContain('red');
    expect(tone('WATCH')).toContain('amber');
    expect(age(null)).toBe('age not recorded');
  });

  it('shows each screen only to the roles that use it', () => {
    const forRoles = (roles: string[]) => NAV.filter((n) => n.roles.some((r) => roles.includes(r))).map((n) => n.label);
    expect(forRoles(['MANAGER'])).toEqual(['Capacity']);
    expect(forRoles(['COORDINATOR'])).toEqual(['Worklist', 'Patients']);
    expect(forRoles(['SAFETY', 'PRIVACY'])).toEqual(['Model governance', 'Audit']);
  });
});

describe('forecast chart', () => {
  it('draws actuals, a dashed forecast, and a range band joined to the last actual', () => {
    TestBed.configureTestingModule({ imports: [ForecastChart] });
    const fixture = TestBed.createComponent(ForecastChart);
    fixture.componentRef.setInput('beds', 450);
    fixture.componentRef.setInput('points', [
      { date: '2026-11-17', actual: 410, p10: null, p50: null, p90: null },
      { date: '2026-11-18', actual: 412, p10: null, p50: null, p90: null },
      { date: '2026-11-19', actual: null, p10: 405, p50: 415, p90: 425 },
    ]);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('path.actual')!.getAttribute('d')).toMatch(/^M.*L/);
    expect(el.querySelector('path.band')!.getAttribute('d')).toMatch(/Z$/);
    expect(el.textContent).toContain('450 beds');
  });
});
