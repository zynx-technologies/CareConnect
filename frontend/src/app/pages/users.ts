import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AccountApi, AppUser } from '../api';
import { AuthService } from '../auth';
import { PageTitle } from '../ui';

const ROLES = [
  { value: 'COORDINATOR', label: 'Care coordinator' },
  { value: 'CLINICIAN', label: 'Clinician' },
  { value: 'TRIAGE', label: 'Referral triage' },
  { value: 'MANAGER', label: 'Bed and nursing manager' },
  { value: 'SAFETY', label: 'Clinical safety officer' },
  { value: 'PRIVACY', label: 'Privacy officer' },
  { value: 'ADMIN', label: 'User administrator' },
];

@Component({
  selector: 'app-users',
  imports: [DatePipe, FormsModule, PageTitle],
  template: `
    <app-page-title title="Users" />
    @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
    @if (message()) { <p class="badge" role="status">{{ message() }}</p> }

    <section class="card">
      <h2>CareConnect accounts</h2>
      <table>
        <thead><tr><th>Username</th><th>Name</th><th>Roles</th><th>Status</th><th>Last sign-in</th><th><span class="sr">Actions</span></th></tr></thead>
        <tbody>
          @for (u of users(); track u.id) {
            <tr>
              <td>{{ u.username }}</td>
              <td>{{ u.displayName }}</td>
              <td>{{ roleNames(u.roles) }}</td>
              <td>
                @if (!u.enabled) { <span class="badge red">Disabled</span> }
                @else if (locked(u)) { <span class="badge red">Locked</span> }
                @else if (u.mustChangePassword) { <span class="badge amber">Temporary password</span> }
                @else { <span class="badge">Active</span> }
              </td>
              <td>{{ u.lastLoginAt ? (u.lastLoginAt | date: 'd MMM, HH:mm') : 'Never' }}</td>
              <td><button (click)="edit(u)">Edit</button></td>
            </tr>
          }
        </tbody>
      </table>
    </section>

    <section class="card">
      <h2>{{ editing() ? 'Edit ' + editing()!.username : 'Add a user' }}</h2>
      <div class="form">
        @if (!editing()) {
          <div><label for="un">Username</label><input id="un" [(ngModel)]="username" autocomplete="off" /></div>
        }
        <div><label for="dn">Name</label><input id="dn" [(ngModel)]="displayName" /></div>
        <fieldset>
          <legend>Roles</legend>
          @for (r of roleOptions; track r.value) {
            <label class="check"><input type="checkbox" [checked]="selected().includes(r.value)" (change)="toggle(r.value)" /> {{ r.label }}</label>
          }
        </fieldset>
        @if (editing()) {
          <label class="check"><input type="checkbox" [(ngModel)]="enabled" [disabled]="editing()!.username === auth.username()" /> Account enabled</label>
        }
        <div>
          <label for="tp">{{ editing() ? 'New temporary password (optional)' : 'Temporary password' }}</label>
          <input id="tp" type="text" [(ngModel)]="temporary" autocomplete="off" />
          <button type="button" (click)="generate()">Generate</button>
          <p class="footnote">The person must change it at first sign-in. Share it with them directly, not by email.</p>
        </div>
      </div>
      <div class="actions-row">
        @if (editing()) {
          <button class="pri" [disabled]="!displayName().trim() || !selected().length" (click)="save()">Save changes</button>
          <button (click)="reset()">Cancel</button>
        } @else {
          <button class="pri" [disabled]="!username().trim() || !displayName().trim() || !selected().length || temporary().length < 12" (click)="create()">Add user</button>
        }
      </div>
    </section>
  `,
  styles: `.form { display: grid; gap: 8px; max-width: 520px; } .form input[type=text], .form input:not([type]) { width: 100%; }
    fieldset { border: 1px solid var(--line); border-radius: 6px; padding: 6px 10px; } legend { font-weight: 600; font-size: 12px; }
    label.check { margin: 4px 0; }`,
})
export class UsersPage {
  private api = inject(AccountApi);
  protected auth = inject(AuthService);
  protected roleOptions = ROLES;
  protected users = signal<AppUser[]>([]);
  protected editing = signal<AppUser | null>(null);
  protected username = signal('');
  protected displayName = signal('');
  protected selected = signal<string[]>([]);
  protected enabled = signal(true);
  protected temporary = signal('');
  protected error = signal('');
  protected message = signal('');

  constructor() { this.load(); }

  private load(): void {
    this.api.users().subscribe({ next: (u) => this.users.set(u), error: () => this.error.set('Users could not be loaded, or your role does not include user administration.') });
  }

  protected roleNames(roles: string[]): string {
    return roles.map((r) => ROLES.find((x) => x.value === r)?.label ?? r).join(', ');
  }

  protected locked(u: AppUser): boolean { return !!u.lockedUntil && new Date(u.lockedUntil).getTime() > Date.now(); }

  protected toggle(role: string): void {
    this.selected.update((s) => (s.includes(role) ? s.filter((r) => r !== role) : [...s, role]));
  }

  /** A random temporary password that meets the policy. Made in the browser, never sent anywhere but the API. */
  protected generate(): void {
    const sets = ['ABCDEFGHJKLMNPQRSTUVWXYZ', 'abcdefghijkmnopqrstuvwxyz', '23456789', '!#%+-=?'];
    const pick = (s: string) => s[crypto.getRandomValues(new Uint32Array(1))[0] % s.length];
    const chars = [...sets.map(pick), ...Array.from({ length: 12 }, () => pick(sets.join('')))];
    for (let i = chars.length - 1; i > 0; i--) {
      const j = crypto.getRandomValues(new Uint32Array(1))[0] % (i + 1);
      [chars[i], chars[j]] = [chars[j], chars[i]];
    }
    this.temporary.set(chars.join(''));
  }

  protected edit(u: AppUser): void {
    this.editing.set(u);
    this.displayName.set(u.displayName);
    this.selected.set([...u.roles]);
    this.enabled.set(u.enabled);
    this.temporary.set('');
    this.message.set('');
  }

  protected reset(): void {
    this.editing.set(null);
    this.username.set('');
    this.displayName.set('');
    this.selected.set([]);
    this.enabled.set(true);
    this.temporary.set('');
  }

  protected create(): void {
    this.api.createUser(this.username().trim(), this.displayName().trim(), this.selected(), this.temporary()).subscribe({
      next: (u) => { this.message.set(`Added ${u.username}. They must change the temporary password at first sign-in.`); this.reset(); this.load(); },
      error: (e: HttpErrorResponse) => this.error.set(detail(e, 'The user was not added.')),
    });
  }

  protected save(): void {
    const u = this.editing()!;
    this.api.updateUser(u.id, this.displayName().trim(), this.selected(), this.enabled()).subscribe({
      next: () => {
        if (this.temporary()) {
          this.api.resetPassword(u.id, this.temporary()).subscribe({
            next: () => { this.message.set(`Saved ${u.username} and set a temporary password.`); this.reset(); this.load(); },
            error: (e: HttpErrorResponse) => this.error.set(detail(e, 'Saved, but the password was not reset.')),
          });
        } else {
          this.message.set(`Saved ${u.username}.`);
          this.reset();
          this.load();
        }
      },
      error: (e: HttpErrorResponse) => this.error.set(detail(e, 'The changes were not saved.')),
    });
  }
}

function detail(e: HttpErrorResponse, fallback: string): string {
  return (e.error as { detail?: string } | null)?.detail ?? fallback;
}
