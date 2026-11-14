import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface WorklistItem { id: string; patientId: string; mrn: string; familyName: string; givenName: string; title: string; status: string; dueAt: string; assignedTo: string | null; }
export interface Patient { id: string; mrn: string; familyName: string; givenName: string; birthDate: string | null; }
export interface Encounter { id: string; visitNumber: string; status: string; admittedAt: string | null; dischargedAt: string | null; }
export interface Note { id: string; noteType: string; author: string; body: string; writtenAt: string; }
export interface Task { id: string; title: string; status: string; dueAt: string; assignedTo: string | null; }
export interface PatientRecord { patient: Patient; careConsent: string; careTeam: { userId: string; teamRole: string }[]; encounters: Encounter[]; notes: Note[]; tasks: Task[]; }
export interface Referral { id: string; patientId: string; mrn: string; familyName: string; givenName: string; service: string; reason: string; urgency: string; status: string; createdAt: string; triageReason: string | null; appointmentAt: string | null; }
export interface Statement { text: string; sourceIds: string[]; warnings: string[]; }
export interface Draft { id: string; patientId: string; status: string; content: string; statements: Statement[]; generator: string; signedBy: string | null; signedAt: string | null; }

@Injectable({ providedIn: 'root' })
export class Api {
  private http = inject(HttpClient);
  private base = '/api/v1';

  worklist() { return this.http.get<WorklistItem[]>(`${this.base}/worklist`); }
  completeTask(id: string) { return this.http.post<void>(`${this.base}/tasks/${id}/complete`, {}); }
  patients() { return this.http.get<Patient[]>(`${this.base}/patients`); }
  patient(id: string) { return this.http.get<PatientRecord>(`${this.base}/patients/${id}`); }
  emergencyAccess(id: string, reason: string) { return this.http.post<{ expiresAt: string }>(`${this.base}/patients/${id}/emergency-access`, { reason }); }
  referrals() { return this.http.get<Referral[]>(`${this.base}/referrals`); }
  triage(id: string, urgency: string, reason: string) { return this.http.post<void>(`${this.base}/referrals/${id}/triage`, { urgency, reason }); }
  book(id: string, appointmentAt: string) { return this.http.post<void>(`${this.base}/referrals/${id}/book`, { appointmentAt }); }
  createDraft(patientId: string) { return this.http.post<Draft>(`${this.base}/patients/${patientId}/discharge-drafts`, {}); }
  draft(id: string) { return this.http.get<Draft>(`${this.base}/discharge-drafts/${id}`); }
  signDraft(id: string, content: string, reviewedSources: boolean) { return this.http.post<Draft>(`${this.base}/discharge-drafts/${id}/sign`, { content, reviewedSources }); }
  discardDraft(id: string) { return this.http.post<void>(`${this.base}/discharge-drafts/${id}/discard`, {}); }
}
