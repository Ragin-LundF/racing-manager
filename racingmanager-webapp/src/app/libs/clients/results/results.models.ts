export interface EventResultSnapshotResponse {
  event: EventResultSummaryResponse;
  qualificationRankings: QualificationResultEntryResponse[];
  knockoutResults: KnockoutResultEntryResponse[];
  allHeats: HeatResultEntryResponse[];
  measurementType: string;
  isSimulated: boolean;
}

export interface EventResultSummaryResponse {
  id: string;
  name: string;
  description: string | null;
  status: string;
  laneType: string;
  measurementType: string;
  createdAt: string;
  activatedAt: string | null;
  completedAt: string | null;
}

export interface QualificationResultEntryResponse {
  participantId: string;
  startNumber: number;
  firstName: string;
  lastName: string;
  club: string | null;
  bestTimeNanos: number | null;
  totalTimeNanos: number | null;
  completedRuns: number;
  dnfCount: number;
  rank: number;
}

export interface KnockoutResultEntryResponse {
  rank: number;
  participantId: string;
  firstName: string;
  lastName: string;
  startNumber: number;
  club: string | null;
}

export interface HeatResultEntryResponse {
  id: string;
  round: number;
  heatNumber: number;
  status: string;
  lanes: HeatResultLaneResponse[];
  measurements: HeatResultMeasurementResponse[];
  startedAt: string | null;
  finishedAt: string | null;
}

export interface HeatResultLaneResponse {
  lane: number;
  participantId: string;
  participantStartNumber: number;
  participantFirstName: string;
  participantLastName: string;
}

export interface HeatResultMeasurementResponse {
  id: string;
  lane: number;
  durationNanos: number;
  outcome: string;
  receivedAt: string;
}

export interface JsonExportResponse {
  schemaVersion: number;
  exportedAt: string;
  event: EventResultSnapshotResponse;
}

export interface BackupResponse {
  schemaVersion: number;
  exportedAt: string;
  event: EventResultSnapshotResponse;
}

export interface RestoreResponse {
  eventId: string;
  name: string;
  status: string;
}
