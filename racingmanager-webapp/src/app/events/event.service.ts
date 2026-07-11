import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateEventRequest,
  EventResponse,
  UpdateEventRequest,
} from './event.models';

@Injectable({ providedIn: 'root' })
export class EventService {
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

  create(request: CreateEventRequest): Observable<EventResponse> {
    return this.http.post<EventResponse>(`${this.baseUrl}/api/v1/events`, request, {
      headers: this.headers,
    });
  }

  findAll(): Observable<EventResponse[]> {
    return this.http.get<EventResponse[]>(`${this.baseUrl}/api/v1/events`, {
      headers: this.headers,
    });
  }

  findById(id: string): Observable<EventResponse> {
    return this.http.get<EventResponse>(`${this.baseUrl}/api/v1/events/${id}`, {
      headers: this.headers,
    });
  }

  update(id: string, request: UpdateEventRequest): Observable<EventResponse> {
    return this.http.put<EventResponse>(`${this.baseUrl}/api/v1/events/${id}`, request, {
      headers: this.headers,
    });
  }

  activate(id: string): Observable<EventResponse> {
    return this.http.post<EventResponse>(`${this.baseUrl}/api/v1/events/${id}/activate`, {}, {
      headers: this.headers,
    });
  }

  archive(id: string): Observable<EventResponse> {
    return this.http.post<EventResponse>(`${this.baseUrl}/api/v1/events/${id}/archive`, {}, {
      headers: this.headers,
    });
  }
}
