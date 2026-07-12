import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { ResultsClient } from '../libs/clients/results/results.client';
import { BackupResponse } from '../libs/clients/results/results.models';

@Component({
  selector: 'app-export',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './export.component.html',
  styleUrl: './export.component.scss',
})
export class ExportComponent {
  private readonly resultsClient = inject(ResultsClient);
  private readonly route = inject(ActivatedRoute);

  protected error = signal('');
  protected backupData = signal<BackupResponse | null>(null);
  protected restoreResult = signal('');

  private get eventId(): string {
    return this.route.snapshot.paramMap.get('id')!;
  }

  protected onExportCsv(): void {
    this.resultsClient.exportCsv(this.eventId).subscribe({
      next: (blob) => this.downloadBlob(blob, 'results.csv'),
      error: () => this.error.set('CSV export failed.'),
    });
  }

  protected onExportHtml(): void {
    this.resultsClient.exportHtml(this.eventId).subscribe({
      next: (blob) => this.downloadBlob(blob, 'results.html'),
      error: () => this.error.set('HTML export failed.'),
    });
  }

  protected onExportJson(): void {
    this.resultsClient.exportJson(this.eventId).subscribe({
      next: (data) => {
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        this.downloadBlob(blob, 'results.json');
      },
      error: () => this.error.set('JSON export failed.'),
    });
  }

  protected onExportBackup(): void {
    this.resultsClient.exportBackup(this.eventId).subscribe({
      next: (data) => {
        this.backupData.set(data);
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        this.downloadBlob(blob, 'backup.json');
      },
      error: () => this.error.set('Backup export failed.'),
    });
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const backup: BackupResponse = JSON.parse(reader.result as string);
        this.resultsClient.restoreFromBackup(this.eventId, backup).subscribe({
          next: () => {
            this.restoreResult.set('Restore successful.');
            this.error.set('');
          },
          error: (err) => this.error.set('Restore failed: ' + err.message),
        });
      } catch {
        this.error.set('Invalid backup file format.');
      }
    };
    reader.readAsText(file);
  }

  private downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}
