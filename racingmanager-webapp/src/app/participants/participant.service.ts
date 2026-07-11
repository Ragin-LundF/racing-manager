import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateParticipantRequest,
  ImportCsvRequest,
  ImportResponse,
  ParticipantResponse,
  RandomizeResponse,
  UpdateParticipantRequest,
} from './participant.models';

@Injectable({ providedIn: 'root' })
export class ParticipantService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080';

  private get headers(): HttpHeaders {
    const sessionId = localStorage.getItem('racingmanager_session');
    let headers = new HttpHeaders();
    if (sessionId) {
      headers = headers.set('X-Session-Id', sessionId);
    }
    return headers;
  }

  findByEventId(eventId: string): Observable<ParticipantResponse[]> {
    return this.http.get<ParticipantResponse[]>(`${this.baseUrl}/api/v1/events/${eventId}/participants`, {
      headers: this.headers,
    });
  }

  findById(eventId: string, id: string): Observable<ParticipantResponse> {
    return this.http.get<ParticipantResponse>(`${this.baseUrl}/api/v1/events/${eventId}/participants/${id}`, {
      headers: this.headers,
    });
  }

  create(eventId: string, request: CreateParticipantRequest): Observable<ParticipantResponse> {
    return this.http.post<ParticipantResponse>(`${this.baseUrl}/api/v1/events/${eventId}/participants`, request, {
      headers: this.headers,
    });
  }

  update(eventId: string, id: string, request: UpdateParticipantRequest): Observable<ParticipantResponse> {
    return this.http.put<ParticipantResponse>(`${this.baseUrl}/api/v1/events/${eventId}/participants/${id}`, request, {
      headers: this.headers,
    });
  }

  deactivate(eventId: string, id: string): Observable<ParticipantResponse> {
    return this.http.post<ParticipantResponse>(`${this.baseUrl}/api/v1/events/${eventId}/participants/${id}/deactivate`, {}, {
      headers: this.headers,
    });
  }

  reactivate(eventId: string, id: string): Observable<ParticipantResponse> {
    return this.http.post<ParticipantResponse>(`${this.baseUrl}/api/v1/events/${eventId}/participants/${id}/reactivate`, {}, {
      headers: this.headers,
    });
  }

  randomize(eventId: string, force = false): Observable<RandomizeResponse> {
    return this.http.post<RandomizeResponse>(`${this.baseUrl}/api/v1/events/${eventId}/participants/randomize`, { force }, {
      headers: this.headers,
    });
  }

  importCsv(eventId: string, request: ImportCsvRequest): Observable<ImportResponse> {
    return this.http.post<ImportResponse>(`${this.baseUrl}/api/v1/events/${eventId}/participants/import`, request, {
      headers: this.headers,
    });
  }
}
