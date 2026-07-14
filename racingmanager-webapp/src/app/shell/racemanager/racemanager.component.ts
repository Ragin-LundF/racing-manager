import { Component, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth.service';
import { SelectedEventService } from '../../core/selected-event.service';
import { LocaleSelectorComponent } from '../../i18n/locale-selector.component';
import { EventClient } from '../../libs/clients/event/event.client';
import { EventResponse } from '../../libs/clients/event/event.models';
import { QualificationClient } from '../../libs/clients/qualification/qualification.client';
import {
  HeatScheduleResponse,
  QualificationProgressResponse,
  QualificationResponse,
} from '../../libs/clients/qualification/qualification.models';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-racemanager-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LocaleSelectorComponent, FormsModule, TranslatePipe],
  templateUrl: './racemanager.component.html',
  styleUrl: './racemanager.component.scss',
})
export class RaceManagerShellComponent {
  protected readonly authService = inject(AuthService);
  protected readonly selectedEvent = inject(SelectedEventService);
  private readonly eventService = inject(EventClient);
  private readonly qualificationService = inject(QualificationClient);
  private readonly router = inject(Router);

  protected readonly events = signal<EventResponse[]>([]);
  /** Derived from the persisted selection, so it survives menu navigation and
      updates reactively when an event is deleted. */
  protected readonly activeEvent = computed(
    () => this.events().find((e) => e.id === this.selectedEvent.selectedEventId()) ?? null,
  );

  protected readonly qualification = signal<QualificationResponse | null>(null);
  protected readonly progress = signal<QualificationProgressResponse | null>(null);
  protected readonly upcomingHeats = signal<HeatScheduleResponse[]>([]);

  /** Off-canvas nav drawer state — only affects the ≤640px layout. */
  protected readonly navOpen = signal(false);
  protected toggleNav(): void {
    this.navOpen.update((open) => !open);
  }
  protected closeNav(): void {
    this.navOpen.set(false);
  }

  constructor() {
    // Reload the event list on start and whenever it changes (e.g. a delete).
    effect(() => {
      this.selectedEvent.revision();
      this.loadEvents();
    });
    // Adopt an event id that appears in the URL (deep link, or navigating via the
    // "View" button / dropdown). URLs without an id leave the selection untouched.
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.adoptEventFromUrl());

    // Sidebar context follows the active event.
    effect(() => {
      const event = this.activeEvent();
      if (event) {
        this.loadContext(event.id);
      } else {
        this.clearContext();
      }
    });
  }

  private loadEvents(): void {
    this.eventService.findAll().subscribe((events) => {
      this.events.set(events);
      // Priority: URL deep-link > persisted selection > the ACTIVE event.
      const desired =
        this.pick(events, this.eventIdFromUrl()) ??
        this.pick(events, this.selectedEvent.selectedEventId()) ??
        events.find((e) => e.status === 'ACTIVE')?.id ??
        '';
      if (desired) {
        this.selectedEvent.select(desired);
      } else {
        this.adoptEventFromUrl();
      }
    });
  }

  private adoptEventFromUrl(): void {
    const id = this.eventIdFromUrl();
    if (!id || id === this.selectedEvent.selectedEventId()) return;
    if (this.events().some((e) => e.id === id)) {
      this.selectedEvent.select(id);
    } else {
      // Deep link to an event not in the list yet — fetch it, then select.
      this.eventService.findById(id).subscribe({
        next: (e) => {
          this.events.update((list) => (list.some((x) => x.id === e.id) ? list : [...list, e]));
          this.selectedEvent.select(e.id);
        },
        error: () => undefined,
      });
    }
  }

  private pick(events: EventResponse[], id: string | null): string | null {
    return id && events.some((e) => e.id === id) ? id : null;
  }

  private eventIdFromUrl(): string | null {
    return this.router.url.match(/\/racemanager\/([0-9a-fA-F-]{36})/)?.[1] ?? null;
  }

  protected onEventSelect(id: string): void {
    this.selectedEvent.select(id);
    this.router.navigate(id ? ['/', 'racemanager', id] : ['/', 'racemanager']);
  }

  /** Loads the context-sidebar data. Failures degrade to empty panels
      (qualification may not be set up yet — that's a content gap, not an error). */
  private loadContext(eventId: string): void {
    this.qualificationService.findByEventId(eventId).subscribe({
      next: (q) => this.qualification.set(q),
      error: () => this.qualification.set(null),
    });
    this.qualificationService.getProgress(eventId).subscribe({
      next: (p) => this.progress.set(p),
      error: () => this.progress.set(null),
    });
    this.qualificationService.getSchedule(eventId).subscribe({
      next: (heats) =>
        this.upcomingHeats.set(heats.filter((h) => h.status === 'PLANNED').slice(0, 3)),
      error: () => this.upcomingHeats.set([]),
    });
  }

  private clearContext(): void {
    this.qualification.set(null);
    this.progress.set(null);
    this.upcomingHeats.set([]);
  }

  protected progressPercent(): number {
    const p = this.progress();
    if (!p || p.totalHeats === 0) {
      return 0;
    }
    return Math.round((p.completedHeats / p.totalHeats) * 100);
  }

  protected onLogout(): void {
    this.authService.logout().subscribe(() => {
      this.router.navigate(['/login']);
    });
  }
}
