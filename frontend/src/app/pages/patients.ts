import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, PatientListItem } from '../api';
import { age, label, PageTitle, tone } from '../ui';

@Component({
  selector: 'app-patients',
  imports: [RouterLink, PageTitle],
  template: `
    <app-page-title title="My patients" />
    @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
    <section class="card">
      <table>
        <thead><tr><th>Name</th><th>MRN</th><th>Age</th><th>Location</th><th>Readmission risk</th></tr></thead>
        <tbody>
          @for (p of patients(); track p.id) {
            <tr>
              <td><a [routerLink]="['/patients', p.id]">{{ p.familyName }}, {{ p.givenName }}</a></td>
              <td>{{ p.mrn }}</td>
              <td>{{ age(p.birthDate) }}</td>
              <td>{{ p.encounterStatus === 'ADMITTED' ? p.ward : 'Community' }}</td>
              <td><span [class]="tone(p.readmissionRisk)">{{ label(p.readmissionRisk) }}</span></td>
            </tr>
          } @empty { <tr><td colspan="5">No patients are linked to your care team.</td></tr> }
        </tbody>
      </table>
    </section>
  `,
})
export class PatientsPage {
  protected patients = signal<PatientListItem[]>([]);
  protected error = signal('');
  protected age = age;
  protected tone = tone;
  protected label = label;

  constructor() {
    inject(Api).patients().subscribe({
      next: (p) => this.patients.set(p),
      error: () => this.error.set('Patients could not be loaded, or your role does not include this list.'),
    });
  }
}
