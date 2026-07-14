export interface SpectatorTokenResponse {
  exchangeCode: string;
  expiresIn: number;
}

export interface SpectatorExchangeResponse {
  accessToken: string;
  expiresIn: number;
  eventId: string;
}

export interface SpectatorEventModel {
  id: string;
  name: string;
  description?: string;
  status: string;
  laneType: string;
  measurementType: string;
}

export interface SpectatorLaneModel {
  lane: number;
  participantId: string;
  participantStartNumber: number;
  participantFirstName: string;
  participantLastName: string;
  durationNanos?: number;
  outcome?: string;
}

export interface SpectatorHeatModel {
  id: string;
  heatNumber: number;
  round: number;
  status: string;
  lanes: SpectatorLaneModel[];
  hasResult: boolean;
}

export interface SpectatorRankingEntryModel {
  participantId: string;
  startNumber: number;
  firstName: string;
  lastName: string;
  club?: string;
  bestTimeNanos?: number;
  totalTimeNanos?: number;
  completedRuns: number;
  dnfCount: number;
  rank: number;
}

export interface SpectatorKnockoutMatchModel {
  id: string;
  roundNumber: number;
  matchNumber: number;
  participant1Id?: string;
  participant2Id?: string;
  winnerId?: string;
  status: string;
  isBye: boolean;
}

export interface SpectatorKnockoutRoundModel {
  roundNumber: number;
  matches: SpectatorKnockoutMatchModel[];
}

export interface SpectatorKnockoutStateModel {
  status: string;
  pairingMode: string;
  rounds: SpectatorKnockoutRoundModel[];
}

export interface SpectatorParticipantStandingModel {
  participantId: string;
  startNumber: number;
  firstName: string;
  lastName: string;
  bestQualificationTimeNanos?: number;
  bestKnockoutTimeNanos?: number;
  state: string;
  place: number;
  racing: boolean;
}

export interface SpectatorSnapshotResponse {
  event: SpectatorEventModel;
  currentHeat?: SpectatorHeatModel;
  upcomingHeats: SpectatorHeatModel[];
  qualificationRankings: SpectatorRankingEntryModel[];
  qualificationStatus?: string;
  knockout?: SpectatorKnockoutStateModel;
  knockoutStandings?: SpectatorParticipantStandingModel[];
}
