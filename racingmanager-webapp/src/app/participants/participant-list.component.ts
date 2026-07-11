import { Component, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ParticipantClient } from '../libs/clients/participant/participant.client';
import { ParticipantResponse } from '../libs/clients/participant/participant.models';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-participant-list',
  standalone: true,
  imports: [RouterLink, DatePipe, TranslatePipe],
  templateUrl: './participant-list.component.html',
  styleUrl: './participant-list.component.scss',
})
export class ParticipantListComponent {
  private readonly participantService = inject(ParticipantClient);
  private readonly route = inject(ActivatedRoute);

  protected readonly participants = signal<ParticipantResponse[]>([]);
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
        this.participants.set(participants);
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
