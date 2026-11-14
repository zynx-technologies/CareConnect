import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api, WorklistItem } from '../api';

@Component({
  selector: 'app-worklist',
  imports: [DatePipe, RouterLink],
  template: `
    <h1>Care coordination worklist</h1>
    @if (error()) { <p class="alert" role="alert">{{ error() }}</p> }
    <section class="card">
      <h2>Open tasks ({{ items().length }})</h2>
      @if (loaded() && items().length === 0) { <p>No open tasks for your patients.</p> }
      @if (items().length) {
        <table>
          <thead><tr><th>Patient</th><th>Task</th><th>Due</th><th>Assigned to</th><th><span class="sr">Actions</span></th></tr></thead>
          <tbody>
            @for (t of items(); track t.id) {
              <tr>
                <td><a [routerLink]="['/patients', t.patientId]">{{ t.givenName }} {{ t.familyName }}</a> <span class="muted">{{ t.mrn }}</span></td>
                <td>{{ t.title }}</td>
                <td>{{ t.dueAt | date: 'd MMM, HH:mm' }}</td>
                <td>{{ t.assignedTo ?? 'Unassigned' }}</td>
                <td><button (click)="complete(t)">Mark complete</button></td>
              </tr>
            }
          </tbody>
        </table>
      }
    </section>
  `,
})
export class WorklistPage {
  private api = inject(Api);
  protected items = signal<WorklistItem[]>([]);
  protected loaded = signal(false);
  protected error = signal('');

  constructor() { this.load(); }

  private load(): void {
    this.api.worklist().subscribe({
      next: (items) => { this.items.set(items); this.loaded.set(true); this.error.set(''); },
      error: () => this.error.set('The worklist could not be loaded, or your role does not include it.'),
    });
  }

  protected complete(task: WorklistItem): void {
    this.api.completeTask(task.id).subscribe({
      next: () => this.load(),
      error: () => this.error.set('The task could not be completed. Refresh and try again.'),
    });
  }
}
