import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../core/auth.service';
import { LocaleSelectorComponent } from '../../i18n/locale-selector.component';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, LocaleSelectorComponent, RouterLink, TranslatePipe],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <div class="auth-locale"><app-locale-selector /></div>

        <h2>{{ 'register.title' | translate }}</h2>

        @if (errorMessage(); as msg) {
          <p class="error">{{ msg }}</p>
        }

        <form #form="ngForm" (ngSubmit)="onSubmit()">
          <label>
            <span>{{ 'register.tenantName.label' | translate }}</span>
            <input name="tenantName" [(ngModel)]="tenantName" required #tenantNameField="ngModel" />
          </label>
          @if (tenantNameField.invalid && tenantNameField.touched) {
            <small>{{ 'register.tenantName.required' | translate }}</small>
          }

          <label>
            <span>{{ 'register.tenantSlug.label' | translate }}</span>
            <input name="tenantSlug" [(ngModel)]="tenantSlug" required #tenantSlugField="ngModel" />
          </label>
          @if (tenantSlugField.invalid && tenantSlugField.touched) {
            <small>{{ 'register.tenantSlug.required' | translate }}</small>
          }

          <label>
            <span>{{ 'register.username.label' | translate }}</span>
            <input name="username" [(ngModel)]="username" required minlength="3" #usernameField="ngModel" />
          </label>
          @if (usernameField.invalid && usernameField.touched) {
            <small>{{ 'register.username.required' | translate }}</small>
          }

          <label>
            <span>{{ 'register.displayName.label' | translate }}</span>
            <input name="displayName" [(ngModel)]="displayName" required #displayNameField="ngModel" />
          </label>
          @if (displayNameField.invalid && displayNameField.touched) {
            <small>{{ 'register.displayName.required' | translate }}</small>
          }

          <label>
            <span>{{ 'register.password.label' | translate }}</span>
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
            <small>{{ 'register.password.required' | translate }}</small>
          }

          <label>
            <span>{{ 'register.confirmPassword' | translate }}</span>
            <input
              type="password"
              name="confirmPassword"
              [(ngModel)]="confirmPassword"
              required
              #confirmField="ngModel"
            />
          </label>
          @if (confirmField.touched && password !== confirmPassword) {
            <small>{{ 'register.password.mismatch' | translate }}</small>
          }

          <button type="submit" [disabled]="form.invalid || password !== confirmPassword">
            {{ 'register.submit' | translate }}
          </button>
        </form>

        <a [routerLink]="['/login']">{{ 'register.loginLink' | translate }}</a>
      </div>
    </div>
  `,
  styleUrl: './register.component.scss',
})
export class RegisterComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);

  protected tenantName = '';
  protected tenantSlug = '';
  protected username = '';
  protected displayName = '';
  protected password = '';
  protected confirmPassword = '';
  protected errorMessage = signal<string | null>(null);

  protected onSubmit(): void {
    this.errorMessage.set(null);
    this.authService
      .register({
        tenantName: this.tenantName,
        tenantSlug: this.tenantSlug,
        username: this.username,
        displayName: this.displayName,
        password: this.password,
      })
      .subscribe((res) => {
        if ('code' in res) {
          this.errorMessage.set(this.localizeError(res.code));
        } else {
          this.router.navigate(['/racemanager']);
        }
      });
  }

  private localizeError(code: string): string {
    const key =
      code === 'TENANT_SLUG_TAKEN'
        ? 'register.error.slugTaken'
        : code === 'ALREADY_SETUP'
          ? 'register.error.alreadySetUp'
          : 'register.error.generic';
    return this.translate.instant(key);
  }
}
