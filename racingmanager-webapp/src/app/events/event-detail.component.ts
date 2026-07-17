import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { EventClient } from '../libs/clients/event/event.client';
import { EventResponse } from '../libs/clients/event/event.models';
import { SelectedEventService } from '../core/selected-event.service';
import { SpectatorClient } from '../libs/clients/spectator/spectator.client';
import { ConfirmService } from '../shared/confirm/confirm.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, TranslatePipe],
  templateUrl: './event-detail.component.html',
  styleUrl: './event-detail.component.scss',
})
export class EventDetailComponent {
  private readonly eventService = inject(EventClient);
  private readonly spectatorClient = inject(SpectatorClient);
  private readonly selectedEvent = inject(SelectedEventService);
  private readonly translate = inject(TranslateService);
  private readonly confirm = inject(ConfirmService);
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
      next: (updated) => {
        this.event.set(updated);
        // Refresh the shell's cached event list so the top selector reflects
        // the new status (e.g. DRAFT → ACTIVE) instead of a stale label.
        this.selectedEvent.notifyEventsChanged();
      },
      error: () => this.error.set('Failed to activate event.'),
    });
  }

  protected async onArchive(): Promise<void> {
    const event = this.event();
    if (!event) return;
    const ok = await this.confirm.confirm({
      message: this.translate.instant('events.detail.archiveConfirm', { name: event.name }),
      variant: 'warning',
    });
    if (!ok) return;

    this.eventService.archive(event.id).subscribe({
      next: (updated) => {
        this.event.set(updated);
        // Refresh the shell's cached event list so the top selector reflects
        // the new status (e.g. DRAFT → ACTIVE) instead of a stale label.
        this.selectedEvent.notifyEventsChanged();
      },
      error: () => this.error.set('Failed to archive event.'),
    });
  }

  protected async onReactivate(): Promise<void> {
    const event = this.event();
    if (!event) return;
    const ok = await this.confirm.confirm({
      message: this.translate.instant('events.detail.reactivateConfirm', { name: event.name }),
      variant: 'warning',
    });
    if (!ok) return;

    this.eventService.reactivate(event.id).subscribe({
      next: (updated) => {
        this.event.set(updated);
        // Refresh the shell's cached event list so the top selector reflects
        // the new status (e.g. DRAFT → ACTIVE) instead of a stale label.
        this.selectedEvent.notifyEventsChanged();
      },
      error: () => this.error.set('Failed to reactivate event.'),
    });
  }

  /** Issues a one-time spectator exchange code and opens `/spectator` with it
      in the URL fragment — the reusable spectator JWT itself never appears
      in a URL, browser history, or referrer (design §G.4). */
  protected onOpenSpectatorView(): void {
    const event = this.event();
    if (!event) return;
    this.spectatorClient.issueToken(event.id).subscribe({
      next: (res) => window.open(`${window.location.origin}/spectator#code=${encodeURIComponent(res.exchangeCode)}`, '_blank'),
      error: () => this.error.set('Failed to open spectator view.'),
    });
  }
}
