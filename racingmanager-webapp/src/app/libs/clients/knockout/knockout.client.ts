import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../core/api.config';
import {
  KnockoutTournamentResponse,
  SetupKnockoutRequest,
  KnockoutMatchResponse,
  RecordMatchResultRequest,
  CreateHeatForMatchRequest,
  KnockoutResultEntryResponse,
} from './knockout.models';

@Injectable({ providedIn: 'root' })
export class KnockoutClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  findByEventId(eventId: string): Observable<KnockoutTournamentResponse> {
    return this.http.get<KnockoutTournamentResponse>(`${this.baseUrl}/api/v1/events/${eventId}/knockout`);
  }

  setup(eventId: string, request: SetupKnockoutRequest): Observable<KnockoutTournamentResponse> {
    return this.http.post<KnockoutTournamentResponse>(`${this.baseUrl}/api/v1/events/${eventId}/knockout/setup`, request);
  }

  generatePairings(eventId: string): Observable<KnockoutTournamentResponse> {
    return this.http.post<KnockoutTournamentResponse>(`${this.baseUrl}/api/v1/events/${eventId}/knockout/pairings`, {});
  }

  getMatches(eventId: string): Observable<KnockoutMatchResponse[]> {
    return this.http.get<KnockoutMatchResponse[]>(`${this.baseUrl}/api/v1/events/${eventId}/knockout/matches`);
  }

  createHeatForMatch(eventId: string, request: CreateHeatForMatchRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/events/${eventId}/knockout/heat`, request);
  }

  recordMatchResult(eventId: string, request: RecordMatchResultRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/events/${eventId}/knockout/result`, request);
  }

  finalize(eventId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/events/${eventId}/knockout/finalize`, {});
  }

  getResults(eventId: string): Observable<KnockoutResultEntryResponse[]> {
    return this.http.get<KnockoutResultEntryResponse[]>(`${this.baseUrl}/api/v1/events/${eventId}/knockout/results`);
  }
}
