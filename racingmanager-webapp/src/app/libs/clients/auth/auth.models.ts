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
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  sessionId: string;
  userId: string;
  username: string;
  displayName: string;
  role: string;
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
