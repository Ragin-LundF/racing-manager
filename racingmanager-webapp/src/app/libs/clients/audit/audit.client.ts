import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../core/api.config';
import { AuditEntryResponse } from './audit.models';

@Injectable({ providedIn: 'root' })
export class AuditClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  findByEventId(eventId: string): Observable<AuditEntryResponse[]> {
    return this.http.get<AuditEntryResponse[]>(`${this.baseUrl}/api/v1/events/${eventId}/audit`);
  }

  query(params?: {
    action?: string;
    targetType?: string;
    targetId?: string;
    actorId?: string;
    limit?: number;
    offset?: number;
  }): Observable<AuditEntryResponse[]> {
    let httpParams = new HttpParams();
    if (params) {
      if (params.action) httpParams = httpParams.set('action', params.action);
      if (params.targetType) httpParams = httpParams.set('targetType', params.targetType);
      if (params.targetId) httpParams = httpParams.set('targetId', params.targetId);
      if (params.actorId) httpParams = httpParams.set('actorId', params.actorId);
      if (params.limit !== undefined) httpParams = httpParams.set('limit', params.limit);
      if (params.offset !== undefined) httpParams = httpParams.set('offset', params.offset);
    }
    return this.http.get<AuditEntryResponse[]>(`${this.baseUrl}/api/v1/audit`, { params: httpParams });
  }
}
