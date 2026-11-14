import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'worklist' },
  { path: 'worklist', loadComponent: () => import('./pages/worklist').then((m) => m.WorklistPage) },
  { path: 'patients', loadComponent: () => import('./pages/patients').then((m) => m.PatientsPage) },
  { path: 'patients/:id', loadComponent: () => import('./pages/patient-record').then((m) => m.PatientRecordPage) },
  { path: 'referrals', loadComponent: () => import('./pages/referrals').then((m) => m.ReferralsPage) },
  { path: 'drafts/:id', loadComponent: () => import('./pages/draft-review').then((m) => m.DraftReviewPage) },
  { path: '**', redirectTo: 'worklist' },
];
