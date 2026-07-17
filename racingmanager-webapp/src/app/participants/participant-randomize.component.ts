import { Component, inject, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ParticipantClient } from '../libs/clients/participant/participant.client';
import { ConfirmService } from '../shared/confirm/confirm.service';
import { catchError, of } from 'rxjs';

@Component({
  selector: 'app-participant-randomize',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './participant-randomize.component.html',
  styleUrl: './participant-randomize.component.scss',
})
export class ParticipantRandomizeComponent {
  private readonly participantService = inject(ParticipantClient);
  protected readonly router = inject(Router);
  protected readonly route = inject(ActivatedRoute);
  private readonly translate = inject(TranslateService);
  private readonly confirm = inject(ConfirmService);

  protected result = signal<{ seed: number } | null>(null);
  protected error = signal('');
  protected loading = signal(false);

  protected onRandomize(force = false): void {
    this.error.set('');
    this.result.set(null);
    this.loading.set(true);

    const eventId = this.route.snapshot.paramMap.get('id')!;
    this.participantService.randomize(eventId, force).pipe(
      catchError((err) => {
        const body = err.error;
        if (body && body.alreadyRandomized) {
          this.promptForce();
        } else {
          this.error.set(this.translate.instant('participants.randomize.randomizationError'));
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

  /** Already-randomized is reported by the backend as a rejection; ask before forcing a re-run. */
  private async promptForce(): Promise<void> {
    const ok = await this.confirm.confirm({
      title: this.translate.instant('participants.randomize.already.title'),
      message: this.translate.instant('participants.randomize.already.message'),
      confirmLabel: this.translate.instant('participants.randomize.already.confirm'),
      variant: 'warning',
    });
    if (ok) this.onRandomize(true);
  }
}
