import { TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../../testing/translate.testing';
import { SetupComponent } from './setup.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

describe('SetupComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SetupComponent],
      providers: [
        provideTestTranslate(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(SetupComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render title in local mode', () => {
    const fixture = TestBed.createComponent(SetupComponent);
    fixture.detectChanges();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/setup-status').flush({ firstRun: true, mode: 'LOCAL' });
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Administrator Setup');
  });

  it('should have a submit button in local mode', () => {
    const fixture = TestBed.createComponent(SetupComponent);
    fixture.detectChanges();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/setup-status').flush({ firstRun: true, mode: 'LOCAL' });
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(button).toBeTruthy();
    expect(button?.textContent).toContain('Create Administrator');
  });

  it('should render the supervisor form in hosted mode', () => {
    const fixture = TestBed.createComponent(SetupComponent);
    fixture.detectChanges();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/setup-status').flush({ firstRun: true, mode: 'HOSTED' });
    httpTesting.expectOne('http://localhost:8080/api/v1/admin/setup-status').flush({ firstRun: true, mode: 'HOSTED' });
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Supervisor Setup');
    const link = fixture.nativeElement.querySelector('a');
    expect(link).toBeTruthy();
  });

  it('should show the already-set-up state in hosted mode', () => {
    const fixture = TestBed.createComponent(SetupComponent);
    fixture.detectChanges();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/setup-status').flush({ firstRun: true, mode: 'HOSTED' });
    httpTesting.expectOne('http://localhost:8080/api/v1/admin/setup-status').flush({ firstRun: false, mode: 'HOSTED' });
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(button).toBeFalsy();
  });
});
