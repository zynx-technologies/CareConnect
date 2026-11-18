import { inject } from '@angular/core';
import { Router, Routes } from '@angular/router';
import { AuthService } from './auth';
import { NAV } from './nav';

/** Opens the first screen the person's roles allow. */
const home = () => {
  const auth = inject(AuthService);
  const first = NAV.find((n) => n.roles.some((r) => auth.hasRole(r)));
  return inject(Router).parseUrl(first?.path ?? '/no-access');
};

export const routes: Routes = [
  { path: '', pathMatch: 'full', canActivate: [home], children: [] },
  { path: 'worklist', loadComponent: () => import('./pages/worklist').then((m) => m.WorklistPage)},
  { path: 'patients', loadComponent: () => import('./pages/patients').then((m) => m.PatientsPage) },
  { path: 'patients/:id', loadComponent: () => import('./pages/patient-record').then((m) => m.PatientRecordPage) },
  { path: 'note-review', loadComponent: () => import('./pages/note-review').then((m) => m.NoteReviewPage) },
  { path: 'drafts/:id', loadComponent: () => import('./pages/draft-review').then((m) => m.DraftReviewPage) },
  { path: 'referrals', loadComponent: () => import('./pages/referrals').then((m) => m.ReferralsPage) },
  { path: 'capacity', loadComponent: () => import('./pages/capacity').then((m) => m.CapacityPage) },
  { path: 'governance', loadComponent: () => import('./pages/governance').then((m) => m.GovernancePage) },
  { path: 'audit', loadComponent: () => import('./pages/audit').then((m) => m.AuditPage) },
  { path: 'no-access', loadComponent: () => import('./pages/no-access').then((m) => m.NoAccessPage) },
  { path: '**', redirectTo: '' },
];
