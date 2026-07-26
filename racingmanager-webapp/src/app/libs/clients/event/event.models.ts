export interface EventSettings {
  laneType: string;
  measurementType: string;
  maxParticipants: number | null;
  /** Course length in meters, or null when unknown. */
  trackLength: number | null;
}

export interface EventResponse {
  id: string;
  name: string;
  description: string | null;
  status: string;
  settings: EventSettings;
  version: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string | null;
  activatedAt: string | null;
}

export interface CreateEventRequest {
  name: string;
  description?: string | null;
  laneType?: string;
  measurementType?: string;
  maxParticipants?: number | null;
  trackLength?: number | null;
}

export interface UpdateEventRequest {
  name: string;
  description?: string | null;
  laneType?: string;
  measurementType?: string;
  maxParticipants?: number | null;
  trackLength?: number | null;
  expectedVersion: number;
}

export interface ConflictResponse {
  code: string;
  message: string;
  expectedVersion: number;
  actualVersion: number;
}
