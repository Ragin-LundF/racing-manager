import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HealthClient } from './libs/clients/health/health.client';
import { LocaleSelectorComponent } from './i18n/locale-selector.component';
import { toSignal } from '@angular/core/rxjs-interop';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, LocaleSelectorComponent],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly healthService = inject(HealthClient);

  protected readonly health = toSignal(this.healthService.checkHealth());
  protected readonly buildInfo = toSignal(this.healthService.getBuildInfo());
}
