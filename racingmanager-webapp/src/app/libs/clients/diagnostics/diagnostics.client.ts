import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of } from 'rxjs';
import { API_BASE_URL } from '../core/api.config';
import {
  DiagnosticsResponse,
  ReadinessResponse,
  RecoveryActionResponse,
} from './diagnostics.models';

@Injectable({ providedIn: 'root' })
export class DiagnosticsClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getDiagnostics(): Observable<DiagnosticsResponse> {
    return this.http.get<DiagnosticsResponse>(`${this.baseUrl}/api/v1/diagnostics`);
  }

  getReadiness(): Observable<ReadinessResponse | null> {
    return this.http.get<ReadinessResponse>(`${this.baseUrl}/api/v1/readiness`).pipe(catchError(() => of(null)));
  }

  recoverHeat(heatId: string, action: string): Observable<RecoveryActionResponse> {
    const body = new URLSearchParams({ heatId, action });
    return this.http.post<RecoveryActionResponse>(
      `${this.baseUrl}/api/v1/diagnostics/recover`,
      body.toString(),
      { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } },
    );
  }
}
