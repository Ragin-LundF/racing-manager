import { TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../../testing/translate.testing';
import { LoginComponent } from './login.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('LoginComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideTestTranslate(),provideHttpClient(), provideRouter([
      ])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Login');
  });

  it('should have a submit button', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(button).toBeTruthy();
    expect(button?.textContent).toContain('Log In');
  });
});
