import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { LocaleService } from '../../i18n/locale.service';
import { LocaleSelectorComponent } from '../../i18n/locale-selector.component';

@Component({
  selector: 'app-setup',
  standalone: true,
  imports: [FormsModule, LocaleSelectorComponent],
  template: `
    <app-locale-selector />

    <h2 i18n="@@setup.title">Administrator Setup</h2>

    @if (error()) {
      <p class="error" i18n="@@setup.error.generic">Setup failed. Please try again.</p>
    }

    <form #form="ngForm" (ngSubmit)="onSubmit()">
      <label i18n="@@setup.username">
        Username
        <input
          name="username"
          [(ngModel)]="username"
          required
          minlength="3"
          #usernameField="ngModel"
        />
      </label>
      @if (usernameField.invalid && usernameField.touched) {
        <small i18n="@@setup.username.required">Username is required (min. 3 characters).</small>
      }

      <label i18n="@@setup.displayName">
        Display Name
        <input
          name="displayName"
          [(ngModel)]="displayName"
          required
          #displayNameField="ngModel"
        />
      </label>
      @if (displayNameField.invalid && displayNameField.touched) {
        <small i18n="@@setup.displayName.required">Display name is required.</small>
      }

      <label i18n="@@setup.password">
        Password
        <input
          type="password"
          name="password"
          [(ngModel)]="password"
          required
          minlength="8"
          #passwordField="ngModel"
        />
      </label>
      @if (passwordField.invalid && passwordField.touched) {
        <small i18n="@@setup.password.required">Password is required (min. 8 characters).</small>
      }

      <label i18n="@@setup.confirmPassword">
        Confirm Password
        <input
          type="password"
          name="confirmPassword"
          [(ngModel)]="confirmPassword"
          required
          #confirmField="ngModel"
        />
      </label>
      @if (confirmField.touched && password !== confirmPassword) {
        <small i18n="@@setup.password.mismatch">Passwords do not match.</small>
      }

      <button type="submit" [disabled]="form.invalid || password !== confirmPassword" i18n="@@setup.submit">
        Create Administrator
      </button>
    </form>
  `,
  styleUrl: './setup.component.scss',
})
export class SetupComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly localeService = inject(LocaleService);

  protected username = '';
  protected displayName = '';
  protected password = '';
  protected confirmPassword = '';
  protected error = signal(false);

  protected onSubmit(): void {
    this.error.set(false);
    this.authService
      .setup({
        username: this.username,
        password: this.password,
        displayName: this.displayName,
      })
      .subscribe((res) => {
        if ('code' in res) {
          this.error.set(true);
        } else {
          this.router.navigate([
            this.localeService.currentLocale(),
            'login',
          ]);
        }
      });
  }
}
