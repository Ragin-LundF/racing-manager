import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
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
  private readonly translate = inject(TranslateService);

  protected error = signal('');
  protected backupData = signal<BackupResponse | null>(null);
  protected restoreResult = signal('');

  private get eventId(): string {
    return this.route.snapshot.paramMap.get('id')!;
  }

  protected onExportCsv(): void {
    this.resultsClient.exportCsv(this.eventId).subscribe({
      next: (blob) => this.downloadBlob(blob, 'results.csv'),
      error: () => this.error.set(this.translate.instant('export.csvExportError')),
    });
  }

  protected onExportHtml(): void {
    this.resultsClient.exportHtml(this.eventId).subscribe({
      next: (blob) => this.downloadBlob(blob, 'results.html'),
      error: () => this.error.set(this.translate.instant('export.htmlExportError')),
    });
  }

  protected onExportJson(): void {
    this.resultsClient.exportJson(this.eventId).subscribe({
      next: (data) => {
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        this.downloadBlob(blob, 'results.json');
      },
      error: () => this.error.set(this.translate.instant('export.jsonExportError')),
    });
  }

  protected onExportBackup(): void {
    this.resultsClient.exportBackup(this.eventId).subscribe({
      next: (data) => {
        this.backupData.set(data);
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        this.downloadBlob(blob, 'backup.json');
      },
      error: () => this.error.set(this.translate.instant('export.backupExportError')),
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
            this.restoreResult.set(this.translate.instant('export.restoreSuccess'));
            this.error.set('');
          },
          error: (err) => this.error.set(this.translate.instant('export.restoreFailed', { message: err.message })),
        });
      } catch {
        this.error.set(this.translate.instant('export.invalidBackupFormat'));
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
