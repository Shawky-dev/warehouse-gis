import { describe, expect, it, vi } from "vitest";
import {
  configureAuthSessionManager,
  refreshAccessTokenOnce,
  resetAuthSessionManager,
} from "@/features/auth/session/authSessionManager";
import type { AuthResponse, AuthScope } from "@/features/auth/shared/types";

function createSession(token: string): AuthResponse {
  return {
    accessToken: token,
    tokenType: "Bearer",
    accessTokenExpiresAt: "2026-01-01T00:00:00Z",
    user: {
      id: "00000000-0000-0000-0000-000000000001",
      email: "admin@system.local",
      roles: ["ROLE_ADMIN"],
    },
  };
}

describe("authSessionManager", () => {
  it("deduplicates concurrent refresh calls", async () => {
    resetAuthSessionManager();

    const refreshSession = vi.fn<() => Promise<AuthResponse>>().mockResolvedValue(createSession("refreshed-token"));
    const scope: AuthScope = { kind: "landlord" };

    configureAuthSessionManager({
      getActiveScope: () => scope,
      getAccessTokenForScope: () => "stale-token",
      refreshSession: () => refreshSession(),
      onSessionUpdate: () => {},
      onUnauthorized: () => {},
    });

    const [first, second] = await Promise.all([refreshAccessTokenOnce(), refreshAccessTokenOnce()]);

    expect(first.accessToken).toBe("refreshed-token");
    expect(second.accessToken).toBe("refreshed-token");
    expect(refreshSession).toHaveBeenCalledTimes(1);
  });
});
