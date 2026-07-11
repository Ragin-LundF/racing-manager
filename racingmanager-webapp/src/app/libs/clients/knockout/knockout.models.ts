export interface KnockoutTournamentResponse {
  id: string;
  eventId: string;
  status: string;
  pairingMode: string;
  qualificationId: string;
  createdAt: string;
  updatedAt: string | null;
  finalizedAt: string | null;
  finalizedBy: string | null;
}

export interface SetupKnockoutRequest {
  pairingMode: string;
}

export interface KnockoutMatchResponse {
  id: string;
  tournamentId: string;
  roundNumber: number;
  matchNumber: number;
  participant1Id: string | null;
  participant2Id: string | null;
  winnerId: string | null;
  heatId: string | null;
  status: string;
  createdAt: string;
}

export interface RecordMatchResultRequest {
  matchId: string;
  winnerId: string;
  heatId: string;
}

export interface CreateHeatForMatchRequest {
  matchId: string;
}

export interface KnockoutResultEntryResponse {
  rank: number;
  participantId: string;
  firstName: string;
  lastName: string;
  startNumber: number;
  club: string | null;
}
