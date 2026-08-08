import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FlameWrapComponent } from './flame-wrap.component';

@Component({
  standalone: true,
  imports: [FlameWrapComponent],
  template: `<app-flame-wrap><button type="button">Ignite</button></app-flame-wrap>`,
})
class HostComponent {}

describe('FlameWrapComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
  });

  it('should render projected content as real DOM', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('.flame-wrap__content button');
    expect(button).toBeTruthy();
    expect(button?.textContent).toContain('Ignite');
  });

  it('should keep the projected content outside the canvases', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    // Canvas children are invisible without the HTML-in-Canvas origin trial, so
    // wrapped content must never end up inside one.
    const canvases: HTMLCanvasElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('canvas'),
    );
    expect(canvases.length).toBe(2);
    for (const canvas of canvases) {
      expect(canvas.querySelector('button')).toBeNull();
    }
  });

  it('should expose the flame reach as custom properties', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();

    const wrap: HTMLElement = fixture.nativeElement.querySelector('.flame-wrap');
    expect(wrap.style.getPropertyValue('--flame-reach-top')).toBe('64px');
    expect(wrap.style.getPropertyValue('--flame-reach-side')).toBe('12px');
  });
});
