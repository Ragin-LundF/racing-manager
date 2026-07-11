import { Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { RouterLink } from '@angular/router';
import { EventClient } from '../libs/clients/event/event.client';
import { EventResponse } from '../libs/clients/event/event.models';
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
