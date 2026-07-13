import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { TenantClient } from '../libs/clients/tenant/tenant.client';
import { TenantResponse, TenantUserResponse } from '../libs/clients/tenant/tenant.models';

@Component({
  selector: 'app-tenant-settings',
  standalone: true,
  imports: [FormsModule, TranslatePipe],
  templateUrl: './tenant-settings.component.html',
  styleUrl: './tenant-settings.component.scss',
})
export class TenantSettingsComponent {
  private readonly tenantClient = inject(TenantClient);

  protected readonly tenant = signal<TenantResponse | null>(null);
  protected readonly users = signal<TenantUserResponse[]>([]);
  protected readonly error = signal<string | null>(null);

  protected displayName = '';

  protected newUsername = '';
  protected newPassword = '';
  protected newDisplayName = '';
  protected newRole = 'DIRECTOR';

  constructor() {
    this.load();
  }

  private load(): void {
    this.tenantClient.getTenant().subscribe((tenant) => {
      this.tenant.set(tenant);
      this.displayName = tenant.displayName;
    });
    this.tenantClient.listUsers().subscribe((users) => this.users.set(users));
  }

  protected onSaveTenant(): void {
    this.error.set(null);
    this.tenantClient.updateTenant({ displayName: this.displayName }).subscribe({
      next: (tenant) => this.tenant.set(tenant),
      error: () => this.error.set('tenant.error.generic'),
    });
  }

  protected onCreateUser(): void {
    this.error.set(null);
    this.tenantClient
      .createUser({
        username: this.newUsername,
        password: this.newPassword,
        displayName: this.newDisplayName,
        role: this.newRole,
      })
      .subscribe({
        next: (user) => {
          this.users.update((list) => [...list, user]);
          this.newUsername = '';
          this.newPassword = '';
          this.newDisplayName = '';
          this.newRole = 'DIRECTOR';
        },
        error: (err) => this.error.set(err.error?.code === 'USERNAME_TAKEN' ? 'tenant.error.usernameTaken' : 'tenant.error.generic'),
      });
  }

  protected onToggleUserStatus(user: TenantUserResponse): void {
    const status = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    this.tenantClient.updateUser(user.userId, { status }).subscribe((updated) => {
      this.users.update((list) => list.map((u) => (u.userId === updated.userId ? updated : u)));
    });
  }
}
