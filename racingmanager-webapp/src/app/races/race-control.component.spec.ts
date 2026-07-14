import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { provideTestTranslate } from '../testing/translate.testing';
import { RaceControlComponent } from './race-control.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { HeatClient } from '../libs/clients/heat/heat.client';
import { ParticipantClient } from '../libs/clients/participant/participant.client';
import { HeatResponse } from '../libs/clients/heat/heat.models';

function heat(status: string): HeatResponse {
  return {
    id: 'h1', eventId: 'e1', round: 1, heatNumber: 1, status,
    lanes: [], measurements: [], createdAt: '', armedAt: null, startedAt: null, finishedAt: null,
  };
}

const acceptResult = vi.fn(() => of({ status: 'accepted' }));

async function createComponent(heats: HeatResponse[]): Promise<ComponentFixture<RaceControlComponent>> {
  acceptResult.mockClear();
  const heatClient: Partial<HeatClient> = {
    findByEventId: () => of(heats),
    acceptResult,
    rejectResult: () => of({ status: 'rejected' }),
    repeat: () => of(heat('PLANNED')),
    connectLive: () => undefined as unknown as WebSocket,
  };
  const participantClient: Partial<ParticipantClient> = { findByEventId: () => of([]) };

  await TestBed.configureTestingModule({
    imports: [RaceControlComponent],
    providers: [
      provideHttpClient(), provideRouter([]), ...provideTestTranslate(),
      { provide: HeatClient, useValue: heatClient },
      { provide: ParticipantClient, useValue: participantClient },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(RaceControlComponent);
  fixture.detectChanges();
  return fixture;
}

function actionButtons(fixture: ComponentFixture<RaceControlComponent>): HTMLButtonElement[] {
  return [...fixture.nativeElement.querySelectorAll('.actions button')] as HTMLButtonElement[];
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

  it('Accept opens a confirm block and does not call the service until confirmed', async () => {
    const fixture = await createComponent([heat('FINISHED')]);

    const accept = actionButtons(fixture).find(b => b.textContent?.trim() === 'Accept')!;
    accept.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.confirm-dialog')).toBeTruthy();
    expect(acceptResult).not.toHaveBeenCalled();

    const confirm = [...fixture.nativeElement.querySelectorAll('.confirm-dialog button')]
      .find((b: HTMLButtonElement) => b.textContent?.trim() === 'Confirm') as HTMLButtonElement;
    confirm.click();
    fixture.detectChanges();

    expect(acceptResult).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.success')).toBeTruthy();
  });

  it('ACCEPTED heat disables all three action buttons', async () => {
    const fixture = await createComponent([heat('ACCEPTED')]);
    const buttons = actionButtons(fixture);
    expect(buttons.length).toBe(3);
    expect(buttons.every(b => b.disabled)).toBe(true);
  });

  it('REJECTED heat disables Accept but keeps Repeat enabled', async () => {
    const fixture = await createComponent([heat('REJECTED')]);
    const buttons = actionButtons(fixture);
    expect(buttons.find(b => b.textContent?.trim() === 'Accept')!.disabled).toBe(true);
    expect(buttons.find(b => b.textContent?.trim() === 'Repeat')!.disabled).toBe(false);
  });
});
