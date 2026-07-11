import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ParticipantService } from './participant.service';
import { ImportError } from './participant.models';
import { catchError, of } from 'rxjs';

@Component({
  selector: 'app-participant-import',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './participant-import.component.html',
  styleUrl: './participant-import.component.scss',
})
export class ParticipantImportComponent {
  private readonly participantService = inject(ParticipantService);
  protected readonly router = inject(Router);
  protected readonly route = inject(ActivatedRoute);

  protected csvText = '';
  protected error = signal('');
  protected result = signal<{ created: number; errors: ImportError[] } | null>(null);

  protected onSubmit(): void {
    this.error.set('');
    this.result.set(null);

    const rows = this.csvText
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line.length > 0)
      .map((line) => {
        const parts = line.split(',');
        return {
          startNumber: parts[0] ? parseInt(parts[0].trim(), 10) || null : null,
          firstName: parts[1]?.trim() || null,
          lastName: parts[2]?.trim() || null,
          club: parts[3]?.trim() || null,
          vehicleName: parts[4]?.trim() || null,
          vehicleCategory: parts[5]?.trim() || null,
        };
      });

    if (rows.length === 0) {
      this.error.set('No rows to import.');
      return;
    }

    const eventId = this.route.snapshot.paramMap.get('id')!;
    this.participantService.importCsv(eventId, { rows }).pipe(
      catchError(() => {
        this.error.set('Import failed.');
        return of(null);
      }),
    ).subscribe((res) => {
      if (res) {
        this.result.set(res);
      }
    });
  }
}
