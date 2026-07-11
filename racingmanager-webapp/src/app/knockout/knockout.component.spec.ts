import { TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../testing/translate.testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { KnockoutComponent } from './knockout.component';

describe('KnockoutComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [KnockoutComponent],
      providers: [
        provideTestTranslate(),
        provideHttpClient(),
        provideRouter([]),
      ],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(KnockoutComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render setup form when no tournament', () => {
    const fixture = TestBed.createComponent(KnockoutComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Setup Knockout');
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(KnockoutComponent);
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Knockout');
  });
});
