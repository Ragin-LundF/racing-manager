import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../core/api.config';
import { SetupResponse, SetupStatusResponse } from '../auth/auth.models';
import { DeleteTenantRequest, SupervisorSetupRequest, TenantResponse, UpdateTenantRequest } from './admin.models';

@Injectable({ providedIn: 'root' })
export class AdminClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getSetupStatus(): Observable<SetupStatusResponse> {
    return this.http.get<SetupStatusResponse>(`${this.baseUrl}/api/v1/admin/setup-status`);
  }

  setup(request: SupervisorSetupRequest): Observable<SetupResponse> {
    return this.http.post<SetupResponse>(`${this.baseUrl}/api/v1/admin/setup`, request);
  }

  listTenants(): Observable<TenantResponse[]> {
    return this.http.get<TenantResponse[]>(`${this.baseUrl}/api/v1/admin/tenants`);
  }

  updateTenant(id: string, request: UpdateTenantRequest): Observable<TenantResponse> {
    return this.http.put<TenantResponse>(`${this.baseUrl}/api/v1/admin/tenants/${id}`, request);
  }

  deactivateTenant(id: string): Observable<TenantResponse> {
    return this.http.post<TenantResponse>(`${this.baseUrl}/api/v1/admin/tenants/${id}/deactivate`, {});
  }

  deleteTenant(id: string, request: DeleteTenantRequest): Observable<TenantResponse> {
    return this.http.delete<TenantResponse>(`${this.baseUrl}/api/v1/admin/tenants/${id}`, { body: request });
  }
}
