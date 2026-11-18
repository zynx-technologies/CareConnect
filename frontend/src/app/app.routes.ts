import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { AuthService } from './auth';
import { NAV } from './nav';

/** With CareConnect accounts, every screen needs a session, and a temporary password must be changed first. */
const signedIn: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.signedIn()) return router.parseUrl('/login');
  if (auth.mustChangePassword()) return router.parseUrl('/change-password');
  return true;
};

/** Opens the first screen the person's roles allow. */
const home: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.signedIn()) return router.parseUrl('/login');
  if (auth.mustChangePassword()) return router.parseUrl('/change-password');
  const first = NAV.find((n) => n.roles.some((r) => auth.hasRole(r)));
  return router.parseUrl(first?.path ?? '/no-access');
};

const needsSession: CanActivateFn = () => (inject(AuthService).signedIn() ? true : inject(Router).parseUrl('/login'));

const screens: Routes = [
  { path: 'worklist', loadComponent: () => import('./pages/worklist').then((m) => m.WorklistPage) },
  { path: 'patients', loadComponent: () => import('./pages/patients').then((m) => m.PatientsPage) },
  { path: 'patients/:id', loadComponent: () => import('./pages/patient-record').then((m) => m.PatientRecordPage) },
  { path: 'note-review', loadComponent: () => import('./pages/note-review').then((m) => m.NoteReviewPage) },
  { path: 'drafts/:id', loadComponent: () => import('./pages/draft-review').then((m) => m.DraftReviewPage) },
  { path: 'referrals', loadComponent: () => import('./pages/referrals').then((m) => m.ReferralsPage) },
  { path: 'capacity', loadComponent: () => import('./pages/capacity').then((m) => m.CapacityPage) },
  { path: 'governance', loadComponent: () => import('./pages/governance').then((m) => m.GovernancePage) },
  { path: 'audit', loadComponent: () => import('./pages/audit').then((m) => m.AuditPage) },
  { path: 'users', loadComponent: () => import('./pages/users').then((m) => m.UsersPage) },
  { path: 'no-access', loadComponent: () => import('./pages/no-access').then((m) => m.NoAccessPage) },
];

export const routes: Routes = [
  { path: '', pathMatch: 'full', canActivate: [home], children: [] },
  { path: 'login', loadComponent: () => import('./pages/login').then((m) => m.LoginPage) },
  { path: 'change-password', canActivate: [needsSession], loadComponent: () => import('./pages/change-password').then((m) => m.ChangePasswordPage) },
  ...screens.map((r) => ({ ...r, canActivate: [signedIn] })),
  { path: '**', redirectTo: '' },
];
