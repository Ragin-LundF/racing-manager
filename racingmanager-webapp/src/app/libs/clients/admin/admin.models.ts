import { SetupRequest, SetupResponse, SetupStatusResponse } from '../auth/auth.models';
import { TenantResponse, UpdateTenantRequest } from '../tenant/tenant.models';

export type SupervisorSetupRequest = SetupRequest;
export type SupervisorSetupResponse = SetupResponse;
export type SupervisorSetupStatusResponse = SetupStatusResponse;

export type { TenantResponse, UpdateTenantRequest };

export interface DeleteTenantRequest {
  confirmSlug: string;
}
