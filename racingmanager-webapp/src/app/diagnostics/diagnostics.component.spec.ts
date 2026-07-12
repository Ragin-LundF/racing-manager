import { TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../testing/translate.testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { DiagnosticsComponent } from './diagnostics.component';

describe('DiagnosticsComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DiagnosticsComponent],
      providers: [
        provideTestTranslate(),
        provideHttpClient(),
        provideRouter([]),
      ],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(DiagnosticsComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(DiagnosticsComponent);
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Diagnostics');
  });
});
