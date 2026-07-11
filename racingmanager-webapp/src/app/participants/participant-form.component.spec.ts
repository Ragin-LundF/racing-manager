import { TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../testing/translate.testing';
import { ParticipantFormComponent } from './participant-form.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('ParticipantFormComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ParticipantFormComponent],
      providers: [
        provideTestTranslate(),provideHttpClient(), provideRouter([
      ])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(ParticipantFormComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render form title for create', () => {
    const fixture = TestBed.createComponent(ParticipantFormComponent);
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Add Participant');
  });

  it('should have a submit button', () => {
    const fixture = TestBed.createComponent(ParticipantFormComponent);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(button).toBeTruthy();
  });
});
