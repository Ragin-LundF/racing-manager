import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { EventClient } from '../libs/clients/event/event.client';
import { ConflictResponse } from '../libs/clients/event/event.models';
import { LocaleService } from '../i18n/locale.service';
import { TranslatePipe } from '@ngx-translate/core';
import { catchError, of } from 'rxjs';

@Component({
  selector: 'app-event-form',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  templateUrl: './event-form.component.html',
  styleUrl: './event-form.component.scss',
})
export class EventFormComponent {
  private readonly eventService = inject(EventClient);
  protected readonly router = inject(Router);
  protected readonly route = inject(ActivatedRoute);
  private readonly localeService = inject(LocaleService);

  protected isEdit = signal(false);
  protected name = '';
  protected description = '';
  protected laneType = 'TWO_LANE';
  protected measurementType = 'SIMULATED';
  protected maxParticipants: number | null = null;
  protected version = 0;
  protected error = signal('');
  protected conflict = signal<ConflictResponse | null>(null);

  protected readonly laneTypes = ['TWO_LANE', 'FOUR_LANE', 'SIX_LANE', 'EIGHT_LANE'];
  protected readonly measurementTypes = ['SIMULATED', 'MANUAL', 'ELECTRONIC'];

  constructor() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'new') {
      this.isEdit.set(true);
      this.loadEvent(id);
    }
  }

  private loadEvent(id: string): void {
    this.eventService.findById(id).subscribe({
      next: (event) => {
        this.name = event.name;
        this.description = event.description ?? '';
        this.laneType = event.settings.laneType;
        this.measurementType = event.settings.measurementType;
        this.maxParticipants = event.settings.maxParticipants;
        this.version = event.version;
      },
      error: () => {
        this.error.set('Failed to load event.');
      },
    });
  }

  protected onSubmit(): void {
    this.error.set('');
    this.conflict.set(null);

    if (this.isEdit()) {
      this.eventService.update(this.route.snapshot.paramMap.get('id')!, {
        name: this.name,
        description: this.description || null,
        laneType: this.laneType,
        measurementType: this.measurementType,
        maxParticipants: this.maxParticipants,
        expectedVersion: this.version,
      }).pipe(
        catchError((err) => {
          const body = err.error as ConflictResponse;
          if (body && body.expectedVersion !== undefined) {
            this.conflict.set(body);
          } else {
            this.error.set('Update failed.');
          }
          return of(null);
        }),
      ).subscribe((res) => {
        if (res) {
          this.router.navigate(['..'], { relativeTo: this.route });
        }
      });
    } else {
      this.eventService.create({
        name: this.name,
        description: this.description || null,
        laneType: this.laneType,
        measurementType: this.measurementType,
        maxParticipants: this.maxParticipants,
      }).pipe(
        catchError((err) => {
          this.error.set(err?.error?.message ?? 'Create failed.');
          return of(null);
        }),
      ).subscribe((res) => {
        if (res) {
          // Open the newly created event directly — the shell then auto-selects
          // it, so there is no separate "select on top" step.
          this.router.navigate(['..', res.id], { relativeTo: this.route });
        }
      });
    }
  }

  protected onDismissConflict(): void {
    this.conflict.set(null);
    const id = this.route.snapshot.paramMap.get('id')!;
    this.loadEvent(id);
  }
}
