import { Component, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { LocaleSelectorComponent } from '../../i18n/locale-selector.component';
import { LocaleService } from '../../i18n/locale.service';

@Component({
  selector: 'app-director-shell',
  standalone: true,
  imports: [RouterOutlet, LocaleSelectorComponent],
  template: `
    <header>
      <app-locale-selector />
      <span>{{ authService.session()?.displayName }}</span>
      <button (click)="onLogout()" i18n="@@director.logout">Log Out</button>
    </header>

    <main>
      <h2 i18n="@@director.shell.title">Director Dashboard</h2>
      <p i18n="@@director.shell.placeholder">Event management will appear here.</p>
      <router-outlet />
    </main>
  `,
})
export class DirectorShellComponent {
  protected readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly localeService = inject(LocaleService);

  protected onLogout(): void {
    this.authService.logout().subscribe(() => {
      this.router.navigate([this.localeService.currentLocale(), 'login']);
    });
  }
}
