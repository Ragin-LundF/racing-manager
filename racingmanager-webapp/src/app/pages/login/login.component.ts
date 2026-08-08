import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth.service';
import { LocaleSelectorComponent } from '../../i18n/locale-selector.component';
import { FlameWrapComponent } from '../../shared/canvasui/flame-wrap.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FlameWrapComponent, FormsModule, LocaleSelectorComponent, RouterLink, TranslatePipe],
  template: `
    <div class="auth-page">
      <app-flame-wrap>
        <div class="auth-card">
          <div class="auth-locale"><app-locale-selector /></div>

          <h2>{{ 'login.title' | translate }}</h2>

          @if (errorMessage(); as msg) {
            <p class="error">{{ msg }}</p>
          }

          <form #form="ngForm" (ngSubmit)="onSubmit()">
            <label>
              <span>{{ 'login.username.label' | translate }}</span>
              <input name="username" [(ngModel)]="username" required #usernameField="ngModel" />
            </label>
            @if (usernameField.invalid && usernameField.touched) {
              <small>{{ 'login.username.required' | translate }}</small>
            }

            <label>
              <span>{{ 'login.password.label' | translate }}</span>
              <input
                type="password"
                name="password"
                [(ngModel)]="password"
                required
                #passwordField="ngModel"
              />
            </label>
            @if (passwordField.invalid && passwordField.touched) {
              <small>{{ 'login.password.required' | translate }}</small>
            }

            <label>
              <span>{{ 'login.tenantSlug.label' | translate }}</span>
              <input name="tenantSlug" [(ngModel)]="tenantSlug" />
            </label>

            <button type="submit" [disabled]="form.invalid">
              {{ 'login.submit' | translate }}
            </button>
          </form>

          <a [routerLink]="['/register']">{{ 'login.registerLink' | translate }}</a>
        </div>
      </app-flame-wrap>

      <a class="docs-button" [routerLink]="['/raspberry-pi']">{{ 'login.raspberryPiLink' | translate }}</a>
    </div>
  `,
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);

  protected username = '';
  protected password = '';
  protected tenantSlug = '';
  protected errorMessage = signal<string | null>(null);

  protected onSubmit(): void {
    this.errorMessage.set(null);
    this.authService
      .login({ username: this.username, password: this.password, tenantSlug: this.tenantSlug || undefined })
      .subscribe((res) => {
        if ('code' in res) {
          this.errorMessage.set(this.localizeError(res.code));
        } else {
          this.router.navigate([this.authService.hasScope('rm:supervisor') ? '/supervisor' : '/racemanager']);
        }
      });
  }

  private localizeError(code: string): string {
    const key =
      code === 'INVALID_CREDENTIALS'
        ? 'login.error.invalidCredentials'
        : code === 'TENANT_DISABLED'
          ? 'login.error.tenantDisabled'
          : 'login.error.generic';
    return this.translate.instant(key);
  }
}
