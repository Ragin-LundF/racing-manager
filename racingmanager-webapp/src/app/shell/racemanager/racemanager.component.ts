import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { LocaleSelectorComponent } from '../../i18n/locale-selector.component';
import { LocaleService } from '../../i18n/locale.service';
import { EventService } from '../../events/event.service';
import { EventResponse } from '../../events/event.models';
import { QualificationService } from '../../qualification/qualification.service';
import {
  HeatScheduleResponse,
  QualificationProgressResponse,
  QualificationResponse,
} from '../../qualification/qualification.models';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-racemanager-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, LocaleSelectorComponent, FormsModule],
  templateUrl: './racemanager.component.html',
  styleUrl: './racemanager.component.scss',
})
export class RaceManagerShellComponent {
  protected readonly authService = inject(AuthService);
  private readonly eventService = inject(EventService);
  private readonly qualificationService = inject(QualificationService);
  private readonly router = inject(Router);
  protected readonly localeService = inject(LocaleService);

  protected events: EventResponse[] = [];
  protected activeEvent: EventResponse | null = null;
  protected selectedEventId = '';

  protected readonly qualification = signal<QualificationResponse | null>(null);
  protected readonly progress = signal<QualificationProgressResponse | null>(null);
  protected readonly upcomingHeats = signal<HeatScheduleResponse[]>([]);

  constructor() {
    this.loadEvents();
  }

  private loadEvents(): void {
    this.eventService.findAll().subscribe((events) => {
      this.events = events;
      this.activeEvent = events.find((e) => e.status === 'ACTIVE') ?? null;
      if (this.activeEvent) {
        this.selectedEventId = this.activeEvent.id;
        this.loadContext(this.activeEvent.id);
      }
    });
  }

  protected onEventSelect(): void {
    if (!this.selectedEventId) {
      this.activeEvent = null;
      this.clearContext();
      return;
    }
    this.activeEvent = this.events.find((e) => e.id === this.selectedEventId) ?? null;
    if (this.activeEvent) {
      this.loadContext(this.activeEvent.id);
    }
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
      this.router.navigate([this.localeService.currentLocale(), 'login']);
    });
  }
}
