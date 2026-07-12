export interface AuditEntryResponse {
  id: string;
  actorId: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  summary: string | null;
  details: string | null;
  correlationId: string | null;
  occurredAt: string;
}
