import {Component, computed, inject, OnDestroy, signal} from '@angular/core';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {ActivatedRoute} from '@angular/router';
import {DatePipe, NgTemplateOutlet} from '@angular/common';
import {HeatClient} from '../libs/clients/heat/heat.client';
import {ParticipantClient} from '../libs/clients/participant/participant.client';
import {QualificationClient} from '../libs/clients/qualification/qualification.client';
import {KnockoutClient} from '../libs/clients/knockout/knockout.client';
import {HeatResponse, HeatStateChangeEvent, MeasurementResponse} from '../libs/clients/heat/heat.models';
import {ParticipantResponse} from '../libs/clients/participant/participant.models';
import {QualificationResponse} from '../libs/clients/qualification/qualification.models';
import {KnockoutMatchResponse, KnockoutTournamentResponse} from '../libs/clients/knockout/knockout.models';

@Component({
  selector: 'app-race-control',
  standalone: true,
  imports: [DatePipe, NgTemplateOutlet, TranslatePipe],
  templateUrl: './race-control.component.html',
  styleUrl: './race-control.component.scss',
})
export class RaceControlComponent implements OnDestroy {
  private readonly heatService = inject(HeatClient);
  private readonly participantService = inject(ParticipantClient);
  private readonly qualificationService = inject(QualificationClient);
  private readonly knockoutService = inject(KnockoutClient);
  private readonly route = inject(ActivatedRoute);
  private readonly translate = inject(TranslateService);

  protected heats = signal<HeatResponse[]>([]);
  protected participants = signal<ParticipantResponse[]>([]);
  protected selectedParticipantIds = signal<string[]>([]);
  protected error = signal('');
  protected success = signal('');
  protected creating = signal(false);
  protected confirmingAcceptId = signal<string | null>(null);
  protected accepting = signal(false);

  protected qualification = signal<QualificationResponse | null>(null);
  protected knockout = signal<KnockoutTournamentResponse | null>(null);
  protected koMatches = signal<KnockoutMatchResponse[]>([]);
  protected qualExpanded = signal(true);
  protected koExpanded = signal(true);
  protected showQualFinalizeConfirm = signal(false);
  protected showKoFinalizeConfirm = signal(false);
  protected finalizing = signal(false);
  protected expandedHeatIds = signal<Set<string>>(new Set());

  /** Heats split by phase (round 1 = qualification, round 2 = knockout), heat-number ordered. */
  protected qualHeats = computed(() => this.heats().filter(h => h.round === 1).sort((a, b) => a.heatNumber - b.heatNumber));
  protected koHeats = computed(() => this.heats().filter(h => h.round === 2).sort((a, b) => a.heatNumber - b.heatNumber));

  /** Knockout matches ready to race: both feeders resolved, no heat yet. Mirrors the backend guard. */
  protected readyMatches = computed(() => this.koMatches()
    .filter(m => m.status === 'PLANNED' && m.participant1Id && m.participant2Id && !m.heatId)
    .sort((a, b) => a.roundNumber - b.roundNumber || a.matchNumber - b.matchNumber));

  private ws: WebSocket | null = null;

  private get eventId(): string {
    return this.route.snapshot.paramMap.get('id')!;
  }

  constructor() {
    this.loadHeats();
    this.loadParticipants();
    this.loadPhases();
    this.connectWebSocket();
  }

  ngOnDestroy(): void {
    this.ws?.close();
  }

  private loadHeats(): void {
    this.heatService.findByEventId(this.eventId).subscribe({
      next: (heats) => this.heats.set(heats),
      error: () => this.error.set(this.translate.instant('races.control.loadError')),
    });
  }

  private loadParticipants(): void {
    this.participantService.findByEventId(this.eventId).subscribe({
      next: (participants) => this.participants.set(participants.filter(p => p.status === 'ACTIVE')),
      error: () => undefined,
    });
  }

  private loadPhases(): void {
    this.qualificationService.findByEventId(this.eventId).subscribe({
      next: (q) => {
        this.qualification.set(q);
        // Collapse the qualification box once it is finalized so the knockout box is in focus.
        this.qualExpanded.set(q.status !== 'FINALIZED');
      },
      error: () => this.qualification.set(null),
    });
    this.knockoutService.findByEventId(this.eventId).subscribe({
      next: (k) => this.knockout.set(k),
      error: () => this.knockout.set(null),
    });
    this.loadKnockoutMatches();
  }

  private loadKnockoutMatches(): void {
    this.knockoutService.getMatches(this.eventId).subscribe({
      next: (matches) => this.koMatches.set(matches),
      error: () => this.koMatches.set([]),
    });
  }

  protected toggleQual(): void {
    this.qualExpanded.update(v => !v);
  }

  protected toggleKo(): void {
    this.koExpanded.update(v => !v);
  }

  protected onFinalizeQualification(): void {
    this.finalizing.set(true);
    this.error.set('');
    this.success.set('');
    this.qualificationService.finalize(this.eventId).subscribe({
      next: () => {
        this.finalizing.set(false);
        this.showQualFinalizeConfirm.set(false);
        this.success.set(this.translate.instant('races.control.finalizeSuccess'));
        this.loadPhases();
      },
      error: () => {
        this.finalizing.set(false);
        this.error.set(this.translate.instant('races.control.finalizeError'));
      },
    });
  }

  protected onFinalizeKnockout(): void {
    this.finalizing.set(true);
    this.error.set('');
    this.success.set('');
    this.knockoutService.finalize(this.eventId).subscribe({
      next: () => {
        this.finalizing.set(false);
        this.showKoFinalizeConfirm.set(false);
        this.success.set(this.translate.instant('races.control.finalizeSuccess'));
        this.loadPhases();
      },
      error: () => {
        this.finalizing.set(false);
        this.error.set(this.translate.instant('races.control.finalizeError'));
      },
    });
  }

  protected phaseChipClass(status: string | undefined): string {
    if (status === 'SCHEDULED' || status === 'IN_PROGRESS' || status === 'PAIRING') return 'chip-success';
    if (status === 'FINALIZED') return 'chip-warning';
    return 'chip-muted';
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
        this.error.set(this.translate.instant('races.control.createError'));
        this.creating.set(false);
      },
    });
  }

  protected onCreateKnockoutHeat(match: KnockoutMatchResponse): void {
    this.creating.set(true);
    this.error.set('');
    this.knockoutService.createHeatForMatch(this.eventId, { matchId: match.id }).subscribe({
      next: () => {
        this.creating.set(false);
        this.loadHeats();
        this.loadKnockoutMatches();
      },
      error: () => {
        this.error.set(this.translate.instant('races.control.createError'));
        this.creating.set(false);
      },
    });
  }

  /** Participant display name from the loaded participant list (matches carry only ids). */
  protected participantName(id: string | null): string {
    if (!id) return '';
    const p = this.participants().find(x => x.id === id);
    return p ? `${p.firstName} ${p.lastName}` : '';
  }

  protected onArm(heat: HeatResponse): void {
    this.heatService.arm(this.eventId, heat.id).subscribe({
      next: () => this.loadHeats(),
      error: () => this.error.set(this.translate.instant('races.control.armError')),
    });
  }

  protected onStart(heat: HeatResponse): void {
    this.heatService.start(this.eventId, heat.id).subscribe({
      next: () => this.loadHeats(),
      error: () => this.error.set(this.translate.instant('races.control.startError')),
    });
  }

  protected onFinish(heat: HeatResponse): void {
    this.heatService.finish(this.eventId, heat.id).subscribe({
      next: () => this.loadHeats(),
      error: () => this.error.set(this.translate.instant('races.control.finishError')),
    });
  }

  protected onCancel(heat: HeatResponse): void {
    this.heatService.cancel(this.eventId, heat.id).subscribe({
      next: () => this.loadHeats(),
      error: () => this.error.set(this.translate.instant('races.control.cancelError')),
    });
  }

  protected onAccept(heat: HeatResponse): void {
    this.confirmingAcceptId.set(heat.id);
  }

  protected onCancelAccept(): void {
    this.confirmingAcceptId.set(null);
  }

  protected onConfirmAccept(heat: HeatResponse): void {
    this.accepting.set(true);
    this.heatService.acceptResult(this.eventId, heat.id).subscribe({
      next: () => {
        this.confirmingAcceptId.set(null);
        this.accepting.set(false);
        this.success.set(this.translate.instant('races.control.acceptSuccess'));
        this.loadHeats();
        this.loadKnockoutMatches();
      },
      error: () => {
        this.accepting.set(false);
        this.error.set(this.translate.instant('races.control.acceptError'));
      },
    });
  }

  protected onReject(heat: HeatResponse): void {
    this.heatService.rejectResult(this.eventId, heat.id).subscribe({
      next: () => {
        this.success.set(this.translate.instant('races.control.rejectSuccess'));
        this.loadHeats();
      },
      error: () => this.error.set(this.translate.instant('races.control.rejectError')),
    });
  }

  protected onRepeat(heat: HeatResponse): void {
    this.heatService.repeat(this.eventId, heat.id).subscribe({
      next: () => {
        this.success.set(this.translate.instant('races.control.repeatSuccess'));
        this.loadHeats();
      },
      error: () => this.error.set(this.translate.instant('races.control.repeatError')),
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

  /** Accepted heats collapse to a one-line summary to keep the overview short; click to expand. */
  protected isHeatCollapsed(heat: HeatResponse): boolean {
    return heat.status === 'ACCEPTED' && !this.expandedHeatIds().has(heat.id);
  }

  protected toggleHeat(id: string): void {
    this.expandedHeatIds.update((ids) => {
      const next = new Set(ids);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  protected heatSummary(heat: HeatResponse): string {
    return heat.lanes
      .map((lane) => {
        const m = this.getLaneMeasurement(heat, lane.lane);
        const time = m ? (m.outcome === 'FINISHED' ? this.formatNanos(m.durationNanos) : m.outcome) : '-';
        return `${lane.participantFirstName} ${lane.participantLastName} (${time})`;
      })
      .join(' : ');
  }
}
