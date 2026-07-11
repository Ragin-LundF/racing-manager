import { Component, inject, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { KnockoutClient } from '../libs/clients/knockout/knockout.client';
import {
  KnockoutTournamentResponse,
  KnockoutMatchResponse,
  KnockoutResultEntryResponse,
} from '../libs/clients/knockout/knockout.models';

@Component({
  selector: 'app-knockout',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './knockout.component.html',
  styleUrl: './knockout.component.scss',
})
export class KnockoutComponent {
  private readonly knockoutClient = inject(KnockoutClient);
  private readonly route = inject(ActivatedRoute);

  protected tournament = signal<KnockoutTournamentResponse | null>(null);
  protected matches = signal<KnockoutMatchResponse[]>([]);
  protected results = signal<KnockoutResultEntryResponse[]>([]);
  protected error = signal('');
  protected success = signal('');
  protected selectedPairingMode = signal('FIRST_VS_LAST');
  protected showFinalizeConfirm = signal(false);
  protected loading = signal(false);

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

  protected onSetup(): void {
    this.loading.set(true);
    this.error.set('');
    this.success.set('');
    this.knockoutClient.setup(this.eventId, { pairingMode: this.selectedPairingMode() }).subscribe({
      next: (t) => {
        this.tournament.set(t);
        this.loading.set(false);
        this.success.set('Knockout setup complete.');
      },
      error: () => {
        this.error.set('Failed to setup knockout.');
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
        this.success.set('Pairings generated.');
        this.loadMatches();
      },
      error: () => {
        this.error.set('Failed to generate pairings.');
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
        this.success.set('Knockout finalized.');
        this.load();
      },
      error: () => {
        this.error.set('Failed to finalize knockout.');
        this.loading.set(false);
      },
    });
  }

  protected matchStatusClass(status: string): string {
    if (status === 'COMPLETED') return 'finished';
    if (status === 'IN_PROGRESS') return 'in-progress';
    return 'planned';
  }

  protected getRounds(): number[] {
    const rounds = new Set(this.matches().map(m => m.roundNumber));
    return Array.from(rounds).sort((a, b) => a - b);
  }

  protected matchesByRound(round: number): KnockoutMatchResponse[] {
    return this.matches().filter(m => m.roundNumber === round);
  }

  protected getParticipantName(match: KnockoutMatchResponse, participantId: string | null): string {
    if (!participantId) return '-';
    return `#${participantId.slice(0, 8)}`;
  }
}
