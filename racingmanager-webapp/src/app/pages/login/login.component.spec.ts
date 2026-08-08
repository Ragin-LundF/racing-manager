import { TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../../testing/translate.testing';
import { LoginComponent } from './login.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

describe('LoginComponent', () => {
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [provideTestTranslate(), provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  /** Renders the component and answers the setup-status probe it fires on init. */
  function renderWith(status: { firstRun: boolean; mode: 'LOCAL' | 'HOSTED' }) {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/setup-status').flush(status);
    fixture.detectChanges();
    return fixture;
  }

  function linkTexts(fixture: ReturnType<typeof renderWith>): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.auth-card a') as NodeListOf<HTMLElement>).map(
      (a) => a.textContent ?? '',
    );
  }

  it('should create', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    expect(fixture.componentInstance).toBeTruthy();
    fixture.detectChanges();
    httpTesting.expectOne('http://localhost:8080/api/v1/auth/setup-status').flush({ firstRun: false, mode: 'LOCAL' });
  });

  it('should render title', () => {
    const fixture = renderWith({ firstRun: false, mode: 'LOCAL' });
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Login');
  });

  it('should have a submit button', () => {
    const fixture = renderWith({ firstRun: false, mode: 'LOCAL' });
    const button = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(button).toBeTruthy();
    expect(button?.textContent).toContain('Log In');
  });

  it('offers both bootstrap paths on a fresh local install', () => {
    const texts = linkTexts(renderWith({ firstRun: true, mode: 'LOCAL' })).join('|');
    expect(texts).toContain('Set up this installation');
    expect(texts).toContain('Register a new event organization');
  });

  it('hides both bootstrap paths once a local installation has a user', () => {
    const texts = linkTexts(renderWith({ firstRun: false, mode: 'LOCAL' })).join('|');
    expect(texts).not.toContain('Set up this installation');
    expect(texts).not.toContain('Register a new event organization');
  });

  it('keeps registration offered in hosted mode', () => {
    const texts = linkTexts(renderWith({ firstRun: false, mode: 'HOSTED' })).join('|');
    expect(texts).toContain('Register a new event organization');
    expect(texts).not.toContain('Set up this installation');
  });
});
