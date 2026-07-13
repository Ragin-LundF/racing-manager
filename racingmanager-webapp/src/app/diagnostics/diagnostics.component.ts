import { Component, OnInit, inject, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { DiagnosticsClient } from '../libs/clients/diagnostics/diagnostics.client';
import {
  DiagnosticsResponse,
  ReadinessResponse,
  UnfinishedHeat,
} from '../libs/clients/diagnostics/diagnostics.models';

@Component({
  selector: 'app-diagnostics',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './diagnostics.component.html',
  styleUrl: './diagnostics.component.scss',
})
export class DiagnosticsComponent implements OnInit {
  private readonly diagnosticsClient = inject(DiagnosticsClient);
  private readonly translate = inject(TranslateService);

  protected diagnostics = signal<DiagnosticsResponse | null>(null);
  protected readiness = signal<ReadinessResponse | null>(null);
  protected error = signal('');
  protected recoveryResult = signal('');
  protected loading = signal(true);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set('');
    this.recoveryResult.set('');
    this.diagnosticsClient.getDiagnostics().subscribe({
      next: (d) => {
        this.diagnostics.set(d);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.translate.instant('diagnostics.loadError'));
        this.loading.set(false);
      },
    });
    this.diagnosticsClient.getReadiness().subscribe({
      next: (r) => { if (r) this.readiness.set(r); },
    });
  }

  protected onRecover(heat: UnfinishedHeat, action: string): void {
    this.diagnosticsClient.recoverHeat(heat.heatId, action).subscribe({
      next: (result) => {
        this.recoveryResult.set(
          this.translate.instant('diagnostics.recoveryResult', { heatNumber: heat.heatNumber, action: result.action }),
        );
        this.load();
      },
      error: (err) => {
        this.error.set(this.translate.instant('diagnostics.recoveryFailed', { message: err.message }));
      },
    });
  }
}
