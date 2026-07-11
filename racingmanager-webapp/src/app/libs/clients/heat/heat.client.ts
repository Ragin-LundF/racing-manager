import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL, SESSION_STORAGE_KEY } from '../core/api.config';
import { HeatResponse, CreateHeatRequest, AddMeasurementRequest } from './heat.models';

@Injectable({ providedIn: 'root' })
export class HeatClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  findByEventId(eventId: string): Observable<HeatResponse[]> {
    return this.http.get<HeatResponse[]>(`${this.baseUrl}/api/v1/events/${eventId}/heats`);
  }

  findLatestByEventId(eventId: string): Observable<HeatResponse> {
    return this.http.get<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/latest`);
  }

  findById(eventId: string, id: string): Observable<HeatResponse> {
    return this.http.get<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}`);
  }

  create(eventId: string, request: CreateHeatRequest): Observable<HeatResponse> {
    return this.http.post<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats`, request);
  }

  arm(eventId: string, id: string): Observable<HeatResponse> {
    return this.http.post<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/arm`, {});
  }

  start(eventId: string, id: string): Observable<HeatResponse> {
    return this.http.post<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/start`, {});
  }

  finish(eventId: string, id: string): Observable<HeatResponse> {
    return this.http.post<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/finish`, {});
  }

  cancel(eventId: string, id: string): Observable<HeatResponse> {
    return this.http.post<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/cancel`, {});
  }

  acceptResult(eventId: string, id: string): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/accept`, {});
  }

  rejectResult(eventId: string, id: string): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/reject`, {});
  }

  repeat(eventId: string, id: string): Observable<HeatResponse> {
    return this.http.post<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/repeat`, {});
  }

  addMeasurement(eventId: string, id: string, request: AddMeasurementRequest): Observable<HeatResponse> {
    return this.http.put<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/measurements`, request);
  }

  connectLive(eventId: string): WebSocket {
    const sessionId = localStorage.getItem(SESSION_STORAGE_KEY);
    const wsBase = this.baseUrl.replace(/^http/, 'ws');
    return new WebSocket(`${wsBase}/api/v1/events/${eventId}/live?X-Session-Id=${sessionId}`);
  }
}
