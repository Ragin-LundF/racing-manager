import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../core/api.config';
import { SpectatorExchangeResponse, SpectatorSnapshotResponse, SpectatorTokenResponse } from './spectator.models';

/** Token-bound spectator access (design §F). `issueToken`/`exchange` run
    under the operator's normal Bearer session; `getSnapshot`/
    `getLiveWebSocketUrl` are called with the short-lived spectator token
    itself, passed explicitly rather than via the app's operator session. */
@Injectable({ providedIn: 'root' })
export class SpectatorClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  issueToken(eventId: string): Observable<SpectatorTokenResponse> {
    return this.http.post<SpectatorTokenResponse>(`${this.baseUrl}/api/v1/events/${eventId}/spectator-token`, {});
  }

  exchange(code: string): Observable<SpectatorExchangeResponse> {
    return this.http.post<SpectatorExchangeResponse>(`${this.baseUrl}/api/v1/spectator/exchange`, { code });
  }

  getSnapshot(spectatorToken: string): Observable<SpectatorSnapshotResponse> {
    return this.http.get<SpectatorSnapshotResponse>(`${this.baseUrl}/api/v1/spectator/snapshot`, {
      headers: { Authorization: `Bearer ${spectatorToken}` },
    });
  }

  getLiveWebSocketUrl(): string {
    const wsBase = this.baseUrl
      ? this.baseUrl.replace(/^http/, 'ws')
      : `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}`;
    return `${wsBase}/api/v1/spectator/live`;
  }
}
