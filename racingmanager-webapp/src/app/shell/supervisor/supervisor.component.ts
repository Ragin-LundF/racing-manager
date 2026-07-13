import { Component, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth.service';
import { LocaleSelectorComponent } from '../../i18n/locale-selector.component';

/** A supervisor has no tenant membership and no event/participant data, so
    this shell carries none of the racemanager shell's event-selection nav —
    just identity + logout around the router outlet. */
@Component({
  selector: 'app-supervisor-shell',
  standalone: true,
  imports: [RouterOutlet, LocaleSelectorComponent, TranslatePipe],
  templateUrl: './supervisor.component.html',
  styleUrl: './supervisor.component.scss',
})
export class SupervisorShellComponent {
  protected readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected onLogout(): void {
    this.authService.logout().subscribe(() => {
      this.router.navigate(['/login']);
    });
  }
}
