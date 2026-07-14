import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { provideTestTranslate } from '../testing/translate.testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { KnockoutComponent } from './knockout.component';
import { KnockoutClient } from '../libs/clients/knockout/knockout.client';
import { ParticipantClient } from '../libs/clients/participant/participant.client';
import { KnockoutMatchResponse, KnockoutTournamentResponse } from '../libs/clients/knockout/knockout.models';
import { ParticipantResponse } from '../libs/clients/participant/participant.models';

function tournament(pairingMode: string, status = 'IN_PROGRESS'): KnockoutTournamentResponse {
  return {
    id: 't1', eventId: 'e1', status, pairingMode, qualificationId: 'q1',
    createdAt: '', updatedAt: null, finalizedAt: null, finalizedBy: null,
  };
}

function match(): KnockoutMatchResponse {
  return {
    id: 'm1', tournamentId: 't1', roundNumber: 1, matchNumber: 1,
    participant1Id: 'p1', participant2Id: 'p2', winnerId: null, heatId: null,
    status: 'PLANNED', createdAt: '',
  };
}

async function createComponent(
  t: KnockoutTournamentResponse | null,
  matches: KnockoutMatchResponse[],
): Promise<ComponentFixture<KnockoutComponent>> {
  const knockoutClient: Partial<KnockoutClient> = {
    findByEventId: () => of(t as KnockoutTournamentResponse),
    getMatches: () => of(matches),
    getResults: () => of([]),
    getQualifiedParticipants: () => of([]),
  };
  const participants = [
    { id: 'p1', startNumber: 1, firstName: 'Alice', lastName: 'Smith', status: 'ACTIVE' },
    { id: 'p2', startNumber: 2, firstName: 'Bob', lastName: 'Jones', status: 'ACTIVE' },
  ] as unknown as ParticipantResponse[];
  const participantClient: Partial<ParticipantClient> = {
    findByEventId: () => of(participants),
  };

  await TestBed.configureTestingModule({
    imports: [KnockoutComponent],
    providers: [
      provideTestTranslate(), provideHttpClient(), provideRouter([]),
      { provide: KnockoutClient, useValue: knockoutClient },
      { provide: ParticipantClient, useValue: participantClient },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(KnockoutComponent);
  fixture.detectChanges();
  return fixture;
}

describe('KnockoutComponent', () => {
  it('should create', async () => {
    const fixture = await createComponent(null, []);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the setup form when there is no tournament', async () => {
    const fixture = await createComponent(null, []);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Setup Knockout');
  });

  it('translates the pairing mode instead of showing the raw key', async () => {
    const fixture = await createComponent(tournament('RANDOM'), []);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Random');
    expect(text).not.toContain('knockout.mode');
  });

  it('shows the tournament status as a chip', async () => {
    const fixture = await createComponent(tournament('RANDOM'), []);
    const chip = fixture.nativeElement.querySelector('.chip');
    expect(chip).toBeTruthy();
    expect(chip.textContent).toContain('IN_PROGRESS');
  });

  it('renders matches as heat-item rows with a Create Heat action', async () => {
    const fixture = await createComponent(tournament('RANDOM'), [match()]);
    const row = fixture.nativeElement.querySelector('.heat-item');
    expect(row).toBeTruthy();
    expect(row.textContent).toContain('Alice');
    expect(row.textContent).toContain('Bob');
    const buttons = [...fixture.nativeElement.querySelectorAll('.heat-item button')] as HTMLButtonElement[];
    expect(buttons.some(b => b.textContent?.includes('Create Heat'))).toBe(true);
  });
});
