import { TestBed } from '@angular/core/testing';
import { provideTestTranslate } from '../testing/translate.testing';
import { EventDetailComponent } from './event-detail.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('EventDetailComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EventDetailComponent],
      providers: [
        provideTestTranslate(),provideHttpClient(), provideRouter([
      ])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(EventDetailComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
