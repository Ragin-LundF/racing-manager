import { Injectable, signal } from '@angular/core';
import { EventResponse } from '../libs/clients/event/event.models';

/** Globally-held "active event" selection, persisted to localStorage so it
    survives navigation and reloads. The URL is no longer the source of truth for
    which event is selected — this store is — which stops the selection from
    jumping back when moving between menu items. */
@Injectable({ providedIn: 'root' })
export class SelectedEventService {
  private readonly storageKey = 'racingmanager.selectedEventId';

  readonly selectedEventId = signal<string>(localStorage.getItem(this.storageKey) ?? '');

  /** The fully-loaded event behind [selectedEventId], published by
      `EventDetailComponent` so its child views (results, qualification, race
      control) can read event settings without each refetching the event. */
  readonly event = signal<EventResponse | null>(null);

  /** Bumped whenever the set of events changes (e.g. a delete), so long-lived
      views like the shell can refresh their cached list. */
  readonly revision = signal(0);

  notifyEventsChanged(): void {
    this.revision.update((v) => v + 1);
  }

  select(id: string): void {
    this.selectedEventId.set(id);
    if (id) {
      localStorage.setItem(this.storageKey, id);
    } else {
      localStorage.removeItem(this.storageKey);
    }
  }
}
