import { ChangeDetectorRef, Component, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ParticipantClient } from '../libs/clients/participant/participant.client';
import { catchError, of } from 'rxjs';

@Component({
  selector: 'app-participant-form',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  templateUrl: './participant-form.component.html',
  styleUrl: './participant-form.component.scss',
})
export class ParticipantFormComponent {
  private readonly participantService = inject(ParticipantClient);
  protected readonly router = inject(Router);
  protected readonly route = inject(ActivatedRoute);
  // Zoneless CD: ngModel fields set in an async callback need an explicit nudge.
  private readonly cdr = inject(ChangeDetectorRef);

  protected isEdit = signal(false);
  protected startNumber: number | null = null;
  protected firstName = '';
  protected lastName = '';
  protected club = '';
  protected vehicleName = '';
  protected vehicleCategory = '';
  protected error = signal('');

  constructor() {
    const participantId = this.route.snapshot.paramMap.get('participantId');
    if (participantId) {
      this.isEdit.set(true);
      this.loadParticipant(participantId);
    }
  }

  /** Returns to the participant list. The participant routes span two URL
      segments (participants/new, participants/:participantId), so a relative
      ['..'] would land on a non-existent '.../participants' route — navigate to
      the event's list root by id instead. */
  protected backToList(): void {
    this.router.navigate(['/', 'racemanager', this.route.snapshot.paramMap.get('id')]);
  }

  private loadParticipant(id: string): void {
    const eventId = this.route.snapshot.paramMap.get('id')!;
    this.participantService.findById(eventId, id).subscribe({
      next: (p) => {
        this.startNumber = p.startNumber;
        this.firstName = p.firstName;
        this.lastName = p.lastName;
        this.club = p.club ?? '';
        this.vehicleName = p.vehicle?.name ?? '';
        this.vehicleCategory = p.vehicle?.category ?? '';
        this.cdr.markForCheck();
      },
      error: () => this.error.set('Failed to load participant.'),
    });
  }

  protected onSubmit(): void {
    this.error.set('');
    const eventId = this.route.snapshot.paramMap.get('id')!;

    if (this.isEdit()) {
      const participantId = this.route.snapshot.paramMap.get('participantId')!;
      this.participantService.update(eventId, participantId, {
        startNumber: this.startNumber!,
        firstName: this.firstName,
        lastName: this.lastName,
        club: this.club || null,
      }).pipe(
        catchError((err) => {
          this.error.set(err?.error?.message ?? 'Update failed.');
          return of(null);
        }),
      ).subscribe((res) => {
        if (res) {
          this.backToList();
        }
      });
    } else {
      this.participantService.create(eventId, {
        startNumber: this.startNumber!,
        firstName: this.firstName,
        lastName: this.lastName,
        club: this.club || null,
        vehicleName: this.vehicleName || null,
        vehicleCategory: this.vehicleCategory || null,
      }).pipe(
        catchError((err) => {
          this.error.set(err?.error?.message ?? 'Create failed.');
          return of(null);
        }),
      ).subscribe((res) => {
        if (res) {
          this.backToList();
        }
      });
    }
  }
}
