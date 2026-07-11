import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EventService } from './event.service';
import { EventResponse } from './event.models';
import { DatePipe } from '@angular/common';

@Component({
  selector: 'app-event-list',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './event-list.component.html',
  styles: [
    `
      .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
      .empty { padding: 2rem; text-align: center; color: #666; }
      table { width: 100%; border-collapse: collapse; }
      th, td { padding: 0.5rem; text-align: left; border-bottom: 1px solid #ddd; }
      th { font-weight: 600; }
    `,
  ],
})
export class EventListComponent {
  private readonly eventService = inject(EventService);

  protected events: EventResponse[] = [];

  constructor() {
    this.loadEvents();
  }

  private loadEvents(): void {
    this.eventService.findAll().subscribe((events) => {
      this.events = events;
    });
  }
}
