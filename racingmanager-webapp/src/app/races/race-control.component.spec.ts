import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { provideTestTranslate } from '../testing/translate.testing';
import { RaceControlComponent } from './race-control.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { HeatClient } from '../libs/clients/heat/heat.client';
import { ParticipantClient } from '../libs/clients/participant/participant.client';
import { QualificationClient } from '../libs/clients/qualification/qualification.client';
import { KnockoutClient } from '../libs/clients/knockout/knockout.client';
import { HeatResponse } from '../libs/clients/heat/heat.models';
import { QualificationResponse } from '../libs/clients/qualification/qualification.models';
import { KnockoutMatchResponse, KnockoutTournamentResponse } from '../libs/clients/knockout/knockout.models';
import { ParticipantResponse } from '../libs/clients/participant/participant.models';

function heat(status: string, round = 1, heatNumber = 1): HeatResponse {
  return {
    id: `h${round}-${heatNumber}`, eventId: 'e1', round, heatNumber, status,
    lanes: [], measurements: [], createdAt: '', armedAt: null, startedAt: null, finishedAt: null,
  };
}

const acceptResult = vi.fn(() => of({ status: 'accepted' }));
const qualFinalize = vi.fn(() => of(void 0));
const createHeatForMatch = vi.fn((_eventId: string, _req: { matchId: string }) => of(void 0));

interface Opts {
  qualification?: QualificationResponse;
  knockout?: KnockoutTournamentResponse;
  matches?: KnockoutMatchResponse[];
  participants?: ParticipantResponse[];
}

async function createComponent(heats: HeatResponse[], opts: Opts = {}): Promise<ComponentFixture<RaceControlComponent>> {
  acceptResult.mockClear();
  qualFinalize.mockClear();
  createHeatForMatch.mockClear();
  const heatClient: Partial<HeatClient> = {
    findByEventId: () => of(heats),
    acceptResult,
    rejectResult: () => of({ status: 'rejected' }),
    repeat: () => of(heat('PLANNED')),
    connectLive: () => undefined as unknown as WebSocket,
  };
  const participantClient: Partial<ParticipantClient> = { findByEventId: () => of(opts.participants ?? []) };
  const qualificationClient: Partial<QualificationClient> = {
    findByEventId: () => (opts.qualification ? of(opts.qualification) : throwError(() => new Error('none'))),
    finalize: qualFinalize,
  };
  const knockoutClient: Partial<KnockoutClient> = {
    findByEventId: () => (opts.knockout ? of(opts.knockout) : throwError(() => new Error('none'))),
    getMatches: () => of(opts.matches ?? []),
    createHeatForMatch,
    finalize: () => of(void 0),
  };

  await TestBed.configureTestingModule({
    imports: [RaceControlComponent],
    providers: [
      provideHttpClient(), provideRouter([]), ...provideTestTranslate(),
      { provide: HeatClient, useValue: heatClient },
      { provide: ParticipantClient, useValue: participantClient },
      { provide: QualificationClient, useValue: qualificationClient },
      { provide: KnockoutClient, useValue: knockoutClient },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(RaceControlComponent);
  fixture.detectChanges();
  return fixture;
}

function actionButtons(fixture: ComponentFixture<RaceControlComponent>): HTMLButtonElement[] {
  return [...fixture.nativeElement.querySelectorAll('.heat-card .actions button')] as HTMLButtonElement[];
}

function qualification(status: string): QualificationResponse {
  return { id: 'q1', eventId: 'e1', status, numberOfRuns: 2, seed: 1, createdAt: '', updatedAt: null, finalizedAt: null, finalizedBy: null } as QualificationResponse;
}
function knockout(status: string): KnockoutTournamentResponse {
  return { id: 't1', eventId: 'e1', status, pairingMode: 'RANDOM', qualificationId: 'q1', createdAt: '', updatedAt: null, finalizedAt: null, finalizedBy: null } as KnockoutTournamentResponse;
}
function readyMatch(): KnockoutMatchResponse {
  return { id: 'm1', tournamentId: 't1', roundNumber: 1, matchNumber: 1, participant1Id: 'p1', participant2Id: 'p2', winnerId: null, heatId: null, status: 'PLANNED', createdAt: '' };
}
function participant(id: string, first: string, last: string): ParticipantResponse {
  return { id, eventId: 'e1', startNumber: 1, firstName: first, lastName: last, status: 'ACTIVE' } as ParticipantResponse;
}

describe('RaceControlComponent', () => {
  it('should create', async () => {
    const fixture = await createComponent([]);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render title', async () => {
    const fixture = await createComponent([]);
    expect(fixture.nativeElement.querySelector('h2')?.textContent).toContain('Race Control');
  });

  it('groups round-1 heats under a Qualification box and shows no Knockout box without a tournament', async () => {
    const fixture = await createComponent([heat('PLANNED', 1, 1)], { qualification: qualification('IN_PROGRESS') });
    const sections = [...fixture.nativeElement.querySelectorAll('.section .phase-header h3')] as HTMLElement[];
    const titles = sections.map(s => s.textContent?.trim());
    expect(titles).toContain('Qualification');
    expect(titles).not.toContain('Knockout');
    // The round-1 heat renders with the qualification phase label.
    expect(fixture.nativeElement.textContent).toContain('Qualification Heat #1');
  });

  it('shows a Knockout box with round-2 heats when a tournament exists', async () => {
    const fixture = await createComponent([heat('PLANNED', 2, 1)], {
      qualification: qualification('FINALIZED'),
      knockout: knockout('IN_PROGRESS'),
    });
    const titles = [...fixture.nativeElement.querySelectorAll('.phase-header h3')].map((s: HTMLElement) => s.textContent?.trim());
    expect(titles).toContain('Knockout');
    expect(fixture.nativeElement.textContent).toContain('Knockout Heat #1');
  });

  it('finalizes the qualification phase from race control', async () => {
    const fixture = await createComponent([heat('ACCEPTED', 1, 1)], { qualification: qualification('IN_PROGRESS') });
    const finalizeBtn = [...fixture.nativeElement.querySelectorAll('button')]
      .find((b: HTMLButtonElement) => b.textContent?.includes('Finalize Qualification')) as HTMLButtonElement;
    expect(finalizeBtn).toBeTruthy();
    finalizeBtn.click();
    fixture.detectChanges();
    const confirm = [...fixture.nativeElement.querySelectorAll('.confirm-dialog button')]
      .find((b: HTMLButtonElement) => b.textContent?.trim() === 'Yes, Finalize') as HTMLButtonElement;
    confirm.click();
    expect(qualFinalize).toHaveBeenCalled();
  });

  it('Accept opens a confirm block and does not call the service until confirmed', async () => {
    const fixture = await createComponent([heat('FINISHED', 1, 1)], { qualification: qualification('IN_PROGRESS') });
    const accept = actionButtons(fixture).find(b => b.textContent?.trim() === 'Accept')!;
    accept.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.heat-card .confirm-dialog')).toBeTruthy();
    expect(acceptResult).not.toHaveBeenCalled();

    const confirm = [...fixture.nativeElement.querySelectorAll('.heat-card .confirm-dialog button')]
      .find((b: HTMLButtonElement) => b.textContent?.trim() === 'Confirm') as HTMLButtonElement;
    confirm.click();
    fixture.detectChanges();
    expect(acceptResult).toHaveBeenCalled();
  });

  it('ACCEPTED heat collapses to a summary row and expands on click', async () => {
    const fixture = await createComponent([heat('ACCEPTED', 1, 1)], { qualification: qualification('IN_PROGRESS') });
    const summary = fixture.nativeElement.querySelector('.heat-summary');
    expect(summary).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.heat-card')).toBeNull();

    summary.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.heat-card')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.heat-summary')).toBeNull();
  });

  it('FINISHED heat renders the full card, not a summary', async () => {
    const fixture = await createComponent([heat('FINISHED', 1, 1)], { qualification: qualification('IN_PROGRESS') });
    expect(fixture.nativeElement.querySelector('.heat-card')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.heat-summary')).toBeNull();
  });

  it('lists a ready knockout match with a Create heat button that creates the heat', async () => {
    const fixture = await createComponent([], {
      qualification: qualification('FINALIZED'),
      knockout: knockout('IN_PROGRESS'),
      matches: [readyMatch()],
      participants: [participant('p1', 'Alice', 'Smith'), participant('p2', 'Bob', 'Jones')],
    });
    const ready = fixture.nativeElement.querySelector('.ready-match');
    expect(ready).toBeTruthy();
    expect(ready.textContent).toContain('Alice Smith');
    expect(ready.textContent).toContain('Bob Jones');

    const createBtn = ready.querySelector('button') as HTMLButtonElement;
    createBtn.click();
    expect(createHeatForMatch).toHaveBeenCalledOnce();
    expect(createHeatForMatch.mock.calls[0][1]).toEqual({ matchId: 'm1' });
  });

  it('hides the generic Create Heat form once qualification is finalized', async () => {
    const fixture = await createComponent([heat('ACCEPTED', 1, 1)], { qualification: qualification('FINALIZED') });
    expect(fixture.nativeElement.querySelector('.create-section')).toBeNull();
  });

  it('shows the generic Create Heat form while qualification is in progress', async () => {
    const fixture = await createComponent([heat('PLANNED', 1, 1)], { qualification: qualification('IN_PROGRESS') });
    expect(fixture.nativeElement.querySelector('.create-section')).toBeTruthy();
  });

  it('REJECTED heat disables Accept but keeps Repeat enabled', async () => {
    const fixture = await createComponent([heat('REJECTED', 1, 1)], { qualification: qualification('IN_PROGRESS') });
    const buttons = actionButtons(fixture);
    expect(buttons.find(b => b.textContent?.trim() === 'Accept')!.disabled).toBe(true);
    expect(buttons.find(b => b.textContent?.trim() === 'Repeat')!.disabled).toBe(false);
  });
});
