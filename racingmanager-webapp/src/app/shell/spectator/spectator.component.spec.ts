import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SpectatorShellComponent } from './spectator.component';
import { SpectatorClient } from '../../libs/clients/spectator/spectator.client';
import { of, throwError } from 'rxjs';
import { SpectatorExchangeResponse, SpectatorLaneModel, SpectatorSnapshotResponse } from '../../libs/clients/spectator/spectator.models';
import { provideRouter } from '@angular/router';
import { provideTestTranslate } from '../../testing/translate.testing';
import { Signal, WritableSignal } from '@angular/core';

interface TestComponent {
  formatNanos(nanos: number | undefined | null): string;
  getParticipantName(id: string | undefined | null): string;
  toggleReducedMotion(): void;
  laneIsRunning(lane: SpectatorLaneModel | null): boolean;
  singleLane: Signal<boolean>;
  fullscreen: WritableSignal<boolean>;
  reducedMotion: WritableSignal<boolean>;
  snapshot: WritableSignal<SpectatorSnapshotResponse | null>;
  error: WritableSignal<string | null>;
}

const mockExchange: SpectatorExchangeResponse = {
  accessToken: 'spectator-jwt',
  expiresIn: 14400,
  eventId: 'e1',
};

const mockSnapshot: SpectatorSnapshotResponse = {
  event: { id: 'e1', name: 'Test Event', status: 'ACTIVE', laneType: 'TWO_LANE', measurementType: 'SIMULATED' },
  currentHeat: {
    id: 'h1', heatNumber: 1, round: 1, status: 'STARTED',
    lanes: [
      { lane: 1, participantId: 'p1', participantStartNumber: 1, participantFirstName: 'Alice', participantLastName: 'Smith' },
      { lane: 2, participantId: 'p2', participantStartNumber: 2, participantFirstName: 'Bob', participantLastName: 'Jones' },
    ],
    hasResult: false,
  },
  upcomingHeats: [
    {
      id: 'h2', heatNumber: 2, round: 1, status: 'PLANNED',
      lanes: [
        { lane: 1, participantId: 'p3', participantStartNumber: 3, participantFirstName: 'Charlie', participantLastName: 'Brown' },
        { lane: 2, participantId: 'p4', participantStartNumber: 4, participantFirstName: 'Diana', participantLastName: 'Prince' },
      ],
      hasResult: false,
    },
  ],
  qualificationRankings: [
    { participantId: 'p1', startNumber: 1, firstName: 'Alice', lastName: 'Smith', bestTimeNanos: 1_500_000_000, totalTimeNanos: 3_000_000_000, completedRuns: 2, dnfCount: 0, rank: 1 },
    { participantId: 'p2', startNumber: 2, firstName: 'Bob', lastName: 'Jones', bestTimeNanos: 1_600_000_000, totalTimeNanos: 3_200_000_000, completedRuns: 2, dnfCount: 0, rank: 2 },
  ],
  qualificationStatus: 'FINALIZED',
  knockout: {
    status: 'IN_PROGRESS',
    pairingMode: 'FIRST_VS_LAST',
    rounds: [
      {
        roundNumber: 1,
        matches: [
          { id: 'm1', roundNumber: 1, matchNumber: 1, participant1Id: 'p1', participant2Id: 'p2', winnerId: 'p1', status: 'COMPLETED', isBye: false },
        ],
      },
    ],
  },
  knockoutStandings: [
    { participantId: 'p1', startNumber: 1, firstName: 'Alice', lastName: 'Smith', bestQualificationTimeNanos: 1_500_000_000, bestKnockoutTimeNanos: 1_400_000_000, state: 'WON', place: 1, racing: false },
    { participantId: 'p2', startNumber: 2, firstName: 'Bob', lastName: 'Jones', bestQualificationTimeNanos: 1_600_000_000, state: 'OUT', place: 2, racing: true },
  ],
};

async function createComponent(spectatorClient: Partial<SpectatorClient>): Promise<ComponentFixture<SpectatorShellComponent>> {
  await TestBed.configureTestingModule({
    imports: [SpectatorShellComponent],
    providers: [
      { provide: SpectatorClient, useValue: spectatorClient },
      provideRouter([]),
      ...provideTestTranslate(),
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(SpectatorShellComponent);
  fixture.detectChanges();
  return fixture;
}

describe('SpectatorShellComponent', () => {
  afterEach(() => {
    window.location.hash = '';
  });

  it('should create and exchange the code from the URL fragment', async () => {
    window.location.hash = '#code=abc123';
    const spectatorClient = {
      exchange: () => of(mockExchange),
      getSnapshot: () => of(mockSnapshot),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    };
    const fixture = await createComponent(spectatorClient);
    expect(fixture.componentInstance).toBeTruthy();
    const c = fixture.componentInstance as unknown as TestComponent;
    expect(c.error()).toBeNull();
  });

  it('shows an invalid-link error when the URL has no exchange code', async () => {
    window.location.hash = '';
    const spectatorClient = {
      exchange: () => of(mockExchange),
      getSnapshot: () => of(mockSnapshot),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    };
    const fixture = await createComponent(spectatorClient);
    const c = fixture.componentInstance as unknown as TestComponent;
    expect(c.error()).toBe('spectator.invalidLink');
  });

  it('shows an invalid-link error when the exchange code is rejected', async () => {
    window.location.hash = '#code=expired';
    const spectatorClient = {
      exchange: () => throwError(() => new Error('invalid code')),
      getSnapshot: () => of(mockSnapshot),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    };
    const fixture = await createComponent(spectatorClient);
    const c = fixture.componentInstance as unknown as TestComponent;
    expect(c.error()).toBe('spectator.invalidLink');
  });

  it('should format nanos', async () => {
    window.location.hash = '#code=abc123';
    const spectatorClient = {
      exchange: () => of(mockExchange),
      getSnapshot: () => of(mockSnapshot),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    };
    const fixture = await createComponent(spectatorClient);
    const c = fixture.componentInstance as unknown as TestComponent;
    expect(c.formatNanos(1_500_000_000)).toBe('1.500s');
    expect(c.formatNanos(null)).toBe('—');
  });

  it('should toggle fullscreen', async () => {
    window.location.hash = '#code=abc123';
    const spectatorClient = {
      exchange: () => of(mockExchange),
      getSnapshot: () => of(mockSnapshot),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    };
    const fixture = await createComponent(spectatorClient);
    const c = fixture.componentInstance as unknown as TestComponent;
    c.fullscreen.set(true);
    expect(c.fullscreen()).toBe(true);
    c.fullscreen.set(false);
    expect(c.fullscreen()).toBe(false);
  });

  it('should toggle reduced motion', async () => {
    window.location.hash = '#code=abc123';
    const spectatorClient = {
      exchange: () => of(mockExchange),
      getSnapshot: () => of(mockSnapshot),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    };
    const fixture = await createComponent(spectatorClient);
    const c = fixture.componentInstance as unknown as TestComponent;
    c.toggleReducedMotion();
    expect(c.reducedMotion()).toBe(true);
    c.toggleReducedMotion();
    expect(c.reducedMotion()).toBe(false);
  });

  it('should get participant name from lanes', async () => {
    window.location.hash = '#code=abc123';
    const spectatorClient = {
      exchange: () => of(mockExchange),
      getSnapshot: () => of(mockSnapshot),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    };
    const fixture = await createComponent(spectatorClient);
    const c = fixture.componentInstance as unknown as TestComponent;
    c.snapshot.set(mockSnapshot);
    const name = c.getParticipantName('p1');
    expect(name).toContain('Alice');
  });

  it('reports singleLane true for a one-car heat and false for two cars', async () => {
    window.location.hash = '#code=abc123';
    const spectatorClient = {
      exchange: () => of(mockExchange),
      getSnapshot: () => of(mockSnapshot),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    };
    const fixture = await createComponent(spectatorClient);
    const c = fixture.componentInstance as unknown as TestComponent;

    c.snapshot.set(mockSnapshot);
    expect(c.singleLane()).toBe(false);

    const oneCar: SpectatorSnapshotResponse = {
      ...mockSnapshot,
      currentHeat: { ...mockSnapshot.currentHeat!, lanes: [mockSnapshot.currentHeat!.lanes[0]] },
    };
    c.snapshot.set(oneCar);
    expect(c.singleLane()).toBe(true);
  });

  it('laneIsRunning is true only while a STARTED lane has no measured time', async () => {
    window.location.hash = '#code=abc123';
    const spectatorClient = {
      exchange: () => of(mockExchange),
      getSnapshot: () => of(mockSnapshot),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    };
    const fixture = await createComponent(spectatorClient);
    const c = fixture.componentInstance as unknown as TestComponent;

    // Heat STARTED, no measured time yet → show the animated placeholder.
    c.snapshot.set(mockSnapshot);
    const runningLane = mockSnapshot.currentHeat!.lanes[0];
    expect(c.laneIsRunning(runningLane)).toBe(true);

    // Real measurement present → no longer "running", the measured time is shown.
    const finishedLane: SpectatorLaneModel = { ...runningLane, durationNanos: 1_234_000_000 };
    expect(c.laneIsRunning(finishedLane)).toBe(false);
  });

  it('should return placeholder for unknown participant', async () => {
    window.location.hash = '#code=abc123';
    const spectatorClient = {
      exchange: () => of(mockExchange),
      getSnapshot: () => of(mockSnapshot),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    };
    const fixture = await createComponent(spectatorClient);
    const c = fixture.componentInstance as unknown as TestComponent;
    c.snapshot.set(mockSnapshot);
    const name = c.getParticipantName('unknown-id');
    expect(name).toBeTruthy();
  });

  it('translates the ACCEPTED heat state instead of showing the raw key', async () => {
    window.location.hash = '#code=abc123';
    const accepted: SpectatorSnapshotResponse = {
      ...mockSnapshot,
      currentHeat: { ...mockSnapshot.currentHeat!, status: 'ACCEPTED' },
    };
    const fixture = await createComponent({
      exchange: () => of(mockExchange),
      getSnapshot: () => of(accepted),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    });
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Result accepted');
    expect(text).not.toContain('spectator.heatState');
  });

  it('shows knockout standings (not the bracket) during the knockout phase', async () => {
    window.location.hash = '#code=abc123';
    const fixture = await createComponent({
      exchange: () => of(mockExchange),
      getSnapshot: () => of(mockSnapshot),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    });
    expect(fixture.nativeElement.querySelector('.bracket')).toBeNull();
    const rows = fixture.nativeElement.querySelectorAll('.standing');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Alice');
    expect(fixture.nativeElement.querySelector('.standing-badge.WON')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.standing-times')).toBeTruthy();
  });

  it('renders standing places and a racing dot for the in-progress pair', async () => {
    window.location.hash = '#code=abc123';
    const fixture = await createComponent({
      exchange: () => of(mockExchange),
      getSnapshot: () => of(mockSnapshot),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    });
    const rows = fixture.nativeElement.querySelectorAll('.standing');
    // Places rendered in backend order (Alice #1, Bob #2).
    expect(rows[0].querySelector('.standing-place').textContent.trim()).toBe('1');
    expect(rows[1].querySelector('.standing-place').textContent.trim()).toBe('2');
    // Only the racing participant (Bob) shows the green dot.
    expect(rows[0].querySelector('.racing-dot')).toBeNull();
    expect(rows[1].querySelector('.racing-dot')).toBeTruthy();
    expect(rows[1].classList.contains('racing')).toBe(true);
  });

  it('shows the qualification rankings table when there is no knockout', async () => {
    window.location.hash = '#code=abc123';
    const qualOnly: SpectatorSnapshotResponse = { ...mockSnapshot, knockout: undefined, knockoutStandings: undefined };
    const fixture = await createComponent({
      exchange: () => of(mockExchange),
      getSnapshot: () => of(qualOnly),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    });
    expect(fixture.nativeElement.querySelector('.leaderboard table')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.standing')).toBeNull();
  });
});
