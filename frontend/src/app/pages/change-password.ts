import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AccountApi } from '../api';
import { AuthService } from '../auth';
import { DemoService } from '../ui';
import { firstScreen } from './login';

@Component({
  selector: 'app-change-password',
  imports: [FormsModule],
  template: `
    <section class="card login">
      <h1>Change your password</h1>
      @if (auth.mustChangePassword()) { <p>Your password is temporary. Choose a new one to continue.</p> }
      @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
      <form (ngSubmit)="submit()">
        <label for="c">Current password</label>
        <input id="c" name="current" type="password" autocomplete="current-password" [(ngModel)]="current" required />
        <label for="n">New password</label>
        <input id="n" name="next" type="password" autocomplete="new-password" [(ngModel)]="next" required />
        <label for="r">Repeat new password</label>
        <input id="r" name="repeat" type="password" autocomplete="new-password" [(ngModel)]="repeat" required />
        <p class="footnote">At least 12 characters, with three of: lower-case, upper-case, digits, symbols. It must not contain your username.</p>
        <button class="pri" type="submit" [disabled]="busy() || !current() || next().length < 12 || next() !== repeat()">Change password</button>
      </form>
      @if (next() && repeat() && next() !== repeat()) { <p class="muted" role="status">The new passwords do not match.</p> }
    </section>
  `,
  styles: `.login { max-width: 420px; margin: 40px auto; } .login input { width: 100%; } .login button { margin-top: 8px; width: 100%; }`,
})
export class ChangePasswordPage {
  private api = inject(AccountApi);
  protected auth = inject(AuthService);
  private router = inject(Router);
  private demo = inject(DemoService);
  protected current = signal('');
  protected next = signal('');
  protected repeat = signal('');
  protected busy = signal(false);
  protected error = signal('');

  protected submit(): void {
    this.busy.set(true);
    this.error.set('');
    this.api.changePassword(this.current(), this.next()).subscribe({
      next: (session) => {
        this.auth.startSession(session);
        this.demo.refresh();
        void this.router.navigateByUrl(firstScreen(session.roles));
      },
      error: (e: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set((e.error as { detail?: string } | null)?.detail ?? 'The password was not changed.');
      },
    });
  }
}
