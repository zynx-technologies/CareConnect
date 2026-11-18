import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AccountApi } from '../api';
import { AuthService } from '../auth';
import { DemoService } from '../ui';
import { NAV } from '../nav';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `
    <section class="card login">
      <h1>Sign in to CareConnect</h1>
      @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
      <form (ngSubmit)="submit()">
        <label for="u">Username</label>
        <input id="u" name="username" autocomplete="username" [(ngModel)]="username" required />
        <label for="p">Password</label>
        <input id="p" name="password" type="password" autocomplete="current-password" [(ngModel)]="password" required />
        <button class="pri" type="submit" [disabled]="busy() || !username().trim() || !password()">Sign in</button>
      </form>
      <p class="footnote">Every sign-in is logged. Five wrong passwords lock the account for 15 minutes.</p>
    </section>
  `,
  styles: `.login { max-width: 380px; margin: 40px auto; } .login input { width: 100%; } .login button { margin-top: 12px; width: 100%; }`,
})
export class LoginPage {
  private api = inject(AccountApi);
  private auth = inject(AuthService);
  private router = inject(Router);
  private demo = inject(DemoService);
  protected username = signal('');
  protected password = signal('');
  protected busy = signal(false);
  protected error = signal('');

  protected submit(): void {
    this.busy.set(true);
    this.error.set('');
    this.api.login(this.username().trim(), this.password()).subscribe({
      next: (session) => {
        this.auth.startSession(session);
        this.demo.refresh();
        this.password.set('');
        void this.router.navigateByUrl(session.mustChangePassword ? '/change-password' : firstScreen(session.roles));
      },
      error: (e: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(e.status === 401 ? 'The username or password is incorrect, or the account is locked.' : 'Sign-in is unavailable. Try again shortly.');
      },
    });
  }
}

export function firstScreen(roles: string[]): string {
  return NAV.find((n) => n.roles.some((r) => roles.includes(r)))?.path ?? '/no-access';
}
