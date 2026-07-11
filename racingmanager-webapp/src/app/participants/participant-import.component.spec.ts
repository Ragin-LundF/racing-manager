import { TestBed } from '@angular/core/testing';
import { ParticipantImportComponent } from './participant-import.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('ParticipantImportComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ParticipantImportComponent],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ParticipantImportComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(ParticipantImportComponent);
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Import Participants');
  });
});
