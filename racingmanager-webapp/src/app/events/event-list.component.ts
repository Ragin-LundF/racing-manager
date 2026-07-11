import { Component, inject, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { RouterLink } from '@angular/router';
import { EventClient } from '../libs/clients/event/event.client';
import { EventResponse } from '../libs/clients/event/event.models';
import { SelectedEventService } from '../core/selected-event.service';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-event-list',
  standalone: true,
  imports: [RouterLink, DatePipe, TranslatePipe],
  templateUrl: './event-list.component.html',
  styleUrl: './event-list.component.scss',
})
export class EventListComponent {
  private readonly eventService = inject(EventClient);
  private readonly selectedEvent = inject(SelectedEventService);
  private readonly translate = inject(TranslateService);

  protected readonly events = signal<EventResponse[]>([]);

  constructor() {
    this.loadEvents();
  }

  private loadEvents(): void {
    this.eventService.findAll().subscribe((events) => {
      this.events.set(events);
    });
  }

  protected onDelete(event: EventResponse): void {
    // ponytail: native confirm — a styled modal is more code than the ask warrants.
    const message = this.translate.instant('events.list.deleteConfirm', { name: event.name });
    if (!confirm(message)) return;

    this.eventService.delete(event.id).subscribe({
      next: () => {
        if (this.selectedEvent.selectedEventId() === event.id) {
          this.selectedEvent.select('');
        }
        this.selectedEvent.notifyEventsChanged();
        this.loadEvents();
      },
    });
  }
}
