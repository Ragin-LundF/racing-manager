import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ParticipantService } from './participant.service';
import { ParticipantResponse } from './participant.models';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-participant-list',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './participant-list.component.html',
  styles: [`
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
    .empty { padding: 2rem; text-align: center; color: #666; }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 0.5rem; text-align: left; border-bottom: 1px solid #ddd; }
    th { font-weight: 600; }
    .inactive { opacity: 0.5; }
    .actions { display: flex; gap: 0.25rem; }
    .toolbar { display: flex; gap: 0.5rem; align-items: center; }
  `],
})
export class ParticipantListComponent {
  private readonly participantService = inject(ParticipantService);
  private readonly route = inject(ActivatedRoute);

  protected participants: ParticipantResponse[] = [];
  protected error = signal('');

  private get eventId(): string {
    return this.route.snapshot.paramMap.get('id')!;
  }

  constructor() {
    this.loadParticipants();
  }

  private loadParticipants(): void {
    this.participantService.findByEventId(this.eventId).subscribe({
      next: (participants) => {
        this.participants = participants;
      },
      error: () => {
        this.error.set('Failed to load participants.');
      },
    });
  }

  protected onDeactivate(id: string): void {
    this.participantService.deactivate(this.eventId, id).subscribe({
      next: () => this.loadParticipants(),
      error: () => this.error.set('Failed to deactivate participant.'),
    });
  }

  protected onReactivate(id: string): void {
    this.participantService.reactivate(this.eventId, id).subscribe({
      next: () => this.loadParticipants(),
      error: () => this.error.set('Failed to reactivate participant.'),
    });
  }
}
