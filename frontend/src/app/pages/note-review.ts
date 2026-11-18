import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, PendingDraft } from '../api';
import { PageTitle } from '../ui';

@Component({
  selector: 'app-note-review',
  imports: [DatePipe, RouterLink, PageTitle],
  template: `
    <app-page-title title="Note review" />
    @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
    <section class="card">
      <h2>AI drafts waiting for a clinician ({{ drafts().length }})</h2>
      <table>
        <thead><tr><th>Patient</th><th>Draft</th><th>Created</th><th><span class="sr">Actions</span></th></tr></thead>
        <tbody>
          @for (d of drafts(); track d.id) {
            <tr>
              <td>{{ d.givenName }} {{ d.familyName }} <span class="muted">(MRN {{ d.mrn }})</span></td>
              <td>Discharge summary <span class="muted">{{ d.generator }}</span></td>
              <td>{{ d.createdAt | date: 'd MMM, HH:mm' }}</td>
              <td><a [routerLink]="['/drafts', d.id]">Review</a></td>
            </tr>
          } @empty { <tr><td colspan="4">No drafts are waiting for your patients.</td></tr> }
        </tbody>
      </table>
      <p class="footnote">Drafts are not part of the record. Nothing is filed or sent until a clinician edits and signs it.</p>
    </section>
  `,
})
export class NoteReviewPage {
  protected drafts = signal<PendingDraft[]>([]);
  protected error = signal('');

  constructor() {
    inject(Api).pendingDrafts().subscribe({
      next: (d) => this.drafts.set(d),
      error: () => this.error.set('Drafts could not be loaded, or your role does not include note review.'),
    });
  }
}
