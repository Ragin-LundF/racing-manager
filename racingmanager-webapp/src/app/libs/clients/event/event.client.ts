import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../core/api.config';
import { CreateEventRequest, EventResponse, UpdateEventRequest } from './event.models';

@Injectable({ providedIn: 'root' })
export class EventClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  create(request: CreateEventRequest): Observable<EventResponse> {
    return this.http.post<EventResponse>(`${this.baseUrl}/api/v1/events`, request);
  }

  findAll(): Observable<EventResponse[]> {
    return this.http.get<EventResponse[]>(`${this.baseUrl}/api/v1/events`);
  }

  findById(id: string): Observable<EventResponse> {
    return this.http.get<EventResponse>(`${this.baseUrl}/api/v1/events/${id}`);
  }

  update(id: string, request: UpdateEventRequest): Observable<EventResponse> {
    return this.http.put<EventResponse>(`${this.baseUrl}/api/v1/events/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/api/v1/events/${id}`);
  }

  activate(id: string): Observable<EventResponse> {
    return this.http.post<EventResponse>(`${this.baseUrl}/api/v1/events/${id}/activate`, {});
  }

  archive(id: string): Observable<EventResponse> {
    return this.http.post<EventResponse>(`${this.baseUrl}/api/v1/events/${id}/archive`, {});
  }
}
