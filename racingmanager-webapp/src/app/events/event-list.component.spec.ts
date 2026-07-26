import { TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../testing/translate.testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { EventListComponent } from './event-list.component';

const eventRow = {
  id: 'e1',
  name: 'Event',
  description: null,
  status: 'ACTIVE',
  settings: { laneType: 'TWO_LANE', measurementType: 'SIMULATED', maxParticipants: null, trackLength: null },
  version: 0,
  createdBy: 'u1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: null,
  activatedAt: null,
};

describe('EventListComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EventListComponent],
      providers: [
        provideTestTranslate(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
      ]),
      ],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(EventListComponent);
    expect(fixture.componentInstance).toBeTruthy();
    httpTesting.expectOne('http://localhost:8080/api/v1/events').flush([]);
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(EventListComponent);
    fixture.detectChanges();
    httpTesting.expectOne('http://localhost:8080/api/v1/events').flush([]);
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Events');
  });

  it('offers Edit for a running event and not for an archived one', () => {
    const fixture = TestBed.createComponent(EventListComponent);
    fixture.detectChanges();
    httpTesting.expectOne('http://localhost:8080/api/v1/events').flush([
      { ...eventRow, id: 'e1', name: 'Running', status: 'ACTIVE' },
      { ...eventRow, id: 'e2', name: 'Old', status: 'ARCHIVED' },
    ]);
    fixture.detectChanges();

    // routerLink on a <button> sets no href, so identify the row instead.
    const editButtons = Array.from(
      fixture.nativeElement.querySelectorAll('.btn-edit'),
    ) as HTMLButtonElement[];
    expect(editButtons.length).toBe(1);
    expect(editButtons[0].closest('tr')?.textContent).toContain('Running');
  });

  it('should show empty state', () => {
    const fixture = TestBed.createComponent(EventListComponent);
    fixture.detectChanges();
    httpTesting.expectOne('http://localhost:8080/api/v1/events').flush([]);
    fixture.detectChanges();
    const empty = fixture.nativeElement.querySelector('.empty');
    expect(empty).toBeTruthy();
  });
});
