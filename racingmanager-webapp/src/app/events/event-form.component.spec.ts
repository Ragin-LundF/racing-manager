import { TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../testing/translate.testing';
import { EventFormComponent } from './event-form.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('EventFormComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EventFormComponent],
      providers: [
        provideTestTranslate(),provideHttpClient(), provideRouter([
      ])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(EventFormComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render form title for create', () => {
    const fixture = TestBed.createComponent(EventFormComponent);
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Create Event');
  });

  it('should have a submit button', () => {
    const fixture = TestBed.createComponent(EventFormComponent);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(button).toBeTruthy();
  });
});
