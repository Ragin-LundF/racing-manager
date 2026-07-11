import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, shareReplay } from 'rxjs';
import { BuildInfoResponse, HealthResponse } from './health.models';

@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8080';

  private readonly health$ = this.http
    .get<HealthResponse>(`${this.baseUrl}/api/v1/health`)
    .pipe(shareReplay(1));

  private readonly buildInfo$ = this.http
    .get<BuildInfoResponse>(`${this.baseUrl}/api/v1/build-info`)
    .pipe(shareReplay(1));

  checkHealth(): Observable<HealthResponse | null> {
    return this.health$.pipe(catchError(() => of(null)));
  }

  getBuildInfo(): Observable<BuildInfoResponse | null> {
    return this.buildInfo$.pipe(catchError(() => of(null)));
  }

  isAvailable(): Observable<boolean> {
    return this.health$.pipe(
      map((r) => r.status === 'UP'),
      catchError(() => of(false)),
    );
  }
}
