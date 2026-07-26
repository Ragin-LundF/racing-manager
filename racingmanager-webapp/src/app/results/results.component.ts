import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { LowerCasePipe } from '@angular/common';
import { ResultsClient } from '../libs/clients/results/results.client';
import { EventResultSnapshotResponse } from '../libs/clients/results/results.models';
import { SelectedEventService } from '../core/selected-event.service';
import { formatSpeedKmh } from '../shared/speed';

@Component({
  selector: 'app-results',
  standalone: true,
  imports: [TranslatePipe, LowerCasePipe],
  templateUrl: './results.component.html',
  styleUrl: './results.component.scss',
})
export class ResultsComponent {
  private readonly resultsClient = inject(ResultsClient);
  private readonly route = inject(ActivatedRoute);
  private readonly translate = inject(TranslateService);
  private readonly selectedEvent = inject(SelectedEventService);

  /** Set only when the event declares a track length — the speed column exists
      only then. */
  protected readonly trackLength = computed(() => this.selectedEvent.event()?.settings.trackLength ?? null);

  protected snapshot = signal<EventResultSnapshotResponse | null>(null);
  protected error = signal('');
  protected completing = signal(false);
  protected reopening = signal(false);

  private get eventId(): string {
    return this.route.snapshot.paramMap.get('id')!;
  }

  constructor() {
    this.load();
  }

  private load(): void {
    this.resultsClient.getSnapshot(this.eventId).subscribe({
      next: (data) => this.snapshot.set(data),
      error: () => this.error.set(this.translate.instant('results.loadError')),
    });
  }

  protected speed(nanos: number | null): string {
    return formatSpeedKmh(nanos, this.trackLength());
  }

  protected onComplete(): void {
    this.completing.set(true);
    this.resultsClient.completeEvent(this.eventId).subscribe({
      next: () => this.load(),
      error: () => {
        this.error.set(this.translate.instant('results.completeError'));
        this.completing.set(false);
      },
    });
  }

  protected onReopen(): void {
    this.reopening.set(true);
    this.resultsClient.reopenEvent(this.eventId).subscribe({
      next: () => this.load(),
      error: () => {
        this.error.set(this.translate.instant('results.reopenError'));
        this.reopening.set(false);
      },
    });
  }
}
