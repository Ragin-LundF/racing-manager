import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-spectator-shell',
  standalone: true,
  imports: [TranslatePipe],
  template: `
    <h2>{{ 'spectator.shell.title' | translate }}</h2>
    <p>{{ 'spectator.shell.placeholder' | translate }}</p>
  `,
})
export class SpectatorShellComponent {}
