import { TestBed } from '@angular/core/testing';
import { SetupComponent } from './setup.component';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';

describe('SetupComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SetupComponent],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(SetupComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(SetupComponent);
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Administrator Setup');
  });

  it('should have a submit button', () => {
    const fixture = TestBed.createComponent(SetupComponent);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(button).toBeTruthy();
    expect(button?.textContent).toContain('Create Administrator');
  });
});
