import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService, DEV_USERS } from './auth';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <a class="skip" href="#main">Skip to content</a>
    <nav class="side" aria-label="Main">
      <div class="logo">CareConnect</div>
      <a routerLink="/worklist" routerLinkActive="on">Worklist</a>
      <a routerLink="/patients" routerLinkActive="on">Patients</a>
      <a routerLink="/referrals" routerLinkActive="on">Referrals</a>
    </nav>
    <div class="main">
      <header class="top">
        @if (auth.isDev) {
          <label>Local sign-in
            <select (change)="switchUser($event)">
              @for (u of users; track u.user) { <option [value]="u.user" [selected]="u.user === auth.devUser().user">{{ u.label }}</option> }
            </select>
          </label>
          <span class="badge amber">Local mode, synthetic data</span>
        } @else {
          <span>{{ auth.displayName() }}</span>
        }
      </header>
      <main id="main" tabindex="-1"><router-outlet /></main>
    </div>
  `,
})
export class App {
  protected auth = inject(AuthService);
  private router = inject(Router);
  protected users = DEV_USERS;

  protected switchUser(event: Event): void {
    const selected = DEV_USERS.find((u) => u.user === (event.target as HTMLSelectElement).value)!;
    this.auth.devUser.set(selected);
    void this.router.navigateByUrl(selected.roles === 'TRIAGE' ? '/referrals' : '/worklist');
  }
}
