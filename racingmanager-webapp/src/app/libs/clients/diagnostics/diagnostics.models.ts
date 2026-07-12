export interface DiagnosticsResponse {
  database: DatabaseHealth;
  events: EventSummary;
  unfinishedHeats: UnfinishedHeat[];
  version: string;
}

export interface DatabaseHealth {
  connected: boolean;
  pingMs: number;
}

export interface EventSummary {
  total: number;
  draft: number;
  active: number;
  completed: number;
  archived: number;
  totalParticipants: number;
  totalHeats: number;
}

export interface UnfinishedHeat {
  heatId: string;
  heatNumber: number;
  round: number;
  status: string;
  eventId: string;
  eventName: string;
}

export interface RecoveryActionResponse {
  heatId: string;
  action: string;
}

export interface ReadinessResponse {
  status: string;
  checks: ReadinessCheck[];
}

export interface ReadinessCheck {
  name: string;
  status: string;
  error: string | null;
}
