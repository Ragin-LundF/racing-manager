import { TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../../testing/translate.testing';
import { RegisterComponent } from './register.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('RegisterComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [provideTestTranslate(), provideHttpClient(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Register Event Organization');
  });

  it('should have a submit button', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(button).toBeTruthy();
    expect(button?.textContent).toContain('Register');
  });
});
