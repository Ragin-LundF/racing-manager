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
  styleUrl: './event-list.component.scss',
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
