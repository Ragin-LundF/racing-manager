import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth.service';
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
  private readonly eventService = inject(EventClient);
  private readonly qualificationService = inject(QualificationClient);
  private readonly router = inject(Router);

  protected events: EventResponse[] = [];
  protected activeEvent: EventResponse | null = null;
  protected selectedEventId = '';

  protected readonly qualification = signal<QualificationResponse | null>(null);
  protected readonly progress = signal<QualificationProgressResponse | null>(null);
  protected readonly upcomingHeats = signal<HeatScheduleResponse[]>([]);

  constructor() {
    this.loadEvents();
    // The selected event follows wherever you are — opening an event (including
    // right after creating it) auto-selects it, so no separate top-bar step.
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.syncActiveEventFromUrl());
    this.syncActiveEventFromUrl();
  }

  private loadEvents(): void {
    this.eventService.findAll().subscribe((events) => {
      this.events = events;
      // Reconcile with the current route first; fall back to the ACTIVE event.
      if (!this.syncActiveEventFromUrl()) {
        this.selectEvent(events.find((e) => e.status === 'ACTIVE') ?? null);
      }
    });
  }

  /** Selects the event whose id is in the current URL, if any. Returns true when
      an event route was matched. */
  private syncActiveEventFromUrl(): boolean {
    const id = this.router.url.match(/\/racemanager\/([0-9a-fA-F-]{36})/)?.[1];
    if (!id) return false;
    if (id === this.selectedEventId && this.activeEvent) return true;

    const known = this.events.find((e) => e.id === id);
    if (known) {
      this.selectEvent(known);
    } else {
      this.eventService.findById(id).subscribe({
        next: (e) => this.selectEvent(e),
        error: () => undefined,
      });
    }
    return true;
  }

  private selectEvent(event: EventResponse | null): void {
    this.activeEvent = event;
    this.selectedEventId = event?.id ?? '';
    if (event) {
      this.loadContext(event.id);
    } else {
      this.clearContext();
    }
  }

  protected onEventSelect(): void {
    if (!this.selectedEventId) {
      this.selectEvent(null);
      this.router.navigate(['/', 'racemanager']);
      return;
    }
    this.router.navigate(['/', 'racemanager', this.selectedEventId]);
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
