import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export type Level = 'LOW' | 'MEDIUM' | 'HIGH';

export interface WorklistItem { id: string; patientId: string; mrn: string; familyName: string; givenName: string; title: string; reason: string | null; team: string | null; status: string; dueAt: string; assignedTo: string | null; readmissionRisk: Level | null; }
export interface TeamLoad { team: string; tasks: number; }
export interface InterfaceFeed { code: string; name: string; lastMessageAt: string | null; delayMinutes: number | null; status: 'LIVE' | 'DELAYED' | 'NO_DATA'; }
export interface WorklistSummary { openTasks: number; overdueTasks: number; referralsAwaitingTriage: number; oldestReferralDays: number | null; dischargesNext24h: number; dischargesWithoutFollowUpPlan: number; followUpWithin7Days: number | null; followUpTarget: number; followUpSample: number; teamLoad: TeamLoad[]; interfaces: InterfaceFeed[]; }

export interface Patient { id: string; mrn: string; familyName: string; givenName: string; birthDate: string | null; }
export interface PatientListItem extends Patient { ward: string | null; encounterStatus: string | null; readmissionRisk: Level | null; }
export interface Encounter { id: string; visitNumber: string; status: string; wardCode: string | null; admittedAt: string | null; dischargedAt: string | null; expectedDischargeAt: string | null; }
export interface Note { id: string; noteType: string; author: string; body: string; writtenAt: string; }
export interface Task { id: string; title: string; reason: string | null; status: string; dueAt: string; assignedTo: string | null; }
export interface Factor { label: string; impact: string; strength: number; }
export interface Risk { id: string; model: string; modelVersion: string; level: Level; score: number; factors: Factor[]; scoredAt: string; }
export interface PatientRecord {
  patient: Patient; careConsent: string; consents: { purpose: string; status: string; updatedAt: string }[];
  alerts: { category: string; detail: string; severity: string }[];
  careTeam: { userId: string; displayName: string | null; teamRole: string }[];
  encounters: Encounter[]; timeline: { occurredAt: string; description: string; source: string }[];
  medicines: { name: string; dose: string; status: string }[]; notes: Note[]; tasks: Task[];
  latestSummary: Note | null; readmissionRisk: Risk | null;
}

export interface Referral { id: string; patientId: string; mrn: string; familyName: string; givenName: string; service: string; source: string | null; reason: string; urgency: string; suggestedUrgency: string | null; status: string; createdAt: string; triagedBy: string | null; triageReason: string | null; appointmentAt: string | null; bookedService: string | null; bookingStatus: string | null; extractedSummary: string | null; extractedSource: string | null; }
export interface ServiceSuggestion { id: string; name: string; nextSlotAt: string | null; distanceKm: number; rapidAccess: boolean; }

export interface Statement { section: string | null; text: string; sourceIds: string[]; warnings: string[]; }
export interface Draft { id: string; patientId: string; status: string; content: string; statements: Statement[]; generator: string; signedBy: string | null; signedAt: string | null; tracedStatements?: number; itemsToCheck?: string[]; }
export interface PendingDraft { id: string; patientId: string; mrn: string; familyName: string; givenName: string; generator: string; createdAt: string; }

export interface SeriesPoint { date: string; actual: number | null; p10: number | null; p50: number | null; p90: number | null; }
export interface CapacityView {
  summary: { occupied: number; beds: number; peak: { date: string; p10: number; p50: number; p90: number } | null; expectedDischargesToday: number; awaitingTransport: number; nursingGapShifts: number; nursingGapLabel: string };
  series: SeriesPoint[];
  wards: { code: string; name: string; beds: number; occupied: number; forecastShare: number; status: 'HIGH' | 'WATCH' | 'OK' }[];
  actions: { id: string; action: string; effect: string; owner: string; status: string; plannedBy: string | null; plannedAt: string | null }[];
  modelVersion: string | null; generatedAt: string | null;
  accuracy: { meanAbsoluteError: number; coverage: number; samples: number };
}

export interface ModelSummary { model: string; version: string; purpose: string; clinicalStatus: string; }
export interface ModelView {
  model: string; version: string; purpose: string; clinicalStatus: string; auroc: number | null; minAuroc: number | null;
  calibrationError: number | null; maxCalibrationError: number | null; nextReview: string | null;
  statusChangedBy: string | null; statusChangedAt: string | null; statusReason: string | null;
  subgroups: { groupName: string; auroc: number; sensitivity: number; calibrationError: number; status: string }[];
  feedback: { total: number; accepted: number; dismissed: number; reported: number };
  drafts: { signed: number; discarded: number; awaitingReview: number } | null;
  forecastAccuracy: { meanAbsoluteError: number; coverage: number; samples: number } | null;
  evidence: { item: string; owner: string; status: string; recordedOn: string }[];
  actions: { title: string; detail: string | null; status: string }[];
  featureSwitchedOff: boolean;
}

export interface EmergencyAccess { id: string; patientId: string; mrn: string; familyName: string; givenName: string; userId: string; reason: string; grantedAt: string; expiresAt: string; reviewedAt: string | null; reviewedBy: string | null; reviewOutcome: string | null; reviewNote: string | null; }
export interface AuditSummary { recordReads: number; denials: number; emergencyPending: number; emergencyLast7Days: number; unusualAccess: { userId: string; denials: number }[]; }
export interface AuditEvent { id: string; occurredAt: string; userId: string; action: string; patientId: string | null; resourceId: string | null; outcome: string; detail: string | null; }

@Injectable({ providedIn: 'root' })
export class Api {
  private http = inject(HttpClient);
  private base = '/api/v1';

  demo() { return this.http.get<{ syntheticData: boolean; onDemoCareTeam: boolean }>(`${this.base}/demo`); }
  joinDemoCareTeam() { return this.http.post<{ patientsAdded: number }>(`${this.base}/demo/care-team`, {}); }

  worklist() { return this.http.get<WorklistItem[]>(`${this.base}/worklist`); }
  worklistSummary() { return this.http.get<WorklistSummary>(`${this.base}/worklist/summary`); }
  completeTask(id: string) { return this.http.post<void>(`${this.base}/tasks/${id}/complete`, {}); }
  createTask(patientId: string, title: string, dueAt: string, reason: string) { return this.http.post<{ id: string }>(`${this.base}/patients/${patientId}/tasks`, { title, dueAt, reason }); }

  patients() { return this.http.get<PatientListItem[]>(`${this.base}/patients`); }
  patient(id: string) { return this.http.get<PatientRecord>(`${this.base}/patients/${id}`); }
  emergencyAccess(id: string, reason: string) { return this.http.post<{ expiresAt: string }>(`${this.base}/patients/${id}/emergency-access`, { reason }); }
  riskFeedback(scoreId: string, outcome: 'ACCEPTED' | 'DISMISSED' | 'REPORTED', reason: string | null) { return this.http.post<void>(`${this.base}/risk-scores/${scoreId}/feedback`, { outcome, reason }); }

  referrals() { return this.http.get<Referral[]>(`${this.base}/referrals`); }
  suggestions(id: string) { return this.http.get<ServiceSuggestion[]>(`${this.base}/referrals/${id}/suggestions`); }
  triage(id: string, urgency: string, reason: string) { return this.http.post<void>(`${this.base}/referrals/${id}/triage`, { urgency, reason }); }
  book(id: string, appointmentAt: string) { return this.http.post<void>(`${this.base}/referrals/${id}/book`, { appointmentAt }); }
  acceptAndBook(id: string, serviceId: string, urgency: string, reason: string) { return this.http.post<void>(`${this.base}/referrals/${id}/accept-and-book`, { serviceId, urgency, reason }); }
  requestInformation(id: string, question: string) { return this.http.post<void>(`${this.base}/referrals/${id}/request-information`, { question }); }

  pendingDrafts() { return this.http.get<PendingDraft[]>(`${this.base}/discharge-drafts`); }
  createDraft(patientId: string) { return this.http.post<Draft>(`${this.base}/patients/${patientId}/discharge-drafts`, {}); }
  draft(id: string) { return this.http.get<Draft>(`${this.base}/discharge-drafts/${id}`); }
  signDraft(id: string, content: string, reviewedSources: boolean) { return this.http.post<Draft>(`${this.base}/discharge-drafts/${id}/sign`, { content, reviewedSources }); }
  discardDraft(id: string) { return this.http.post<void>(`${this.base}/discharge-drafts/${id}/discard`, {}); }

  capacity() { return this.http.get<CapacityView>(`${this.base}/capacity`); }
  planAction(id: string) { return this.http.post<void>(`${this.base}/capacity/actions/${id}/plan`, {}); }

  models() { return this.http.get<ModelSummary[]>(`${this.base}/governance/models`); }
  model(model: string, version: string) { return this.http.get<ModelView>(`${this.base}/governance/models/${model}/${version}`); }
  changeModelStatus(model: string, version: string, status: 'LIVE' | 'SUSPENDED', reason: string) { return this.http.post<void>(`${this.base}/governance/models/${model}/${version}/status`, { status, reason }); }

  auditSummary() { return this.http.get<AuditSummary>(`${this.base}/audit/summary`); }
  emergencyAccessList() { return this.http.get<EmergencyAccess[]>(`${this.base}/audit/emergency-access`); }
  reviewEmergencyAccess(id: string, outcome: 'APPROPRIATE' | 'INAPPROPRIATE', note: string) { return this.http.post<void>(`${this.base}/audit/emergency-access/${id}/review`, { outcome, note }); }
  auditEvents(filter: { userId?: string; patientId?: string }) {
    const params: Record<string, string> = {};
    if (filter.userId) params['userId'] = filter.userId;
    if (filter.patientId) params['patientId'] = filter.patientId;
    return this.http.get<AuditEvent[]>(`${this.base}/audit/events`, { params });
  }
}

export interface AppUser { id: string; username: string; displayName: string; roles: string[]; enabled: boolean; mustChangePassword: boolean; lastLoginAt: string | null; lockedUntil: string | null; }

/** CareConnect's own accounts, used when the portal is in database sign-in mode. */
@Injectable({ providedIn: 'root' })
export class AccountApi {
  private http = inject(HttpClient);
  private base = '/api/v1';

  login(username: string, password: string) { return this.http.post<import('./auth').Session>(`${this.base}/auth/login`, { username, password }); }
  changePassword(currentPassword: string, newPassword: string) { return this.http.post<import('./auth').Session>(`${this.base}/auth/password`, { currentPassword, newPassword }); }
  users() { return this.http.get<AppUser[]>(`${this.base}/admin/users`); }
  createUser(username: string, displayName: string, roles: string[], temporaryPassword: string) { return this.http.post<AppUser>(`${this.base}/admin/users`, { username, displayName, roles, temporaryPassword }); }
  updateUser(id: string, displayName: string, roles: string[], enabled: boolean) { return this.http.put<AppUser>(`${this.base}/admin/users/${id}`, { displayName, roles, enabled }); }
  resetPassword(id: string, temporaryPassword: string) { return this.http.post<void>(`${this.base}/admin/users/${id}/reset-password`, { temporaryPassword }); }
}
