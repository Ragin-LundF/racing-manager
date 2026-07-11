import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../core/api.config';
import {
  CreateParticipantRequest,
  ImportCsvRequest,
  ImportResponse,
  ParticipantResponse,
  RandomizeResponse,
  UpdateParticipantRequest,
} from './participant.models';

@Injectable({ providedIn: 'root' })
export class ParticipantClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  findByEventId(eventId: string): Observable<ParticipantResponse[]> {
    return this.http.get<ParticipantResponse[]>(`${this.baseUrl}/api/v1/events/${eventId}/participants`);
  }

  findById(eventId: string, id: string): Observable<ParticipantResponse> {
    return this.http.get<ParticipantResponse>(`${this.baseUrl}/api/v1/events/${eventId}/participants/${id}`);
  }

  create(eventId: string, request: CreateParticipantRequest): Observable<ParticipantResponse> {
    return this.http.post<ParticipantResponse>(`${this.baseUrl}/api/v1/events/${eventId}/participants`, request);
  }

  update(eventId: string, id: string, request: UpdateParticipantRequest): Observable<ParticipantResponse> {
    return this.http.put<ParticipantResponse>(`${this.baseUrl}/api/v1/events/${eventId}/participants/${id}`, request);
  }

  deactivate(eventId: string, id: string): Observable<ParticipantResponse> {
    return this.http.post<ParticipantResponse>(`${this.baseUrl}/api/v1/events/${eventId}/participants/${id}/deactivate`, {});
  }

  reactivate(eventId: string, id: string): Observable<ParticipantResponse> {
    return this.http.post<ParticipantResponse>(`${this.baseUrl}/api/v1/events/${eventId}/participants/${id}/reactivate`, {});
  }

  randomize(eventId: string, force = false): Observable<RandomizeResponse> {
    return this.http.post<RandomizeResponse>(`${this.baseUrl}/api/v1/events/${eventId}/participants/randomize`, { force });
  }

  importCsv(eventId: string, request: ImportCsvRequest): Observable<ImportResponse> {
    return this.http.post<ImportResponse>(`${this.baseUrl}/api/v1/events/${eventId}/participants/import`, request);
  }
}
