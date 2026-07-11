import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { LocaleSelectorComponent } from '../../i18n/locale-selector.component';
import { LocaleService } from '../../i18n/locale.service';
import { EventService } from '../../events/event.service';
import { EventResponse } from '../../events/event.models';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-racemanager-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, LocaleSelectorComponent, FormsModule],
  templateUrl: './racemanager.component.html',
  styles: [
    `
      header { display: flex; justify-content: space-between; align-items: center; padding: 0.5rem 1rem; background: #f5f5f5; border-bottom: 1px solid #ddd; }
      header nav { display: flex; gap: 1rem; align-items: center; }
      .active-event { display: flex; align-items: center; gap: 0.5rem; }
      main { padding: 1rem; }
    `,
  ],
})
export class RaceManagerShellComponent {
  protected readonly authService = inject(AuthService);
  private readonly eventService = inject(EventService);
  private readonly router = inject(Router);
  protected readonly localeService = inject(LocaleService);

  protected events: EventResponse[] = [];
  protected activeEvent: EventResponse | null = null;
  protected selectedEventId = '';

  constructor() {
    this.loadEvents();
  }

  private loadEvents(): void {
    this.eventService.findAll().subscribe((events) => {
      this.events = events;
      this.activeEvent = events.find((e) => e.status === 'ACTIVE') ?? null;
      if (this.activeEvent) {
        this.selectedEventId = this.activeEvent.id;
      }
    });
  }

  protected onEventSelect(): void {
    if (!this.selectedEventId) {
      this.activeEvent = null;
      return;
    }
    this.activeEvent = this.events.find((e) => e.id === this.selectedEventId) ?? null;
  }

  protected onLogout(): void {
    this.authService.logout().subscribe(() => {
      this.router.navigate([this.localeService.currentLocale(), 'login']);
    });
  }
}
