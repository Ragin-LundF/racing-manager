import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { QualificationClient } from '../libs/clients/qualification/qualification.client';
import { HeatClient } from '../libs/clients/heat/heat.client';
import {
  QualificationResponse,
  QualificationRankingResponse,
  QualificationProgressResponse,
  HeatScheduleResponse,
} from '../libs/clients/qualification/qualification.models';
import { HeatResponse } from '../libs/clients/heat/heat.models';

@Component({
  selector: 'app-qualification',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './qualification.component.html',
  styleUrl: './qualification.component.scss',
})
export class QualificationComponent {
  private readonly qualificationService = inject(QualificationClient);
  private readonly heatService = inject(HeatClient);
  private readonly route = inject(ActivatedRoute);

  protected qualification = signal<QualificationResponse | null>(null);
  protected rankings = signal<QualificationRankingResponse[]>([]);
  protected progress = signal<QualificationProgressResponse | null>(null);
  protected schedule = signal<HeatScheduleResponse[]>([]);
  protected heats = signal<HeatResponse[]>([]);
  protected error = signal('');
  protected success = signal('');
  protected numberOfRuns = signal(2);
  protected showFinalizeConfirm = signal(false);
  protected showReopenConfirm = signal(false);
  protected loading = signal(false);

  private get eventId(): string {
    return this.route.snapshot.paramMap.get('id')!;
  }

  constructor() {
    this.load();
  }

  private load(): void {
    this.loadQualification();
    this.loadRankings();
    this.loadProgress();
    this.loadSchedule();
    this.loadHeats();
  }

  private loadQualification(): void {
    this.qualificationService.findByEventId(this.eventId).subscribe({
      next: (q) => this.qualification.set(q),
      error: () => this.qualification.set(null),
    });
  }

  private loadRankings(): void {
    this.qualificationService.getRankings(this.eventId).subscribe({
      next: (r) => this.rankings.set(r),
      error: () => undefined,
    });
  }

  private loadProgress(): void {
    this.qualificationService.getProgress(this.eventId).subscribe({
      next: (p) => this.progress.set(p),
      error: () => undefined,
    });
  }

  private loadSchedule(): void {
    this.qualificationService.getSchedule(this.eventId).subscribe({
      next: (s) => this.schedule.set(s),
      error: () => undefined,
    });
  }

  private loadHeats(): void {
    this.heatService.findByEventId(this.eventId).subscribe({
      next: (h) => this.heats.set(h),
      error: () => undefined,
    });
  }

  protected onSetup(): void {
    this.loading.set(true);
    this.error.set('');
    this.success.set('');
    this.qualificationService.setup(this.eventId, { numberOfRuns: this.numberOfRuns() }).subscribe({
      next: (q) => {
        this.qualification.set(q);
        this.loading.set(false);
        this.success.set('Qualification setup complete.');
      },
      error: () => {
        this.error.set('Failed to setup qualification.');
        this.loading.set(false);
      },
    });
  }

  protected onGenerateSchedule(): void {
    this.loading.set(true);
    this.error.set('');
    this.success.set('');
    this.qualificationService.generateSchedule(this.eventId).subscribe({
      next: (q) => {
        this.qualification.set(q);
        this.loading.set(false);
        this.success.set('Schedule generated.');
        this.loadSchedule();
      },
      error: () => {
        this.error.set('Failed to generate schedule.');
        this.loading.set(false);
      },
    });
  }

  protected onFinalize(): void {
    this.loading.set(true);
    this.error.set('');
    this.success.set('');
    this.qualificationService.finalize(this.eventId).subscribe({
      next: () => {
        this.loading.set(false);
        this.showFinalizeConfirm.set(false);
        this.success.set('Qualification finalized.');
        this.load();
      },
      error: () => {
        this.error.set('Failed to finalize qualification.');
        this.loading.set(false);
      },
    });
  }

  protected onReopen(): void {
    this.loading.set(true);
    this.error.set('');
    this.success.set('');
    this.qualificationService.reopen(this.eventId).subscribe({
      next: () => {
        this.loading.set(false);
        this.showReopenConfirm.set(false);
        this.success.set('Qualification reopened.');
        this.load();
      },
      error: () => {
        this.error.set('Failed to reopen qualification.');
        this.loading.set(false);
      },
    });
  }

  protected formatNanos(nanos: number | null): string {
    if (nanos === null || nanos === 0) return '-';
    const seconds = nanos / 1_000_000_000;
    return `${seconds.toFixed(3)}s`;
  }

  protected heatStatusClass(status: string): string {
    if (status === 'FINISHED' || status === 'TIMEOUT') return 'finished';
    if (status === 'ARMED' || status === 'STARTED') return 'in-progress';
    if (status === 'CANCELLED' || status === 'TECHNICAL_ERROR') return 'cancelled';
    return 'planned';
  }

  protected getLaneNames(heat: HeatScheduleResponse): string {
    return heat.lanes.map(l => `#${l.participantStartNumber} ${l.participantFirstName} ${l.participantLastName}`).join(' vs ');
  }
}
