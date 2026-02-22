import { toScopeKey } from "@/features/auth/shared/scope";
import type { AuthResponse, AuthScope } from "@/features/auth/shared/types";

interface SessionManagerConfig {
  getActiveScope: () => AuthScope | null;
  getAccessTokenForScope: (scope: AuthScope) => string | null;
  refreshSession: (scope: AuthScope) => Promise<AuthResponse>;
  onSessionUpdate: (scope: AuthScope, session: AuthResponse) => void;
  onUnauthorized: (scope: AuthScope) => void;
}

let accessToken: string | null = null;
let config: SessionManagerConfig | null = null;
const inFlightRefreshByScope = new Map<string, Promise<AuthResponse>>();

export function configureAuthSessionManager(nextConfig: SessionManagerConfig) {
  config = nextConfig;
}

export function clearAuthSessionManagerConfig() {
  config = null;
  inFlightRefreshByScope.clear();
}

export function resetAuthSessionManager() {
  accessToken = null;
  config = null;
  inFlightRefreshByScope.clear();
}

export function getAccessToken() {
  if (config) {
    const activeScope = config.getActiveScope();
    if (activeScope) {
      return config.getAccessTokenForScope(activeScope);
    }
  }
  return accessToken;
}

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export async function refreshAccessTokenOnce(): Promise<AuthResponse> {
  if (!config) {
    throw new Error("Auth session manager is not configured");
  }

  const scope = config.getActiveScope();
  if (!scope) {
    throw new Error("No active auth scope");
  }

  const scopeKey = toScopeKey(scope);
  const inFlightRefresh = inFlightRefreshByScope.get(scopeKey);
  if (inFlightRefresh) {
    return inFlightRefresh;
  }

  const nextRefresh = config
      .refreshSession(scope)
      .then((session) => {
        config?.onSessionUpdate(scope, session);
        return session;
      })
      .catch((error) => {
        config?.onUnauthorized(scope);
        throw error;
      })
      .finally(() => {
        inFlightRefreshByScope.delete(scopeKey);
      });
  inFlightRefreshByScope.set(scopeKey, nextRefresh);

  return nextRefresh;
}
