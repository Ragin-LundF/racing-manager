export interface QualificationResponse {
  id: string;
  eventId: string;
  status: string;
  numberOfRuns: number;
  seed: number;
  createdAt: string;
  updatedAt: string | null;
  finalizedAt: string | null;
  finalizedBy: string | null;
}

export interface SetupQualificationRequest {
  numberOfRuns: number;
}

export interface QualificationRankingResponse {
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

export interface QualificationProgressResponse {
  status: string;
  totalHeats: number;
  completedHeats: number;
  inProgressHeats: number;
  plannedHeats: number;
  cancelledHeats: number;
  totalParticipants: number;
  participantsWithResults: number;
}

export interface HeatScheduleResponse {
  id: string;
  eventId: string;
  round: number;
  heatNumber: number;
  status: string;
  lanes: HeatLaneScheduleResponse[];
  measurements: MeasurementScheduleResponse[];
  createdAt: string;
  armedAt: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface HeatLaneScheduleResponse {
  lane: number;
  participantId: string;
  participantStartNumber: number;
  participantFirstName: string;
  participantLastName: string;
}

export interface MeasurementScheduleResponse {
  id: string;
  heatId: string;
  lane: number;
  durationNanos: number;
  outcome: string;
  receivedAt: string;
}
