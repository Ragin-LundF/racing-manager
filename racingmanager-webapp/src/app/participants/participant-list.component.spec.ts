import { TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../testing/translate.testing';
import { ParticipantListComponent } from './participant-list.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('ParticipantListComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ParticipantListComponent],
      providers: [
        provideTestTranslate(),provideHttpClient(), provideRouter([
      ])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ParticipantListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(ParticipantListComponent);
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Participants');
  });

  it('should show empty state', () => {
    const fixture = TestBed.createComponent(ParticipantListComponent);
    fixture.detectChanges();
    const empty = fixture.nativeElement.querySelector('.empty');
    expect(empty).toBeTruthy();
  });
});
