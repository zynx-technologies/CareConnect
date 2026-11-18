import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Api } from './api';
import { AuthService, DEV_USERS } from './auth';
import { NAV } from './nav';
import { DemoService } from './ui';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <a class="skip" href="#main">Skip to content</a>
    <nav class="side" aria-label="Main">
      <div class="logo">CareConnect</div>
      @for (item of nav(); track item.path) {
        <a [routerLink]="item.path" routerLinkActive="on">{{ item.label }}</a>
      } @empty {
        <p class="side-note">Your account has no CareConnect role. Ask the service desk for access.</p>
      }
    </nav>
    <div class="main">
      <header class="top">
        @if (auth.isDev) {
          <label>Local sign-in
            <select (change)="switchUser($event)">
              @for (u of users; track u.label) { <option [value]="u.label" [selected]="u.label === auth.devUser().label">{{ u.label }}</option> }
            </select>
          </label>
          <span class="badge amber">Local mode</span>
        } @else {
          <span>{{ auth.displayName() }}</span>
        }
        <span class="muted">{{ roleText() }}</span>
      </header>
      @if (demo.synthetic() && !demo.onCareTeam() && careStaff()) {
        <div class="join" role="status">
          <span>You are not on the care team for any synthetic patient yet, so your worklist is empty.</span>
          <button class="pri" (click)="join()">Join the demo care team</button>
        </div>
      }
      <main id="main" tabindex="-1"><router-outlet /></main>
    </div>
  `,
})
export class App {
  protected auth = inject(AuthService);
  protected demo = inject(DemoService);
  private api = inject(Api);
  private router = inject(Router);
  protected users = DEV_USERS;
  protected nav = computed(() => NAV.filter((n) => n.roles.some((r) => this.auth.hasRole(r))));
  protected careStaff = computed(() => this.auth.hasRole('COORDINATOR') || this.auth.hasRole('CLINICIAN'));
  protected roleText = computed(() => {
    const names: Record<string, string> = { COORDINATOR: 'Care coordination', CLINICIAN: 'Clinician', TRIAGE: 'Referral triage', MANAGER: 'Bed management', SAFETY: 'Clinical safety', PRIVACY: 'Privacy', INTEGRATION: 'Integration' };
    const roles = this.auth.isDev ? this.auth.devUser().roles.split(',') : this.auth.roles();
    return roles.map((r) => names[r] ?? r).join(' · ');
  });

  constructor() {
    this.demo.check();
  }

  protected switchUser(event: Event): void {
    const selected = DEV_USERS.find((u) => u.label === (event.target as HTMLSelectElement).value)!;
    this.auth.devUser.set(selected);
    this.demo.refresh();
    const first = NAV.find((n) => n.roles.some((r) => selected.roles.split(',').includes(r)));
    void this.router.navigateByUrl(first?.path ?? '/');
  }

  protected join(): void {
    this.api.joinDemoCareTeam().subscribe({
      next: () => window.location.assign('/worklist'),
    });
  }
}
