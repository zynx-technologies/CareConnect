import { HttpInterceptorFn } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { from, switchMap } from 'rxjs';
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

@Injectable({ providedIn: 'root' })
export class AuthService {
  private settings = inject(APP_SETTINGS);
  private msal?: PublicClientApplication;

  readonly isDev = this.settings.authMode === 'dev';
  readonly devUser = signal(DEV_USERS[0]);
  readonly displayName = signal('');
  readonly roles = signal<string[]>([]);

  /** Signs in before the first screen appears. In Entra mode this redirects if there is no session. */
  async initialise(): Promise<void> {
    if (this.isDev) {
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

  hasRole(role: string): boolean {
    return this.isDev ? this.devUser().roles.split(',').includes(role) : this.roles().includes(role);
  }

  async token(): Promise<string> {
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
  if (auth.isDev) {
    const dev = auth.devUser();
    return next(request.clone({ setHeaders: { 'X-Dev-User': dev.user, 'X-Dev-Roles': dev.roles } }));
  }
  return from(auth.token()).pipe(
    switchMap((token) => next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }))),
  );
};
