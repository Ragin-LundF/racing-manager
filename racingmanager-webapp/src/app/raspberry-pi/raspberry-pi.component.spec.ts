import { TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../testing/translate.testing';
import { provideRouter } from '@angular/router';
import { RaspberryPiComponent } from './raspberry-pi.component';

describe('RaspberryPiComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RaspberryPiComponent],
      providers: [provideTestTranslate(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(RaspberryPiComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the title, protocol reference and the python example', () => {
    const fixture = TestBed.createComponent(RaspberryPiComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.doc-title')?.textContent).toContain('Raspberry Pi');
    // Interface reference lists the protocol message types (literal code cells).
    expect(el.textContent).toContain('prepareRace');
    expect(el.textContent).toContain('finishDetected');
    // Three copy-able code blocks (envelope, finish sample, python).
    expect(el.querySelectorAll('pre').length).toBeGreaterThanOrEqual(3);
    expect(el.querySelector('.copy-btn')).toBeTruthy();
    expect(el.textContent).toContain('import asyncio');
  });

  it('shows the back-to-login link when logged out', () => {
    const fixture = TestBed.createComponent(RaspberryPiComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.public-bar')).toBeTruthy();
  });
});
