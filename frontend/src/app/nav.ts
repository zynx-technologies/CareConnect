/** Main navigation, in the order the design shows it. Each item appears only for the roles that can use it. */
export const NAV = [
  { path: '/worklist', label: 'Worklist', roles: ['COORDINATOR', 'CLINICIAN'] },
  { path: '/patients', label: 'Patients', roles: ['COORDINATOR', 'CLINICIAN'] },
  { path: '/note-review', label: 'Note review', roles: ['CLINICIAN'] },
  { path: '/referrals', label: 'Referrals', roles: ['TRIAGE'] },
  { path: '/capacity', label: 'Capacity', roles: ['MANAGER'] },
  { path: '/governance', label: 'Model governance', roles: ['SAFETY'] },
  { path: '/audit', label: 'Audit', roles: ['PRIVACY'] },
  { path: '/users', label: 'Users', roles: ['ADMIN'] },
];
