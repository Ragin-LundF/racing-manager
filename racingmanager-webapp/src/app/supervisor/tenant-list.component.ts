import { Component, inject, signal } from '@angular/core';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AdminClient } from '../libs/clients/admin/admin.client';
import { TenantResponse } from '../libs/clients/admin/admin.models';

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

  protected onDelete(tenant: TenantResponse): void {
    // ponytail: native prompt for slug confirmation — a styled modal is more code than the ask warrants.
    const confirmSlug = prompt(this.translate.instant('supervisor.tenants.confirmSlugPrompt'));
    if (!confirmSlug || confirmSlug !== tenant.slug) return;

    this.adminClient.deleteTenant(tenant.id, { confirmSlug }).subscribe((updated) => {
      this.tenants.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
    });
  }
}
