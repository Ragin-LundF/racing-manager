export interface TenantResponse {
  id: string;
  slug: string | null;
  displayName: string;
  status: string;
  settings: string | null;
}

export interface UpdateTenantRequest {
  displayName: string;
  settings?: string;
}

export interface TenantUserResponse {
  userId: string;
  username: string;
  displayName: string;
  role: string;
  status: string;
}

export interface CreateTenantUserRequest {
  username: string;
  password: string;
  displayName: string;
  role?: string;
}

export interface UpdateTenantUserRequest {
  role?: string;
  status?: string;
}
