import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../core/api.config';
import {
  CreateTenantUserRequest,
  TenantResponse,
  TenantUserResponse,
  UpdateTenantRequest,
  UpdateTenantUserRequest,
} from './tenant.models';

@Injectable({ providedIn: 'root' })
export class TenantClient {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  getTenant(): Observable<TenantResponse> {
    return this.http.get<TenantResponse>(`${this.baseUrl}/api/v1/tenant`);
  }

  updateTenant(request: UpdateTenantRequest): Observable<TenantResponse> {
    return this.http.put<TenantResponse>(`${this.baseUrl}/api/v1/tenant`, request);
  }

  listUsers(): Observable<TenantUserResponse[]> {
    return this.http.get<TenantUserResponse[]>(`${this.baseUrl}/api/v1/tenant/users`);
  }

  createUser(request: CreateTenantUserRequest): Observable<TenantUserResponse> {
    return this.http.post<TenantUserResponse>(`${this.baseUrl}/api/v1/tenant/users`, request);
  }

  updateUser(userId: string, request: UpdateTenantUserRequest): Observable<TenantUserResponse> {
    return this.http.put<TenantUserResponse>(`${this.baseUrl}/api/v1/tenant/users/${userId}`, request);
  }

  deactivateUser(userId: string): Observable<TenantUserResponse> {
    return this.http.delete<TenantUserResponse>(`${this.baseUrl}/api/v1/tenant/users/${userId}`);
  }
}
