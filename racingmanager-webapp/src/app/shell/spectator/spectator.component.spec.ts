import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SpectatorShellComponent } from './spectator.component';
import { SpectatorClient } from '../../libs/clients/spectator/spectator.client';
import { of } from 'rxjs';
import { SpectatorEventListResponse, SpectatorSnapshotResponse } from '../../libs/clients/spectator/spectator.models';
import { provideRouter } from '@angular/router';
import { provideTestTranslate } from '../../testing/translate.testing';
import { WritableSignal } from '@angular/core';

interface TestComponent {
  formatNanos(nanos: number | undefined | null): string;
  getParticipantName(id: string | undefined | null): string;
  toggleReducedMotion(): void;
  fullscreen: WritableSignal<boolean>;
  reducedMotion: WritableSignal<boolean>;
  snapshot: WritableSignal<SpectatorSnapshotResponse | null>;
}

const mockEvents: SpectatorEventListResponse = {
  events: [
    { id: 'e1', name: 'Test Event', status: 'ACTIVE' },
  ],
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
};

describe('SpectatorShellComponent', () => {
  let component: SpectatorShellComponent;
  let fixture: ComponentFixture<SpectatorShellComponent>;

  beforeEach(async () => {
    const spectatorClient = {
      getEvents: () => of(mockEvents),
      getSnapshot: () => of(mockSnapshot),
      getLiveWebSocketUrl: () => 'ws://localhost/test',
    };

    await TestBed.configureTestingModule({
      imports: [SpectatorShellComponent],
      providers: [
        { provide: SpectatorClient, useValue: spectatorClient },
        provideRouter([]),
        ...provideTestTranslate(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SpectatorShellComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should format nanos', () => {
    const c = component as unknown as TestComponent;
    expect(c.formatNanos(1_500_000_000)).toBe('1.500s');
    expect(c.formatNanos(null)).toBe('—');
  });

  it('should toggle fullscreen', () => {
    const c = component as unknown as TestComponent;
    c.fullscreen.set(true);
    expect(c.fullscreen()).toBe(true);
    c.fullscreen.set(false);
    expect(c.fullscreen()).toBe(false);
  });

  it('should toggle reduced motion', () => {
    const c = component as unknown as TestComponent;
    c.toggleReducedMotion();
    expect(c.reducedMotion()).toBe(true);
    c.toggleReducedMotion();
    expect(c.reducedMotion()).toBe(false);
  });

  it('should get participant name from lanes', () => {
    const c = component as unknown as TestComponent;
    c.snapshot.set(mockSnapshot);
    const name = c.getParticipantName('p1');
    expect(name).toContain('Alice');
  });

  it('should return placeholder for unknown participant', () => {
    const c = component as unknown as TestComponent;
    c.snapshot.set(mockSnapshot);
    const name = c.getParticipantName('unknown-id');
    expect(name).toBeTruthy();
  });
});
