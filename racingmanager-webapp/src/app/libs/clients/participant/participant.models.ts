export interface VehicleResponse {
  id: string;
  name: string;
  category: string | null;
}

export interface ParticipantResponse {
  id: string;
  eventId: string;
  startNumber: number;
  firstName: string;
  lastName: string;
  club: string | null;
  status: string;
  sortOrder: number | null;
  vehicle: VehicleResponse | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface CreateParticipantRequest {
  startNumber?: number | null;
  firstName: string;
  lastName: string;
  club?: string | null;
  vehicleName?: string | null;
  vehicleCategory?: string | null;
}

export interface UpdateParticipantRequest {
  startNumber: number;
  firstName: string;
  lastName: string;
  club?: string | null;
}

export interface RandomizeResponse {
  seed: number;
  alreadyRandomized: boolean;
}

export interface CsvRow {
  startNumber?: number | null;
  firstName?: string | null;
  lastName?: string | null;
  club?: string | null;
  vehicleName?: string | null;
  vehicleCategory?: string | null;
}

export interface ImportCsvRequest {
  rows: CsvRow[];
}

export interface ImportError {
  rowIndex: number;
  message: string;
}

export interface ImportResponse {
  created: number;
  errors: ImportError[];
}
