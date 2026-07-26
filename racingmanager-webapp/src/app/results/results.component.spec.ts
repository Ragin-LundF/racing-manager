import { TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../testing/translate.testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { ResultsComponent } from './results.component';
import { SelectedEventService } from '../core/selected-event.service';
import { EventResponse } from '../libs/clients/event/event.models';
import { EventResultSnapshotResponse } from '../libs/clients/results/results.models';

function eventWithTrackLength(trackLength: number | null): EventResponse {
  return {
    id: 'e1',
    name: 'Test Event',
    description: null,
    status: 'ACTIVE',
    settings: { laneType: 'TWO_LANE', measurementType: 'SIMULATED', maxParticipants: null, trackLength },
    version: 0,
    createdBy: 'u1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: null,
    activatedAt: null,
  };
}

const snapshotWithOneRanking: EventResultSnapshotResponse = {
  event: {
    id: 'e1',
    name: 'Test Event',
    description: null,
    status: 'ACTIVE',
    laneType: 'TWO_LANE',
    measurementType: 'SIMULATED',
    createdAt: '2026-01-01T00:00:00Z',
    activatedAt: null,
    completedAt: null,
  },
  qualificationRankings: [
    {
      participantId: 'p1',
      startNumber: 1,
      firstName: 'Ada',
      lastName: 'Lovelace',
      club: null,
      bestTimeNanos: 10_000_000_000,
      totalTimeNanos: 10_000_000_000,
      completedRuns: 1,
      dnfCount: 0,
      rank: 1,
    },
  ],
  knockoutResults: [],
  allHeats: [],
  measurementType: 'SIMULATED',
  isSimulated: true,
};

describe('ResultsComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResultsComponent],
      providers: [
        provideTestTranslate(),
        provideHttpClient(),
        provideRouter([]),
      ],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ResultsComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(ResultsComponent);
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Results');
  });

  it('shows a km/h column when the selected event has a track length', () => {
    TestBed.inject(SelectedEventService).event.set(eventWithTrackLength(100));
    const fixture = TestBed.createComponent(ResultsComponent);
    fixture.componentInstance['snapshot'].set(snapshotWithOneRanking);
    fixture.detectChanges();

    // 100 m in 10 s = 36.0 km/h
    expect(fixture.nativeElement.textContent).toContain('36.0 km/h');
  });

  it('shows no km/h column when the selected event has no track length', () => {
    TestBed.inject(SelectedEventService).event.set(eventWithTrackLength(null));
    const fixture = TestBed.createComponent(ResultsComponent);
    fixture.componentInstance['snapshot'].set(snapshotWithOneRanking);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('km/h');
  });
});
