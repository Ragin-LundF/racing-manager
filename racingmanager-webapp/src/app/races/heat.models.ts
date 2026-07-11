export interface HeatLaneResponse {
  lane: number;
  participantId: string;
  participantStartNumber: number;
  participantFirstName: string;
  participantLastName: string;
}

export interface MeasurementResponse {
  id: string;
  heatId: string;
  lane: number;
  durationNanos: number;
  outcome: string;
  receivedAt: string;
}

export interface HeatResponse {
  id: string;
  eventId: string;
  round: number;
  heatNumber: number;
  status: string;
  lanes: HeatLaneResponse[];
  measurements: MeasurementResponse[];
  createdAt: string;
  armedAt: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface CreateHeatRequest {
  participantIds: string[];
}

export interface AddMeasurementRequest {
  lane: number;
  durationNanos: number;
  outcome: string;
}

export interface HeatStateChangeEvent {
  type: string;
  heat: HeatResponse;
}
