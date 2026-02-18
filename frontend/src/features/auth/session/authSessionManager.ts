import type { AuthResponse } from "@/features/auth/types";

interface SessionManagerConfig {
  refreshSession: () => Promise<AuthResponse>;
  onSessionUpdate: (session: AuthResponse) => void;
  onUnauthorized: () => void;
}

let accessToken: string | null = null;
let config: SessionManagerConfig | null = null;
let inFlightRefresh: Promise<AuthResponse> | null = null;

export function configureAuthSessionManager(nextConfig: SessionManagerConfig) {
  config = nextConfig;
}

export function clearAuthSessionManagerConfig() {
  config = null;
  inFlightRefresh = null;
}

export function getAccessToken() {
  return accessToken;
}

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export async function refreshAccessTokenOnce(): Promise<AuthResponse> {
  if (!config) {
    throw new Error("Auth session manager is not configured");
  }

  if (!inFlightRefresh) {
    inFlightRefresh = config
      .refreshSession()
      .then((session) => {
        config?.onSessionUpdate(session);
        return session;
      })
      .catch((error) => {
        config?.onUnauthorized();
        throw error;
      })
      .finally(() => {
        inFlightRefresh = null;
      });
  }

  return inFlightRefresh;
}
