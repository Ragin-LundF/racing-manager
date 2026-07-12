import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { LowerCasePipe } from '@angular/common';
import { ResultsClient } from '../libs/clients/results/results.client';
import { EventResultSnapshotResponse } from '../libs/clients/results/results.models';

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
      error: () => this.error.set('Failed to load results.'),
    });
  }

  protected onComplete(): void {
    this.completing.set(true);
    this.resultsClient.completeEvent(this.eventId).subscribe({
      next: () => this.load(),
      error: () => {
        this.error.set('Failed to complete event.');
        this.completing.set(false);
      },
    });
  }

  protected onReopen(): void {
    this.reopening.set(true);
    this.resultsClient.reopenEvent(this.eventId).subscribe({
      next: () => this.load(),
      error: () => {
        this.error.set('Failed to reopen event.');
        this.reopening.set(false);
      },
    });
  }
}
