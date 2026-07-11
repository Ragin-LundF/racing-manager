import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterOutlet } from '@angular/router';
import { EventClient } from '../libs/clients/event/event.client';
import { EventResponse } from '../libs/clients/event/event.models';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [RouterLink, RouterOutlet, TranslatePipe],
  templateUrl: './event-detail.component.html',
  styleUrl: './event-detail.component.scss',
})
export class EventDetailComponent {
  private readonly eventService = inject(EventClient);
  protected readonly route = inject(ActivatedRoute);

  protected event = signal<EventResponse | null>(null);
  protected error = signal('');

  constructor() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.loadEvent(id);
  }

  private loadEvent(id: string): void {
    this.eventService.findById(id).subscribe({
      next: (event) => this.event.set(event),
      error: () => this.error.set('Failed to load event.'),
    });
  }

  protected onActivate(): void {
    const event = this.event();
    if (!event) return;
    this.eventService.activate(event.id).subscribe({
      next: (updated) => this.event.set(updated),
      error: () => this.error.set('Failed to activate event.'),
    });
  }

  protected onArchive(): void {
    const event = this.event();
    if (!event) return;
    this.eventService.archive(event.id).subscribe({
      next: (updated) => this.event.set(updated),
      error: () => this.error.set('Failed to archive event.'),
    });
  }
}
