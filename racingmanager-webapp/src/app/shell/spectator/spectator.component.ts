import { Component } from '@angular/core';

@Component({
  selector: 'app-spectator-shell',
  standalone: true,
  template: `
    <h2 i18n="@@spectator.shell.title">Spectator View</h2>
    <p i18n="@@spectator.shell.placeholder">Live race results will appear here.</p>
  `,
})
export class SpectatorShellComponent {}
