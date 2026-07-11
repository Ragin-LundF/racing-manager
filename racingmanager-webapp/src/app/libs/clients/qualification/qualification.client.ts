import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../core/api.config';
import {
  QualificationResponse,
  SetupQualificationRequest,
  QualificationRankingResponse,
  QualificationProgressResponse,
  HeatScheduleResponse,
} from './qualification.models';

@Injectable({ providedIn: 'root' })
export class QualificationClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  findByEventId(eventId: string): Observable<QualificationResponse> {
    return this.http.get<QualificationResponse>(`${this.baseUrl}/api/v1/events/${eventId}/qualification`);
  }

  setup(eventId: string, request: SetupQualificationRequest): Observable<QualificationResponse> {
    return this.http.post<QualificationResponse>(`${this.baseUrl}/api/v1/events/${eventId}/qualification/setup`, request);
  }

  generateSchedule(eventId: string): Observable<QualificationResponse> {
    return this.http.post<QualificationResponse>(`${this.baseUrl}/api/v1/events/${eventId}/qualification/schedule`, {});
  }

  getSchedule(eventId: string): Observable<HeatScheduleResponse[]> {
    return this.http.get<HeatScheduleResponse[]>(`${this.baseUrl}/api/v1/events/${eventId}/qualification/schedule`);
  }

  getRankings(eventId: string): Observable<QualificationRankingResponse[]> {
    return this.http.get<QualificationRankingResponse[]>(`${this.baseUrl}/api/v1/events/${eventId}/qualification/rankings`);
  }

  getProgress(eventId: string): Observable<QualificationProgressResponse> {
    return this.http.get<QualificationProgressResponse>(`${this.baseUrl}/api/v1/events/${eventId}/qualification/progress`);
  }

  finalize(eventId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/events/${eventId}/qualification/finalize`, {});
  }

  reopen(eventId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/events/${eventId}/qualification/reopen`, {});
  }
}
