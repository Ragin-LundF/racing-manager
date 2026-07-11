import { Component } from '@angular/core';

@Component({
  selector: 'app-director-shell',
  standalone: true,
  template: `
    <h2 i18n="@@director.shell.title">Director Dashboard</h2>
    <p i18n="@@director.shell.placeholder">Event management will appear here.</p>
  `,
})
export class DirectorShellComponent {}
