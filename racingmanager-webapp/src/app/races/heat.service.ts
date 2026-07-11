import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HeatResponse, CreateHeatRequest, AddMeasurementRequest } from './heat.models';

@Injectable({ providedIn: 'root' })
export class HeatService {
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

  findByEventId(eventId: string): Observable<HeatResponse[]> {
    return this.http.get<HeatResponse[]>(`${this.baseUrl}/api/v1/events/${eventId}/heats`, {
      headers: this.headers,
    });
  }

  findLatestByEventId(eventId: string): Observable<HeatResponse> {
    return this.http.get<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/latest`, {
      headers: this.headers,
    });
  }

  findById(eventId: string, id: string): Observable<HeatResponse> {
    return this.http.get<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}`, {
      headers: this.headers,
    });
  }

  create(eventId: string, request: CreateHeatRequest): Observable<HeatResponse> {
    return this.http.post<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats`, request, {
      headers: this.headers,
    });
  }

  arm(eventId: string, id: string): Observable<HeatResponse> {
    return this.http.post<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/arm`, {}, {
      headers: this.headers,
    });
  }

  start(eventId: string, id: string): Observable<HeatResponse> {
    return this.http.post<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/start`, {}, {
      headers: this.headers,
    });
  }

  finish(eventId: string, id: string): Observable<HeatResponse> {
    return this.http.post<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/finish`, {}, {
      headers: this.headers,
    });
  }

  cancel(eventId: string, id: string): Observable<HeatResponse> {
    return this.http.post<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/cancel`, {}, {
      headers: this.headers,
    });
  }

  acceptResult(eventId: string, id: string): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/accept`, {}, {
      headers: this.headers,
    });
  }

  rejectResult(eventId: string, id: string): Observable<{ status: string }> {
    return this.http.post<{ status: string }>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/reject`, {}, {
      headers: this.headers,
    });
  }

  repeat(eventId: string, id: string): Observable<HeatResponse> {
    return this.http.post<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/repeat`, {}, {
      headers: this.headers,
    });
  }

  addMeasurement(eventId: string, id: string, request: AddMeasurementRequest): Observable<HeatResponse> {
    return this.http.put<HeatResponse>(`${this.baseUrl}/api/v1/events/${eventId}/heats/${id}/measurements`, request, {
      headers: this.headers,
    });
  }

  connectLive(eventId: string): WebSocket {
    const sessionId = localStorage.getItem('racingmanager_session');
    return new WebSocket(`ws://localhost:8080/api/v1/events/${eventId}/live?X-Session-Id=${sessionId}`);
  }
}
