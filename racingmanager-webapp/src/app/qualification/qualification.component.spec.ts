import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { QualificationComponent } from './qualification.component';

describe('QualificationComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QualificationComponent],
      providers: [provideHttpClient(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(QualificationComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render setup form when no qualification', () => {
    const fixture = TestBed.createComponent(QualificationComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Setup Qualification');
  });

  it('should render title', () => {
    const fixture = TestBed.createComponent(QualificationComponent);
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2');
    expect(h2?.textContent).toContain('Qualification');
  });
});
