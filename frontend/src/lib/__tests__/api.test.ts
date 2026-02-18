import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { api } from "@/lib/api";
import {
  configureAuthSessionManager,
  setAccessToken,
  clearAuthSessionManagerConfig,
} from "@/features/auth/session/authSessionManager";
import type { AuthResponse } from "@/features/auth/types";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

describe("api auth integration", () => {
  beforeEach(() => {
    clearAuthSessionManagerConfig();
    setAccessToken(null);
  });

  afterEach(() => {
    clearAuthSessionManagerConfig();
    setAccessToken(null);
  });

  it("injects Authorization header when access token exists", async () => {
    setAccessToken("access-123");

    server.use(
      http.get(`${API_URL}/landlord/session`, ({ request }) => {
        return HttpResponse.json({ auth: request.headers.get("authorization") });
      })
    );

    const response = await api.get("/landlord/session");

    expect(response.status).toBe(200);
    expect(response.data.auth).toBe("Bearer access-123");
  });

  it("retries once after 401 using refreshed token", async () => {
    setAccessToken("old-token");

    const refreshSession = vi.fn<() => Promise<AuthResponse>>().mockResolvedValue({
      accessToken: "new-token",
      tokenType: "Bearer",
      accessTokenExpiresAt: "2026-01-01T00:00:00Z",
      user: {
        id: "00000000-0000-0000-0000-000000000001",
        email: "admin@system.local",
        roles: ["ROLE_ADMIN"],
      },
    });

    configureAuthSessionManager({
      refreshSession,
      onSessionUpdate: (session) => setAccessToken(session.accessToken),
      onUnauthorized: () => setAccessToken(null),
    });

    let callCount = 0;
    server.use(
      http.get(`${API_URL}/landlord/session`, ({ request }) => {
        callCount += 1;
        const authHeader = request.headers.get("authorization");

        if (callCount === 1 && authHeader === "Bearer old-token") {
          return HttpResponse.json({ code: "UNAUTHORIZED" }, { status: 401 });
        }

        if (authHeader === "Bearer new-token") {
          return HttpResponse.json({ ok: true }, { status: 200 });
        }

        return HttpResponse.json({ code: "UNAUTHORIZED" }, { status: 401 });
      })
    );

    const response = await api.get("/landlord/session");

    expect(response.status).toBe(200);
    expect(response.data.ok).toBe(true);
    expect(refreshSession).toHaveBeenCalledTimes(1);
  });
});
