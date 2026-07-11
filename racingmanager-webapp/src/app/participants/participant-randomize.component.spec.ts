import { TestBed } from '@angular/core/testing';
import { ParticipantRandomizeComponent } from './participant-randomize.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('ParticipantRandomizeComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ParticipantRandomizeComponent],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ParticipantRandomizeComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(ParticipantRandomizeComponent);
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Randomize Participants');
  });
});
