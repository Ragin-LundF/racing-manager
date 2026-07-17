import { Component, inject, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AdminClient } from '../libs/clients/admin/admin.client';
import { TenantResponse } from '../libs/clients/admin/admin.models';
import { ConfirmService } from '../shared/confirm/confirm.service';

@Component({
  selector: 'app-tenant-list',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './tenant-list.component.html',
  styleUrl: './tenant-list.component.scss',
})
export class TenantListComponent {
  private readonly adminClient = inject(AdminClient);
  private readonly translate = inject(TranslateService);
  private readonly confirm = inject(ConfirmService);

  protected readonly tenants = signal<TenantResponse[]>([]);

  constructor() {
    this.load();
  }

  private load(): void {
    this.adminClient.listTenants().subscribe((tenants) => this.tenants.set(tenants));
  }

  protected onDeactivate(tenant: TenantResponse): void {
    this.adminClient.deactivateTenant(tenant.id).subscribe((updated) => {
      this.tenants.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
    });
  }

  protected onReactivate(tenant: TenantResponse): void {
    this.adminClient.reactivateTenant(tenant.id).subscribe((updated) => {
      this.tenants.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
    });
  }

  protected async onDelete(tenant: TenantResponse): Promise<void> {
    const slug = tenant.slug;
    if (!slug) return;
    const ok = await this.confirm.confirm({
      message: this.translate.instant('supervisor.tenants.confirmSlugPrompt'),
      requireText: slug,
      requireTextLabel: slug,
      variant: 'danger',
    });
    if (!ok) return;

    this.adminClient.deleteTenant(tenant.id, { confirmSlug: slug }).subscribe((updated) => {
      this.tenants.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
    });
  }
}
