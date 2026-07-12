import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../core/api.config';
import {
  EventResultSnapshotResponse,
  JsonExportResponse,
  BackupResponse,
  RestoreResponse,
} from './results.models';

@Injectable({ providedIn: 'root' })
export class ResultsClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getSnapshot(eventId: string): Observable<EventResultSnapshotResponse> {
    return this.http.get<EventResultSnapshotResponse>(`${this.baseUrl}/api/v1/events/${eventId}/results/snapshot`);
  }

  completeEvent(eventId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/events/${eventId}/results/complete`, {});
  }

  reopenEvent(eventId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/api/v1/events/${eventId}/results/reopen`, {});
  }

  exportCsv(eventId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/api/v1/events/${eventId}/results/csv`, { responseType: 'blob' });
  }

  exportHtml(eventId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/api/v1/events/${eventId}/results/html`, { responseType: 'blob' });
  }

  exportJson(eventId: string): Observable<JsonExportResponse> {
    return this.http.get<JsonExportResponse>(`${this.baseUrl}/api/v1/events/${eventId}/results/json`);
  }

  exportBackup(eventId: string): Observable<BackupResponse> {
    return this.http.get<BackupResponse>(`${this.baseUrl}/api/v1/events/${eventId}/results/backup`);
  }

  restoreFromBackup(eventId: string, backup: BackupResponse): Observable<RestoreResponse> {
    return this.http.post<RestoreResponse>(`${this.baseUrl}/api/v1/events/${eventId}/results/restore`, backup);
  }
}
