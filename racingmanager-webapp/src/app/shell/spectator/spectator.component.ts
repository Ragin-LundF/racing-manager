import { Component, DestroyRef, OnInit, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslatePipe } from '@ngx-translate/core';
import { interval, Subscription, switchMap, tap } from 'rxjs';
import { LocaleSelectorComponent } from '../../i18n/locale-selector.component';
import { SpectatorClient } from '../../libs/clients/spectator/spectator.client';
import { SpectatorKnockoutStateModel, SpectatorLaneModel, SpectatorSnapshotResponse } from '../../libs/clients/spectator/spectator.models';

@Component({
  selector: 'app-spectator-shell',
  standalone: true,
  imports: [TranslatePipe, LocaleSelectorComponent],
  templateUrl: './spectator.component.html',
  styleUrl: './spectator.component.scss',
})
export class SpectatorShellComponent implements OnInit {
  private readonly spectatorClient = inject(SpectatorClient);
  private readonly destroyRef = inject(DestroyRef);

  /** The exchanged spectator JWT — held only for this view's lifetime, never
      persisted (design §F/§G): a page reload requires a fresh handoff code. */
  private spectatorToken: string | null = null;

  protected readonly snapshot = signal<SpectatorSnapshotResponse | null>(null);
  protected readonly lastKnownSnapshot = signal<SpectatorSnapshotResponse | null>(null);
  protected readonly connected = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly reducedMotion = signal(false);
  protected readonly fullscreen = signal(false);

  private pollSubscription: Subscription | null = null;
  private ws: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;

  protected readonly currentHeat = computed(() => this.snapshot()?.currentHeat ?? this.lastKnownSnapshot()?.currentHeat ?? null);
  protected readonly lane1 = computed(() => this.currentHeat()?.lanes?.[0] ?? null);
  protected readonly lane2 = computed(() => this.currentHeat()?.lanes?.[1] ?? null);
  /** One car in the heat: clean up the versus layout (no VS badge / split). */
  protected readonly singleLane = computed(() => !!this.lane1() && !this.lane2());
  protected readonly upcomingHeats = computed(() => this.snapshot()?.upcomingHeats ?? this.lastKnownSnapshot()?.upcomingHeats ?? []);
  protected readonly rankings = computed(() => this.snapshot()?.qualificationRankings ?? this.lastKnownSnapshot()?.qualificationRankings ?? []);
  protected readonly knockout = computed(() => this.snapshot()?.knockout ?? this.lastKnownSnapshot()?.knockout ?? null);
  protected readonly eventName = computed(() => this.snapshot()?.event?.name ?? this.lastKnownSnapshot()?.event?.name ?? '');
  protected readonly qualificationStatus = computed(() => this.snapshot()?.qualificationStatus ?? this.lastKnownSnapshot()?.qualificationStatus ?? null);
  protected readonly hasLiveData = computed(() => this.snapshot() !== null || this.lastKnownSnapshot() !== null);

  /** Absolute time gap between the two finished lanes, formatted "0.056". */
  protected readonly heatDifference = computed(() => {
    const a = this.lane1()?.durationNanos;
    const b = this.lane2()?.durationNanos;
    if (a == null || b == null) return null;
    return (Math.abs(a - b) / 1_000_000_000).toFixed(3);
  });

  protected readonly clock = signal(this.formatClock());

  constructor() {
    effect(() => {
      this.reducedMotion();
      document.documentElement.classList.toggle('reduced-motion', this.reducedMotion());
    });
    interval(1000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.clock.set(this.formatClock()));
  }

  /** True while this lane has no measured time yet and the heat is running — the
      time slot then shows the animated "waiting" placeholder instead of "—". */
  protected laneIsRunning(lane: SpectatorLaneModel | null): boolean {
    return this.currentHeat()?.status === 'STARTED' && lane?.durationNanos == null && lane?.outcome !== 'DNF';
  }

  private formatClock(): string {
    return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  ngOnInit(): void {
    const code = this.readExchangeCode();
    if (!code) {
      this.error.set('spectator.invalidLink');
      return;
    }
    this.spectatorClient.exchange(code).subscribe({
      next: (res) => {
        this.spectatorToken = res.accessToken;
        this.connectToEvent();
      },
      error: () => this.error.set('spectator.invalidLink'),
    });
  }

  /** The one-time handoff code travels in the URL fragment, never the query
      string or path, so it never reaches server access logs or history
      entries that get shared/copied as a full URL (design §G.4). */
  private readExchangeCode(): string | null {
    const match = /(?:^|#)code=([^&]+)/.exec(window.location.hash);
    return match ? decodeURIComponent(match[1]) : null;
  }

  protected toggleFullscreen(): void {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().then(() => this.fullscreen.set(true));
    } else {
      document.exitFullscreen().then(() => this.fullscreen.set(false));
    }
  }

  protected toggleReducedMotion(): void {
    this.reducedMotion.update((v) => !v);
  }

  private connectToEvent(): void {
    this.connected.set(false);
    this.error.set(null);

    // Prime with the current snapshot right away so the view shows live state
    // on every open; the WebSocket (or polling) then pushes future changes.
    this.primeSnapshot();
    this.tryWebSocket();
  }

  private primeSnapshot(): void {
    const token = this.spectatorToken;
    if (!token) return;
    this.spectatorClient.getSnapshot(token).subscribe({
      next: (data) => {
        this.snapshot.set(data);
        this.lastKnownSnapshot.set(data);
      },
      error: () => {
        // Ignore: the live connection or polling will populate the view.
      },
    });
  }

  private tryWebSocket(): void {
    const token = this.spectatorToken;
    if (!token) return;
    try {
      const url = this.spectatorClient.getLiveWebSocketUrl();
      this.ws = new WebSocket(url);

      this.ws.onopen = () => {
        this.ws?.send(JSON.stringify({ type: 'auth', token }));
        this.connected.set(true);
        this.error.set(null);
      };

      this.ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data) as SpectatorSnapshotResponse;
          this.snapshot.set(data);
          this.lastKnownSnapshot.set(data);
        } catch {
          // ignore parse errors
        }
      };

      this.ws.onclose = () => {
        this.connected.set(false);
        this.ws = null;
        this.scheduleReconnect();
        this.startPolling();
      };

      this.ws.onerror = () => {
        this.ws?.close();
      };
    } catch {
      this.startPolling();
    }
  }

  private startPolling(): void {
    const token = this.spectatorToken;
    if (!token) return;
    this.stopPolling();
    this.pollSubscription = interval(5000)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap(() =>
          this.spectatorClient.getSnapshot(token).pipe(
            tap({
              next: (data) => {
                this.snapshot.set(data);
                this.lastKnownSnapshot.set(data);
                this.error.set(null);
              },
              error: () => {
                // keep last known state
              },
            }),
          ),
        ),
      )
      .subscribe();
  }

  private stopPolling(): void {
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = null;
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      if (!this.ws) {
        this.tryWebSocket();
      }
    }, 10000);
  }

  protected formatNanos(nanos: number | undefined | null): string {
    if (nanos == null) return '—';
    const seconds = nanos / 1_000_000_000;
    return seconds.toFixed(3) + 's';
  }

  /** Bare seconds value with three decimals (spec §5: unit shown separately). */
  protected formatSeconds(nanos: number | undefined | null): string {
    if (nanos == null) return '—';
    return (nanos / 1_000_000_000).toFixed(3);
  }

  protected getParticipantName(participantId: string | undefined | null): string {
    if (!participantId) return '—';
    const snap = this.snapshot() ?? this.lastKnownSnapshot();
    if (!snap) return participantId.slice(0, 8);
    const all = [
      ...snap.upcomingHeats.flatMap((h) => h.lanes),
      ...(snap.currentHeat?.lanes ?? []),
    ];
    const lane = all.find((l) => l.participantId === participantId);
    if (lane) return `${lane.participantFirstName} ${lane.participantLastName}`;
    const ranking = snap.qualificationRankings.find((r) => r.participantId === participantId);
    if (ranking) return `${ranking.firstName} ${ranking.lastName}`;
    return participantId.slice(0, 8);
  }

  protected getKnockoutParticipantName(participantId: string | undefined | null): string {
    return this.getParticipantName(participantId);
  }

  protected getKnockoutRounds(knockout: SpectatorKnockoutStateModel | null): SpectatorKnockoutStateModel['rounds'] {
    return knockout?.rounds ?? [];
  }

  protected trackById(_index: number, item: { id: string }): string {
    return item.id;
  }

  protected trackByRound(_index: number, item: { roundNumber: number }): number {
    return item.roundNumber;
  }
}
