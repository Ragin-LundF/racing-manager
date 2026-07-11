import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { LocaleService } from '../../i18n/locale.service';
import { LocaleSelectorComponent } from '../../i18n/locale-selector.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, LocaleSelectorComponent],
  template: `
    <app-locale-selector />

    <h2 i18n="@@login.title">Login</h2>

    @if (errorMessage(); as msg) {
      <p class="error">{{ msg }}</p>
    }

    <form #form="ngForm" (ngSubmit)="onSubmit()">
      <label i18n="@@login.username">
        Username
        <input
          name="username"
          [(ngModel)]="username"
          required
          #usernameField="ngModel"
        />
      </label>
      @if (usernameField.invalid && usernameField.touched) {
        <small i18n="@@login.username.required">Username is required.</small>
      }

      <label i18n="@@login.password">
        Password
        <input
          type="password"
          name="password"
          [(ngModel)]="password"
          required
          #passwordField="ngModel"
        />
      </label>
      @if (passwordField.invalid && passwordField.touched) {
        <small i18n="@@login.password.required">Password is required.</small>
      }

      <button type="submit" [disabled]="form.invalid" i18n="@@login.submit">
        Log In
      </button>
    </form>
  `,
  styles: [
    `
      .error { color: red; }
      small { color: red; display: block; }
    `,
  ],
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly localeService = inject(LocaleService);

  protected username = '';
  protected password = '';
  protected errorMessage = signal<string | null>(null);

  protected onSubmit(): void {
    this.errorMessage.set(null);
    this.authService
      .login({ username: this.username, password: this.password })
      .subscribe((res) => {
        if ('code' in res) {
          this.errorMessage.set(this.localizeError(res.code));
        } else {
          this.router.navigate([
            this.localeService.currentLocale(),
            'director',
          ]);
        }
      });
  }

  private localizeError(code: string): string {
    switch (code) {
      case 'INVALID_CREDENTIALS':
        return $localize`:@@login.error.invalidCredentials:Invalid username or password.`;
      default:
        return $localize`:@@login.error.generic:Login failed. Please try again.`;
    }
  }
}
