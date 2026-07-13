import { ChangeDetectorRef, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { RaceDeviceClient } from '../libs/clients/race-device/race-device.client';

@Component({
  selector: 'app-race-device-settings',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  templateUrl: './race-device-settings.component.html',
  styleUrl: './race-device-settings.component.scss',
})
export class RaceDeviceSettingsComponent {
  private readonly client = inject(RaceDeviceClient);
  private readonly translate = inject(TranslateService);
  // Zoneless CD: ngModel fields set in an async callback need an explicit nudge.
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly error = signal('');
  protected readonly success = signal('');
  protected readonly testResult = signal('');
  protected readonly testOk = signal(false);
  protected readonly testing = signal(false);

  protected mode = 'SIMULATED';
  protected endpoint = 'ws://raspberrypi.local:8080/race';
  protected finishTimeoutMs = 30000;

  constructor() {
    this.client.getSettings().subscribe({
      next: (settings) => {
        this.mode = settings.mode;
        this.endpoint = settings.endpoint;
        this.finishTimeoutMs = settings.finishTimeoutMs;
        this.cdr.markForCheck();
      },
      error: () => this.error.set(this.translate.instant('raspberryPi.config.loadError')),
    });
  }

  protected onSave(): void {
    this.error.set('');
    this.success.set('');
    this.testResult.set('');
    this.client
      .updateSettings({ mode: this.mode, endpoint: this.endpoint, finishTimeoutMs: this.finishTimeoutMs })
      .subscribe({
        next: () => this.success.set(this.translate.instant('raspberryPi.config.saved')),
        error: () => this.error.set(this.translate.instant('raspberryPi.config.saveError')),
      });
  }

  protected onTest(): void {
    this.error.set('');
    this.success.set('');
    this.testResult.set('');
    this.testing.set(true);
    this.client.testConnection({ mode: this.mode, endpoint: this.endpoint }).subscribe({
      next: (result) => {
        this.testing.set(false);
        this.testOk.set(result.ok);
        this.testResult.set(
          result.ok
            ? this.translate.instant('raspberryPi.config.testSuccess', { ms: result.pingMs ?? 0 })
            : this.translate.instant('raspberryPi.config.testFailed', { message: result.error ?? '' }),
        );
      },
      error: (err) => {
        this.testing.set(false);
        this.testOk.set(false);
        this.testResult.set(this.translate.instant('raspberryPi.config.testFailed', { message: err.message }));
      },
    });
  }
}
