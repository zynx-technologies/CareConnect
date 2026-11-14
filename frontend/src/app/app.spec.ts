import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { authInterceptor } from './auth';
import { APP_SETTINGS } from './settings';
import { Api, Draft } from './api';
import { DraftReviewPage } from './pages/draft-review';

const draft: Draft = {
  id: 'd1', patientId: 'p1', status: 'DRAFT', content: 'Summary', generator: 'test', signedBy: null, signedAt: null,
  statements: [{ text: 'Doxycycline for 50 days', sourceIds: ['n1'], warnings: ['Number 50 does not appear in the cited notes'] }],
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
