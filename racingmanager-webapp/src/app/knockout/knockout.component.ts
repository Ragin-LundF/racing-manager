import { Component, inject, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { KnockoutClient } from '../libs/clients/knockout/knockout.client';
import { ParticipantClient } from '../libs/clients/participant/participant.client';
import { ParticipantResponse } from '../libs/clients/participant/participant.models';
import {
  KnockoutTournamentResponse,
  KnockoutMatchResponse,
  KnockoutResultEntryResponse,
  QualifiedParticipantResponse,
} from '../libs/clients/knockout/knockout.models';

@Component({
  selector: 'app-knockout',
  standalone: true,
  imports: [RouterLink, TranslatePipe],
  templateUrl: './knockout.component.html',
  styleUrl: './knockout.component.scss',
})
export class KnockoutComponent {
  private readonly knockoutClient = inject(KnockoutClient);
  private readonly participantClient = inject(ParticipantClient);
  private readonly route = inject(ActivatedRoute);
  private readonly translate = inject(TranslateService);

  protected tournament = signal<KnockoutTournamentResponse | null>(null);
  protected matches = signal<KnockoutMatchResponse[]>([]);
  protected results = signal<KnockoutResultEntryResponse[]>([]);
  protected participants = signal<ParticipantResponse[]>([]);
  protected qualifiedParticipants = signal<QualifiedParticipantResponse[]>([]);
  protected error = signal('');
  protected success = signal('');
  protected selectedPairingMode = signal('FIRST_VS_LAST');
  protected showFinalizeConfirm = signal(false);
  protected loading = signal(false);

  // Manual pairing editor
  protected showManualEditor = signal(false);
  protected manualPairings = signal<{ p1: string; p2: string | null }[]>([]);
  protected selectedP1 = signal('');
  protected selectedP2 = signal('');

  private get eventId(): string {
    return this.route.snapshot.paramMap.get('id')!;
  }

  constructor() {
    this.load();
  }

  private load(): void {
    this.loadTournament();
    this.loadMatches();
    this.loadResults();
    this.loadParticipants();
  }

  private loadTournament(): void {
    this.knockoutClient.findByEventId(this.eventId).subscribe({
      next: (t) => this.tournament.set(t),
      error: () => this.tournament.set(null),
    });
  }

  private loadMatches(): void {
    this.knockoutClient.getMatches(this.eventId).subscribe({
      next: (m) => this.matches.set(m),
      error: () => undefined,
    });
  }

  private loadResults(): void {
    this.knockoutClient.getResults(this.eventId).subscribe({
      next: (r) => this.results.set(r),
      error: () => undefined,
    });
  }

  private loadParticipants(): void {
    this.participantClient.findByEventId(this.eventId).subscribe({
      next: (p) => this.participants.set(p),
      error: () => undefined,
    });
  }

  protected loadQualifiedParticipants(): void {
    this.knockoutClient.getQualifiedParticipants(this.eventId).subscribe({
      next: (q) => this.qualifiedParticipants.set(q),
      error: () => undefined,
    });
  }

  protected onSetup(): void {
    this.loading.set(true);
    this.error.set('');
    this.success.set('');
    this.knockoutClient.setup(this.eventId, { pairingMode: this.selectedPairingMode() }).subscribe({
      next: (t) => {
        this.tournament.set(t);
        this.loading.set(false);
        this.success.set(this.translate.instant('knockout.setupSuccess'));
        if (this.selectedPairingMode() === 'MANUAL') {
          this.loadQualifiedParticipants();
          this.showManualEditor.set(true);
        }
      },
      error: () => {
        this.error.set(this.translate.instant('knockout.setupError'));
        this.loading.set(false);
      },
    });
  }

  protected onGeneratePairings(): void {
    this.loading.set(true);
    this.error.set('');
    this.success.set('');
    this.knockoutClient.generatePairings(this.eventId).subscribe({
      next: (t) => {
        this.tournament.set(t);
        this.loading.set(false);
        this.success.set(this.translate.instant('knockout.pairingsGeneratedSuccess'));
        this.loadMatches();
      },
      error: () => {
        this.error.set(this.translate.instant('knockout.pairingsGeneratedError'));
        this.loading.set(false);
      },
    });
  }

  protected onFinalize(): void {
    this.loading.set(true);
    this.error.set('');
    this.success.set('');
    this.knockoutClient.finalize(this.eventId).subscribe({
      next: () => {
        this.loading.set(false);
        this.showFinalizeConfirm.set(false);
        this.success.set(this.translate.instant('knockout.finalizedSuccess'));
        this.load();
      },
      error: () => {
        this.error.set(this.translate.instant('knockout.finalizedError'));
        this.loading.set(false);
      },
    });
  }

  protected onCreateHeat(match: KnockoutMatchResponse): void {
    this.loading.set(true);
    this.error.set('');
    this.knockoutClient.createHeatForMatch(this.eventId, { matchId: match.id }).subscribe({
      next: () => {
        this.loading.set(false);
        this.success.set(this.translate.instant('knockout.heatCreatedSuccess'));
        this.loadMatches();
      },
      error: () => {
        this.error.set(this.translate.instant('knockout.createHeatError'));
        this.loading.set(false);
      },
    });
  }

  protected addManualPairing(): void {
    const p1 = this.selectedP1();
    if (!p1) return;
    const p2 = this.selectedP2() || null;
    this.manualPairings.update((list) => [...list, { p1, p2 }]);
    this.selectedP1.set('');
    this.selectedP2.set('');
  }

  protected removeManualPairing(index: number): void {
    this.manualPairings.update((list) => list.filter((_, i) => i !== index));
  }

  protected submitManualPairings(): void {
    const pairings = this.manualPairings();
    if (pairings.length === 0) return;
    this.loading.set(true);
    this.error.set('');
    this.success.set('');
    this.knockoutClient.setManualPairings(this.eventId, {
      pairings: pairings.map((p) => ({
        participant1Id: p.p1,
        participant2Id: p.p2,
      })),
    }).subscribe({
      next: (t) => {
        this.tournament.set(t);
        this.loading.set(false);
        this.showManualEditor.set(false);
        this.success.set(this.translate.instant('knockout.manualPairingsSavedSuccess'));
        this.loadMatches();
      },
      error: () => {
        this.error.set(this.translate.instant('knockout.manualPairingsSavedError'));
        this.loading.set(false);
      },
    });
  }

  protected getParticipantName(participantId: string | null): string {
    if (!participantId) return '-';
    const p = this.participants().find((p) => p.id === participantId);
    if (p) return `#${p.startNumber} ${p.firstName} ${p.lastName}`;
    return `#${participantId.slice(0, 8)}`;
  }

  protected getQualifiedName(participantId: string): string {
    const q = this.qualifiedParticipants().find((q) => q.participantId === participantId);
    if (q) return `#${q.startNumber} ${q.firstName} ${q.lastName}`;
    return `#${participantId.slice(0, 8)}`;
  }

  protected usedParticipants(): Set<string> {
    return new Set(this.manualPairings().flatMap((p) => [p.p1, p.p2].filter(Boolean) as string[]));
  }

  protected availableQualified(): QualifiedParticipantResponse[] {
    const used = this.usedParticipants();
    return this.qualifiedParticipants().filter((q) => !used.has(q.participantId));
  }

  protected matchStatusClass(status: string): string {
    if (status === 'COMPLETED') return 'finished';
    if (status === 'IN_PROGRESS') return 'in-progress';
    return 'planned';
  }

  protected statusChipClass(status: string): string {
    if (status === 'PAIRING' || status === 'IN_PROGRESS') return 'chip-success';
    if (status === 'FINALIZED') return 'chip-warning';
    return 'chip-muted';
  }

  protected matchStatusChipClass(status: string): string {
    if (status === 'COMPLETED') return 'chip-success';
    if (status === 'IN_PROGRESS') return 'chip-warning';
    return 'chip-muted';
  }

  protected pairingModeKey(mode: string): string {
    const keys: Record<string, string> = {
      FIRST_VS_LAST: 'knockout.modeFirstVsLast',
      ADJACENT: 'knockout.modeAdjacent',
      RANDOM: 'knockout.modeRandom',
      MANUAL: 'knockout.modeManual',
    };
    return keys[mode] ?? mode;
  }

  protected getMatchPairing(match: KnockoutMatchResponse): string {
    return `${this.getParticipantName(match.participant1Id)} vs ${this.getParticipantName(match.participant2Id)}`;
  }

  protected isBye(match: KnockoutMatchResponse): boolean {
    return match.participant2Id === null && match.status === 'COMPLETED';
  }

  protected getRounds(): number[] {
    const rounds = new Set(this.matches().map(m => m.roundNumber));
    return Array.from(rounds).sort((a, b) => a - b);
  }

  protected matchesByRound(round: number): KnockoutMatchResponse[] {
    return this.matches().filter(m => m.roundNumber === round);
  }
}
