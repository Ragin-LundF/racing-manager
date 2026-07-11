import { Component, inject, signal, OnDestroy } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { HeatService } from './heat.service';
import { ParticipantService } from '../participants/participant.service';
import { HeatResponse, HeatStateChangeEvent, MeasurementResponse } from './heat.models';
import { ParticipantResponse } from '../participants/participant.models';

@Component({
  selector: 'app-race-control',
  standalone: true,
  imports: [RouterLink, DatePipe],
  templateUrl: './race-control.component.html',
  styles: [`
    .header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
    .error { color: red; }
    .heats { display: flex; flex-direction: column; gap: 1rem; }
    .heat-card { border: 1px solid #ddd; border-radius: 8px; padding: 1rem; }
    .heat-card.armed { border-color: #ffa500; }
    .heat-card.started { border-color: #2196f3; }
    .heat-card.finished { border-color: #4caf50; }
    .heat-card.cancelled { border-color: #f44336; opacity: 0.6; }
    .heat-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.5rem; }
    .heat-status { font-weight: 600; padding: 0.25rem 0.5rem; border-radius: 4px; font-size: 0.875rem; }
    .heat-status.armed { background: #fff3e0; color: #e65100; }
    .heat-status.started { background: #e3f2fd; color: #1565c0; }
    .heat-status.finished { background: #e8f5e9; color: #2e7d32; }
    .heat-status.cancelled { background: #ffebee; color: #c62828; }
    .heat-status.planned { background: #f5f5f5; color: #616161; }
    .lanes { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 0.5rem; margin-bottom: 0.5rem; }
    .lane-card { border: 1px solid #eee; border-radius: 4px; padding: 0.5rem; text-align: center; }
    .lane-card.dnf { background: #fff3e0; }
    .lane-card.finished { background: #e8f5e9; }
    .lane-number { font-size: 1.25rem; font-weight: 700; color: #1976d2; }
    .lane-name { font-weight: 500; }
    .lane-time { font-family: monospace; font-size: 1.1rem; color: #333; }
    .lane-outcome { font-size: 0.8rem; font-weight: 600; }
    .actions { display: flex; gap: 0.5rem; flex-wrap: wrap; }
    button { padding: 0.4rem 0.8rem; border: 1px solid #ccc; border-radius: 4px; cursor: pointer; font-size: 0.875rem; }
    button:disabled { opacity: 0.5; cursor: not-allowed; }
    button.primary { background: #1976d2; color: white; border-color: #1976d2; }
    button.danger { background: #f44336; color: white; border-color: #f44336; }
    button.success { background: #4caf50; color: white; border-color: #4caf50; }
    button.warning { background: #ff9800; color: white; border-color: #ff9800; }
    .create-section { margin-bottom: 1rem; padding: 1rem; border: 1px dashed #ccc; border-radius: 8px; }
    .create-section label { margin-right: 0.5rem; }
    .create-section select { margin-right: 0.5rem; padding: 0.3rem; }
    .empty { padding: 2rem; text-align: center; color: #666; }
    .timestamps { font-size: 0.8rem; color: #888; margin-top: 0.25rem; }
  `],
})
export class RaceControlComponent implements OnDestroy {
  private readonly heatService = inject(HeatService);
  private readonly participantService = inject(ParticipantService);
  private readonly route = inject(ActivatedRoute);

  protected heats = signal<HeatResponse[]>([]);
  protected participants = signal<ParticipantResponse[]>([]);
  protected selectedParticipantIds = signal<string[]>([]);
  protected error = signal('');
  protected creating = signal(false);

  private ws: WebSocket | null = null;

  private get eventId(): string {
    return this.route.snapshot.paramMap.get('id')!;
  }

  constructor() {
    this.loadHeats();
    this.loadParticipants();
    this.connectWebSocket();
  }

  ngOnDestroy(): void {
    this.ws?.close();
  }

  private loadHeats(): void {
    this.heatService.findByEventId(this.eventId).subscribe({
      next: (heats) => this.heats.set(heats),
      error: () => this.error.set('Failed to load heats.'),
    });
  }

  private loadParticipants(): void {
    this.participantService.findByEventId(this.eventId).subscribe({
      next: (participants) => this.participants.set(participants.filter(p => p.status === 'ACTIVE')),
      error: () => undefined,
    });
  }

  private connectWebSocket(): void {
    try {
      this.ws = this.heatService.connectLive(this.eventId);
      this.ws.onmessage = (event) => {
        try {
          const data: HeatStateChangeEvent = JSON.parse(event.data);
          this.heats.update((heats) => {
            const idx = heats.findIndex(h => h.id === data.heat.id);
            if (idx >= 0) {
              const updated = [...heats];
              updated[idx] = data.heat;
              return updated;
            }
            return [data.heat, ...heats];
          });
        } catch { /* ignore parse errors */ }
      };
    } catch { /* ws connection failed */ }
  }

  protected toggleParticipant(id: string): void {
    this.selectedParticipantIds.update((ids) =>
      ids.includes(id) ? ids.filter(i => i !== id) : [...ids, id],
    );
  }

  protected onCreateHeat(): void {
    const ids = this.selectedParticipantIds();
    if (ids.length === 0) return;
    this.creating.set(true);
    this.heatService.create(this.eventId, { participantIds: ids }).subscribe({
      next: () => {
        this.selectedParticipantIds.set([]);
        this.creating.set(false);
        this.loadHeats();
      },
      error: () => {
        this.error.set('Failed to create heat.');
        this.creating.set(false);
      },
    });
  }

  protected onArm(heat: HeatResponse): void {
    this.heatService.arm(this.eventId, heat.id).subscribe({
      next: () => this.loadHeats(),
      error: () => this.error.set('Failed to arm heat.'),
    });
  }

  protected onStart(heat: HeatResponse): void {
    this.heatService.start(this.eventId, heat.id).subscribe({
      next: () => this.loadHeats(),
      error: () => this.error.set('Failed to start heat.'),
    });
  }

  protected onFinish(heat: HeatResponse): void {
    this.heatService.finish(this.eventId, heat.id).subscribe({
      next: () => this.loadHeats(),
      error: () => this.error.set('Failed to finish heat.'),
    });
  }

  protected onCancel(heat: HeatResponse): void {
    this.heatService.cancel(this.eventId, heat.id).subscribe({
      next: () => this.loadHeats(),
      error: () => this.error.set('Failed to cancel heat.'),
    });
  }

  protected onAccept(heat: HeatResponse): void {
    this.heatService.acceptResult(this.eventId, heat.id).subscribe({
      next: () => this.loadHeats(),
      error: () => this.error.set('Failed to accept result.'),
    });
  }

  protected onReject(heat: HeatResponse): void {
    this.heatService.rejectResult(this.eventId, heat.id).subscribe({
      next: () => this.loadHeats(),
      error: () => this.error.set('Failed to reject result.'),
    });
  }

  protected onRepeat(heat: HeatResponse): void {
    this.heatService.repeat(this.eventId, heat.id).subscribe({
      next: () => this.loadHeats(),
      error: () => this.error.set('Failed to repeat heat.'),
    });
  }

  protected formatNanos(nanos: number): string {
    if (nanos === 0) return '-';
    const seconds = nanos / 1_000_000_000;
    return `${seconds.toFixed(3)}s`;
  }

  protected getLaneMeasurement(heat: HeatResponse, lane: number): MeasurementResponse | undefined {
    return heat.measurements.find(m => m.lane === lane);
  }

  protected statusClass(status: string): string {
    return status.toLowerCase();
  }
}
