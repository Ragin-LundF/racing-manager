import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { DatePipe, SlicePipe } from '@angular/common';
import { AuditClient } from '../libs/clients/audit/audit.client';
import { AuditEntryResponse } from '../libs/clients/audit/audit.models';

@Component({
  selector: 'app-audit',
  standalone: true,
  imports: [TranslatePipe, DatePipe, SlicePipe],
  templateUrl: './audit.component.html',
  styleUrl: './audit.component.scss',
})
export class AuditComponent {
  private readonly auditClient = inject(AuditClient);
  private readonly route = inject(ActivatedRoute);

  protected entries = signal<AuditEntryResponse[]>([]);
  protected error = signal('');

  private get eventId(): string {
    return this.route.snapshot.paramMap.get('id')!;
  }

  constructor() {
    this.load();
  }

  protected load(): void {
    this.error.set('');
    this.auditClient.findByEventId(this.eventId).subscribe({
      next: (data) => this.entries.set(data),
      error: () => this.error.set('Failed to load audit log.'),
    });
  }

  /** Colors the action chip by intent so the log reads at a glance instead
      of as a wall of identical monospace tags. */
  protected actionChipClass(action: string): string {
    if (/DELETE|CANCEL|ARCHIVE|REVOKE|REMOVE/.test(action)) return 'chip-error';
    if (/CREATE|REGISTER|ADD|ACTIVATE|ISSUE/.test(action)) return 'chip-success';
    return 'chip-muted';
  }
}
