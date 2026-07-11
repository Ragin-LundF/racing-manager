import { TestBed } from '@angular/core/testing';
import { RaceControlComponent } from './race-control.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('RaceControlComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RaceControlComponent],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(RaceControlComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(RaceControlComponent);
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Race Control');
  });

  it('should show empty state', () => {
    const fixture = TestBed.createComponent(RaceControlComponent);
    fixture.detectChanges();
    const empty = fixture.nativeElement.querySelector('.empty');
    expect(empty).toBeTruthy();
  });
});
