import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ParticipantService } from './participant.service';
import { catchError, of } from 'rxjs';

@Component({
  selector: 'app-participant-randomize',
  standalone: true,
  templateUrl: './participant-randomize.component.html',
  styleUrl: './participant-randomize.component.scss',
})
export class ParticipantRandomizeComponent {
  private readonly participantService = inject(ParticipantService);
  protected readonly router = inject(Router);
  protected readonly route = inject(ActivatedRoute);

  protected result = signal<{ seed: number } | null>(null);
  protected alreadyRandomized = signal(false);
  protected error = signal('');
  protected loading = signal(false);

  protected onRandomize(force = false): void {
    this.error.set('');
    this.result.set(null);
    this.alreadyRandomized.set(false);
    this.loading.set(true);

    const eventId = this.route.snapshot.paramMap.get('id')!;
    this.participantService.randomize(eventId, force).pipe(
      catchError((err) => {
        const body = err.error;
        if (body && body.alreadyRandomized) {
          this.alreadyRandomized.set(true);
        } else {
          this.error.set('Randomization failed.');
        }
        return of(null);
      }),
    ).subscribe((res) => {
      this.loading.set(false);
      if (res) {
        this.result.set(res);
      }
    });
  }

  protected onForceRandomize(): void {
    this.onRandomize(true);
  }
}
