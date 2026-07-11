import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  QualificationResponse,
  SetupQualificationRequest,
  QualificationRankingResponse,
  QualificationProgressResponse,
  HeatScheduleResponse,
} from './qualification.models';

@Injectable({ providedIn: 'root' })
export class QualificationService {
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

  findByEventId(eventId: string): Observable<QualificationResponse> {
    return this.http.get<QualificationResponse>(
      `${this.baseUrl}/api/v1/events/${eventId}/qualification`,
      { headers: this.headers },
    );
  }

  setup(eventId: string, request: SetupQualificationRequest): Observable<QualificationResponse> {
    return this.http.post<QualificationResponse>(
      `${this.baseUrl}/api/v1/events/${eventId}/qualification/setup`,
      request,
      { headers: this.headers },
    );
  }

  generateSchedule(eventId: string): Observable<QualificationResponse> {
    return this.http.post<QualificationResponse>(
      `${this.baseUrl}/api/v1/events/${eventId}/qualification/schedule`,
      {},
      { headers: this.headers },
    );
  }

  getSchedule(eventId: string): Observable<HeatScheduleResponse[]> {
    return this.http.get<HeatScheduleResponse[]>(
      `${this.baseUrl}/api/v1/events/${eventId}/qualification/schedule`,
      { headers: this.headers },
    );
  }

  getRankings(eventId: string): Observable<QualificationRankingResponse[]> {
    return this.http.get<QualificationRankingResponse[]>(
      `${this.baseUrl}/api/v1/events/${eventId}/qualification/rankings`,
      { headers: this.headers },
    );
  }

  getProgress(eventId: string): Observable<QualificationProgressResponse> {
    return this.http.get<QualificationProgressResponse>(
      `${this.baseUrl}/api/v1/events/${eventId}/qualification/progress`,
      { headers: this.headers },
    );
  }

  finalize(eventId: string): Observable<void> {
    return this.http.post<void>(
      `${this.baseUrl}/api/v1/events/${eventId}/qualification/finalize`,
      {},
      { headers: this.headers },
    );
  }

  reopen(eventId: string): Observable<void> {
    return this.http.post<void>(
      `${this.baseUrl}/api/v1/events/${eventId}/qualification/reopen`,
      {},
      { headers: this.headers },
    );
  }
}
