export type AuthStatus = "idle" | "loading" | "authenticated" | "unauthenticated";

export interface AuthUser {
  id: string;
  email: string;
  roles: string[];
  permissions: string[];
}

export interface AuthResponse {
  accessToken: string;
  tokenType: "Bearer";
  accessTokenExpiresAt: string;
  user: AuthUser;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthState {
  accessToken: string | null;
  user: AuthUser | null;
  status: AuthStatus;
}

export type AuthScope =
  | { kind: "landlord" }
  | {
      kind: "tenant";
      slug: string;
    };
