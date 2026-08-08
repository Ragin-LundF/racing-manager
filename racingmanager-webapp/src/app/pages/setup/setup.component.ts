import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { catchError, of } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { AdminClient } from '../../libs/clients/admin/admin.client';
import { ErrorResponse } from '../../libs/clients/auth/auth.models';
import { LocaleSelectorComponent } from '../../i18n/locale-selector.component';

/** In `local` mode this bootstraps the single implicit-tenant admin
    (unchanged from before). In `hosted` mode there is no implicit tenant to
    bootstrap an admin into — `/api/v1/auth/setup` is local-only — so this
    page instead bootstraps the platform supervisor and points to `/register`
    for creating a tenant + its first admin. */
@Component({
  selector: 'app-setup',
  standalone: true,
  imports: [FormsModule, LocaleSelectorComponent, RouterLink, TranslatePipe],
  template: `
    <div class="auth-page">
      <div class="auth-card">
        <div class="auth-locale"><app-locale-selector /></div>

        @if (mode(); as m) {
          <h2>{{ (m === 'HOSTED' ? 'setup.supervisor.title' : 'setup.title') | translate }}</h2>

          @if (alreadySetUp()) {
            <p>{{ (m === 'HOSTED' ? 'setup.supervisor.alreadySetUp' : 'setup.alreadySetUp') | translate }}</p>
            <a [routerLink]="['/login']">{{ 'setup.loginLink' | translate }}</a>
          } @else {
            @if (error(); as msg) {
              <p class="error">{{ msg }}</p>
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
                {{ (m === 'HOSTED' ? 'setup.supervisor.submit' : 'setup.submit') | translate }}
              </button>
            </form>

            <!-- Offered in both modes: locally, registering a named tenant is
                 the alternative to bootstrapping the implicit one, and it is
                 still open here because no user exists yet. -->
            <a [routerLink]="['/register']">{{ 'setup.registerTenantLink' | translate }}</a>
          }
        }
      </div>
    </div>
  `,
  styleUrl: './setup.component.scss',
})
export class SetupComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly adminClient = inject(AdminClient);
  private readonly router = inject(Router);
  private readonly translate = inject(TranslateService);

  protected username = '';
  protected displayName = '';
  protected password = '';
  protected confirmPassword = '';
  protected error = signal<string | null>(null);

  protected readonly mode = signal<'LOCAL' | 'HOSTED' | null>(null);
  protected readonly alreadySetUp = signal(false);
  private readonly isHosted = computed(() => this.mode() === 'HOSTED');

  ngOnInit(): void {
    this.authService.getSetupStatus().subscribe((status) => {
      this.mode.set(status.mode);
      if (status.mode === 'LOCAL') {
        this.alreadySetUp.set(!status.firstRun);
      } else {
        this.adminClient.getSetupStatus().subscribe((supervisorStatus) => {
          this.alreadySetUp.set(!supervisorStatus.firstRun);
        });
      }
    });
  }

  protected onSubmit(): void {
    this.error.set(null);
    const request = { username: this.username, password: this.password, displayName: this.displayName };
    const submit$ = this.isHosted()
      ? this.adminClient.setup(request).pipe(catchError((err) => of(err.error as ErrorResponse)))
      : this.authService.setup(request);

    submit$.subscribe((res) => {
      if ('code' in res) {
        this.error.set(this.localizeError(res.code));
      } else {
        this.router.navigate(['/login']);
      }
    });
  }

  private localizeError(code: string): string {
    const key = code === 'ALREADY_SETUP' ? 'setup.error.alreadySetUp' : 'setup.error.generic';
    return this.translate.instant(key);
  }
}
