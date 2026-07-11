import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth.service';
import { LocaleSelectorComponent } from '../../i18n/locale-selector.component';

@Component({
  selector: 'app-setup',
  standalone: true,
  imports: [FormsModule, LocaleSelectorComponent, TranslatePipe],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <div class="auth-locale"><app-locale-selector /></div>

        <h2>{{ 'setup.title' | translate }}</h2>

        @if (error()) {
          <p class="error">{{ 'setup.error.generic' | translate }}</p>
        }

        <form #form="ngForm" (ngSubmit)="onSubmit()">
          <label>
            <span>{{ 'setup.username.label' | translate }}</span>
            <input name="username" [(ngModel)]="username" required minlength="3" #usernameField="ngModel" />
          </label>
          @if (usernameField.invalid && usernameField.touched) {
            <small>{{ 'setup.username.required' | translate }}</small>
          }

          <label>
            <span>{{ 'setup.displayName.label' | translate }}</span>
            <input name="displayName" [(ngModel)]="displayName" required #displayNameField="ngModel" />
          </label>
          @if (displayNameField.invalid && displayNameField.touched) {
            <small>{{ 'setup.displayName.required' | translate }}</small>
          }

          <label>
            <span>{{ 'setup.password.label' | translate }}</span>
            <input type="password" name="password" [(ngModel)]="password" required minlength="8" #passwordField="ngModel" />
          </label>
          @if (passwordField.invalid && passwordField.touched) {
            <small>{{ 'setup.password.required' | translate }}</small>
          }

          <label>
            <span>{{ 'setup.confirmPassword' | translate }}</span>
            <input type="password" name="confirmPassword" [(ngModel)]="confirmPassword" required #confirmField="ngModel" />
          </label>
          @if (confirmField.touched && password !== confirmPassword) {
            <small>{{ 'setup.password.mismatch' | translate }}</small>
          }

          <button type="submit" [disabled]="form.invalid || password !== confirmPassword">
            {{ 'setup.submit' | translate }}
          </button>
        </form>
      </div>
    </div>
  `,
  styleUrl: './setup.component.scss',
})
export class SetupComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

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
          this.router.navigate(['/login']);
        }
      });
  }
}
