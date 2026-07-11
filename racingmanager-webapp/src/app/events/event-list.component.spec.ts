import { TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../testing/translate.testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { EventListComponent } from './event-list.component';

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

  it('should show empty state', () => {
    const fixture = TestBed.createComponent(EventListComponent);
    fixture.detectChanges();
    httpTesting.expectOne('http://localhost:8080/api/v1/events').flush([]);
    fixture.detectChanges();
    const empty = fixture.nativeElement.querySelector('.empty');
    expect(empty).toBeTruthy();
  });
});
