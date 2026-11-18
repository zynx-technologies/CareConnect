import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { PublicClientApplication } from '@azure/msal-browser';
import { APP_SETTINGS } from './settings';

export const DEV_USERS = [
  { user: 'r.coordinator@example.test', roles: 'COORDINATOR', label: 'R. Coordinator (coordinator)' },
  { user: 'p.example@example.test', roles: 'CLINICIAN', label: 'Dr P. Example (clinician)' },
  { user: 'triage@example.test', roles: 'TRIAGE', label: 'Triage clinician' },
  { user: 'bed.manager@example.test', roles: 'MANAGER', label: 'Bed and nursing manager' },
  { user: 'safety.officer@example.test', roles: 'SAFETY', label: 'Clinical safety officer' },
  { user: 'privacy.officer@example.test', roles: 'PRIVACY', label: 'Privacy officer' },
  { user: 'r.coordinator@example.test', roles: 'COORDINATOR,CLINICIAN,TRIAGE,MANAGER,SAFETY,PRIVACY', label: 'All roles (demo walkthrough)' },
];

/** What the API returns on sign-in with a CareConnect account. */
export interface Session {
  token: string | null;
  expiresAt: string | null;
  username: string;
  displayName: string;
  roles: string[];
  mustChangePassword: boolean;
}

const SESSION_KEY = 'careconnect.session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private settings = inject(APP_SETTINGS);
  private msal?: PublicClientApplication;

  readonly isDev = this.settings.authMode === 'dev';
  readonly isDatabase = this.settings.authMode === 'database';
  readonly devUser = signal(DEV_USERS[0]);
  readonly displayName = signal('');
  readonly username = signal('');
  readonly roles = signal<string[]>([]);
  readonly mustChangePassword = signal(false);
  private token = '';

  /** Signs in before the first screen appears. In Entra mode this redirects if there is no session. */
  async initialise(): Promise<void> {
    if (this.isDev) {
      return;
    }
    if (this.isDatabase) {
      this.restore();
      return;
    }
    this.msal = new PublicClientApplication({
      auth: {
        clientId: this.settings.clientId!,
        authority: `https://login.microsoftonline.com/${this.settings.tenantId}`,
        redirectUri: window.location.origin,
      },
      cache: { cacheLocation: 'sessionStorage' },
    });
    await this.msal.initialize();
    const result = await this.msal.handleRedirectPromise();
    const account = result?.account ?? this.msal.getAllAccounts()[0];
    if (!account) {
      await this.msal.loginRedirect({ scopes: [this.settings.apiScope!] });
      return;
    }
    this.msal.setActiveAccount(account);
    this.displayName.set(account.name ?? account.username);
    this.roles.set((account.idTokenClaims?.roles as string[] | undefined) ?? []);
  }

  /** True when the person can use the portal. Entra and local modes sign in before the app starts. */
  signedIn(): boolean {
    return !this.isDatabase || !!this.token;
  }

  hasRole(role: string): boolean {
    return this.isDev ? this.devUser().roles.split(',').includes(role) : this.roles().includes(role);
  }

  /** Stores a session from the API. The token lives in session storage, so closing the tab signs out. */
  startSession(session: Session): void {
    if (session.token) this.token = session.token;
    this.username.set(session.username);
    this.displayName.set(session.displayName);
    this.roles.set(session.roles);
    this.mustChangePassword.set(session.mustChangePassword);
    try {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify({ ...session, token: this.token }));
    } catch {
      // Storage can be blocked; the session then lasts only until the page reloads.
    }
  }

  signOut(): void {
    this.token = '';
    this.username.set('');
    this.displayName.set('');
    this.roles.set([]);
    this.mustChangePassword.set(false);
    try { sessionStorage.removeItem(SESSION_KEY); } catch { /* nothing stored */ }
  }

  private restore(): void {
    try {
      const stored = JSON.parse(sessionStorage.getItem(SESSION_KEY) ?? 'null') as Session | null;
      if (stored?.token && stored.expiresAt && new Date(stored.expiresAt).getTime() > Date.now()) {
        this.startSession(stored);
      } else {
        sessionStorage.removeItem(SESSION_KEY);
      }
    } catch {
      this.signOut();
    }
  }

  async token$(): Promise<string> {
    if (this.isDatabase) {
      return this.token;
    }
    const scopes = [this.settings.apiScope!];
    try {
      return (await this.msal!.acquireTokenSilent({ scopes })).accessToken;
    } catch (error) {
      await this.msal!.acquireTokenRedirect({ scopes });
      throw error;
    }
  }
}

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) {
    return next(request);
  }
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isDev) {
    const dev = auth.devUser();
    return next(request.clone({ setHeaders: { 'X-Dev-User': dev.user, 'X-Dev-Roles': dev.roles } }));
  }
  if (auth.isDatabase && request.url === '/api/v1/auth/login') {
    return next(request);
  }
  return from(auth.token$()).pipe(
    switchMap((token) => next(token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request)),
    catchError((error: unknown) => {
      // An expired or revoked session goes back to the sign-in screen.
      if (auth.isDatabase && error instanceof HttpErrorResponse && error.status === 401) {
        auth.signOut();
        void router.navigateByUrl('/login');
      }
      return throwError(() => error);
    }),
  );
};
