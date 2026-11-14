import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, Patient } from '../api';

@Component({
  selector: 'app-patients',
  imports: [RouterLink],
  template: `
    <h1>My patients</h1>
    @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
    <section class="card">
      <table>
        <thead><tr><th>Name</th><th>MRN</th><th>Date of birth</th></tr></thead>
        <tbody>
          @for (p of patients(); track p.id) {
            <tr><td><a [routerLink]="['/patients', p.id]">{{ p.familyName }}, {{ p.givenName }}</a></td><td>{{ p.mrn }}</td><td>{{ p.birthDate ?? 'Not recorded' }}</td></tr>
          } @empty { <tr><td colspan="3">No patients are linked to your care team.</td></tr> }
        </tbody>
      </table>
    </section>
  `,
})
export class PatientsPage {
  protected patients = signal<Patient[]>([]);
  protected error = signal('');

  constructor() {
    inject(Api).patients().subscribe({
      next: (p) => this.patients.set(p),
      error: () => this.error.set('Patients could not be loaded, or your role does not include this list.'),
    });
  }
}
