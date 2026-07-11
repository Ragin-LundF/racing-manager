import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { QualificationService } from './qualification.service';
import { HeatService } from '../races/heat.service';
import {
  QualificationResponse,
  QualificationRankingResponse,
  QualificationProgressResponse,
  HeatScheduleResponse,
} from './qualification.models';
import { HeatResponse } from '../races/heat.models';

@Component({
  selector: 'app-qualification',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './qualification.component.html',
  styles: [`
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
    .error { color: red; }
    .success { color: green; }
    .section { border: 1px solid #ddd; border-radius: 8px; padding: 1rem; margin-bottom: 1rem; }
    .section h3 { margin-top: 0; }
    table { width: 100%; border-collapse: collapse; }
    th, td { padding: 0.5rem; text-align: left; border-bottom: 1px solid #eee; }
    th { font-weight: 600; background: #f5f5f5; }
    .rank-1 { background: #fff8e1; }
    .heat-list { display: flex; flex-direction: column; gap: 0.5rem; }
    .heat-item { display: flex; justify-content: space-between; padding: 0.5rem; border: 1px solid #eee; border-radius: 4px; }
    .heat-item.finished { background: #e8f5e9; }
    .heat-item.in-progress { background: #e3f2fd; }
    .heat-item.planned { background: #fafafa; }
    .heat-item.cancelled { background: #ffebee; opacity: 0.6; }
    .progress-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 0.5rem; }
    .progress-card { text-align: center; padding: 1rem; border: 1px solid #eee; border-radius: 4px; }
    .progress-value { font-size: 2rem; font-weight: 700; }
    .progress-label { font-size: 0.8rem; color: #666; }
    .actions { display: flex; gap: 0.5rem; margin-top: 1rem; }
    button { padding: 0.4rem 0.8rem; border: 1px solid #ccc; border-radius: 4px; cursor: pointer; font-size: 0.875rem; }
    button:disabled { opacity: 0.5; cursor: not-allowed; }
    button.primary { background: #1976d2; color: white; border-color: #1976d2; }
    button.danger { background: #f44336; color: white; border-color: #f44336; }
    button.success { background: #4caf50; color: white; border-color: #4caf50; }
    button.warning { background: #ff9800; color: white; border-color: #ff9800; }
    .empty { padding: 2rem; text-align: center; color: #666; }
    .lane-info { font-size: 0.85rem; color: #555; }
    .setup-form { display: flex; gap: 0.5rem; align-items: center; }
    .setup-form input { width: 60px; padding: 0.3rem; }
    .confirm-dialog { margin-top: 1rem; padding: 1rem; border: 1px solid #ff9800; border-radius: 8px; background: #fff8e1; }
  `],
})
export class QualificationComponent {
  private readonly qualificationService = inject(QualificationService);
  private readonly heatService = inject(HeatService);
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
