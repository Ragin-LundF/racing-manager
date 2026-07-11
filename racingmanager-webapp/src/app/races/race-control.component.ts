import {Component, inject, OnDestroy, signal} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {ActivatedRoute} from '@angular/router';
import {DatePipe} from '@angular/common';
import {HeatClient} from '../libs/clients/heat/heat.client';
import {ParticipantClient} from '../libs/clients/participant/participant.client';
import {HeatResponse, HeatStateChangeEvent, MeasurementResponse} from '../libs/clients/heat/heat.models';
import {ParticipantResponse} from '../libs/clients/participant/participant.models';

@Component({
  selector: 'app-race-control',
  standalone: true,
  imports: [DatePipe, TranslatePipe],
  templateUrl: './race-control.component.html',
  styleUrl: './race-control.component.scss',
})
export class RaceControlComponent implements OnDestroy {
  private readonly heatService = inject(HeatClient);
  private readonly participantService = inject(ParticipantClient);
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
