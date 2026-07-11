import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SpectatorEventListResponse, SpectatorSnapshotResponse } from './spectator.models';

@Injectable({ providedIn: 'root' })
export class SpectatorClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/v1/public';

  getEvents(): Observable<SpectatorEventListResponse> {
    return this.http.get<SpectatorEventListResponse>(`${this.baseUrl}/events`);
  }

  getSnapshot(eventId: string): Observable<SpectatorSnapshotResponse> {
    return this.http.get<SpectatorSnapshotResponse>(`${this.baseUrl}/events/${eventId}/snapshot`);
  }

  getLiveWebSocketUrl(eventId: string): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}${this.baseUrl}/events/${eventId}/live`;
  }
}
