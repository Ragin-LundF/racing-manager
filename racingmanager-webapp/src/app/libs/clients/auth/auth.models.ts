export interface SetupRequest {
  username: string;
  password: string;
  displayName: string;
}

export interface SetupResponse {
  userId: string;
  username: string;
  displayName: string;
}

export interface SetupStatusResponse {
  firstRun: boolean;
  mode: 'LOCAL' | 'HOSTED';
}

export interface LoginRequest {
  username: string;
  password: string;
  tenantSlug?: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tenantId: string;
  scopes: string[];
  userId: string;
  username: string;
  displayName: string;
  role: string;
}

export interface RegisterRequest {
  tenantName: string;
  tenantSlug: string;
  username: string;
  password: string;
  displayName: string;
}

export interface RegisterResponse {
  tenantId: string;
  tenantSlug: string;
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  scopes: string[];
  userId: string;
  username: string;
  displayName: string;
  role: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface RefreshResponse {
  accessToken: string;
  expiresIn: number;
}

export interface SessionResponse {
  userId: string;
  username: string;
  displayName: string;
  role: string;
}

export interface ErrorResponse {
  code: string;
  message: string;
}
