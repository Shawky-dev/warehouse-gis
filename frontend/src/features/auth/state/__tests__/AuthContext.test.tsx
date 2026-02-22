import { useEffect } from "react";
import { waitFor, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AuthProvider, useAuth } from "@/features/auth/state/AuthContext";
import { renderWithRouter } from "@/test/utils/renderWithRouter";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { useNavigate } from "react-router-dom";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

function AuthProbe() {
  const { status, isAuthenticated, user } = useAuth();

  return (
    <div>
      <p data-testid="status">{status}</p>
      <p data-testid="is-authenticated">{String(isAuthenticated)}</p>
      <p data-testid="user-email">{user?.email ?? "none"}</p>
    </div>
  );
}

function LoginProbe() {
  const { login, status, user } = useAuth();

  useEffect(() => {
    void login({ email: "admin@system.local", password: "admin123" });
  }, [login]);

  return (
    <div>
      <p data-testid="status">{status}</p>
      <p data-testid="user-email">{user?.email ?? "none"}</p>
    </div>
  );
}

function ScopeIsolationProbe() {
  const navigate = useNavigate();
  const { login, logout, getScopeState, scope, status } = useAuth();

  const landlordState = getScopeState({ kind: "landlord" });
  const tenantState = getScopeState({ kind: "tenant", slug: "acme" });

  const handleLoginCurrentScope = () => {
    const email = scope?.kind === "tenant" ? "admin@acme.local" : "admin@system.local";
    void login({ email, password: "admin123" });
  };

  return (
    <div>
      <p data-testid="active-scope">
        {scope ? (scope.kind === "tenant" ? `tenant:${scope.slug}` : scope.kind) : "none"}
      </p>
      <p data-testid="active-status">{status}</p>
      <p data-testid="landlord-email">{landlordState.user?.email ?? "none"}</p>
      <p data-testid="tenant-email">{tenantState.user?.email ?? "none"}</p>

      <button onClick={handleLoginCurrentScope}>login-current</button>
      <button onClick={() => void logout()}>logout-current</button>
      <button onClick={() => navigate("/landlord")}>go-landlord</button>
      <button onClick={() => navigate("/acme")}>go-tenant</button>
    </div>
  );
}

describe("AuthContext", () => {
  it("bootstraps session successfully via /landlord/auth/refresh", async () => {
    renderWithRouter(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
      ["/landlord"]
    );

    await waitFor(() => {
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated");
      expect(screen.getByTestId("is-authenticated")).toHaveTextContent("true");
      expect(screen.getByTestId("user-email")).toHaveTextContent("admin@system.local");
    });
  });

  it("falls back to unauthenticated when refresh fails", async () => {
    server.use(
      http.post(`${API_URL}/landlord/auth/refresh`, () => {
        return HttpResponse.json({ code: "UNAUTHORIZED" }, { status: 401 });
      })
    );

    renderWithRouter(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
      ["/landlord"]
    );

    await waitFor(() => {
      expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated");
      expect(screen.getByTestId("is-authenticated")).toHaveTextContent("false");
      expect(screen.getByTestId("user-email")).toHaveTextContent("none");
    });
  });

  it("logs in and updates authenticated user state", async () => {
    server.use(
      http.post(`${API_URL}/landlord/auth/refresh`, () => {
        return HttpResponse.json({ code: "UNAUTHORIZED" }, { status: 401 });
      })
    );

    renderWithRouter(
      <AuthProvider>
        <LoginProbe />
      </AuthProvider>,
      ["/landlord"]
    );

    await waitFor(() => {
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated");
      expect(screen.getByTestId("user-email")).toHaveTextContent("admin@system.local");
    });
  });

  it("bootstraps tenant session via tenant slug refresh endpoint", async () => {
    server.use(
      http.post(`${API_URL}/acme/auth/refresh`, () => {
        return HttpResponse.json({
          accessToken: "tenant-access-token",
          tokenType: "Bearer",
          accessTokenExpiresAt: "2026-01-01T00:00:00Z",
          user: {
            id: "00000000-0000-0000-0000-000000000002",
            email: "admin@acme.local",
            roles: ["ROLE_ADMIN"],
          },
        });
      })
    );

    renderWithRouter(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
      ["/acme"]
    );

    await waitFor(() => {
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated");
      expect(screen.getByTestId("is-authenticated")).toHaveTextContent("true");
      expect(screen.getByTestId("user-email")).toHaveTextContent("admin@acme.local");
    });
  });

  it("keeps landlord and tenant sessions isolated by scope key", async () => {
    server.use(
      http.post(`${API_URL}/landlord/auth/refresh`, () => {
        return HttpResponse.json({ code: "UNAUTHORIZED" }, { status: 401 });
      }),
      http.post(`${API_URL}/acme/auth/refresh`, () => {
        return HttpResponse.json({ code: "UNAUTHORIZED" }, { status: 401 });
      }),
      http.post(`${API_URL}/landlord/auth/login`, () => {
        return HttpResponse.json({
          accessToken: "landlord-access-token",
          tokenType: "Bearer",
          accessTokenExpiresAt: "2026-01-01T00:00:00Z",
          user: {
            id: "00000000-0000-0000-0000-000000000001",
            email: "admin@system.local",
            roles: ["ROLE_ADMIN"],
          },
        });
      }),
      http.post(`${API_URL}/acme/auth/login`, () => {
        return HttpResponse.json({
          accessToken: "tenant-access-token",
          tokenType: "Bearer",
          accessTokenExpiresAt: "2026-01-01T00:00:00Z",
          user: {
            id: "00000000-0000-0000-0000-000000000002",
            email: "admin@acme.local",
            roles: ["ROLE_ADMIN"],
          },
        });
      }),
      http.post(`${API_URL}/acme/auth/logout`, () => {
        return new HttpResponse(null, { status: 204 });
      })
    );

    renderWithRouter(
      <AuthProvider>
        <ScopeIsolationProbe />
      </AuthProvider>,
      ["/landlord"]
    );

    await waitFor(() => {
      expect(screen.getByTestId("active-scope")).toHaveTextContent("landlord");
      expect(screen.getByTestId("active-status")).toHaveTextContent("unauthenticated");
      expect(screen.getByTestId("landlord-email")).toHaveTextContent("none");
      expect(screen.getByTestId("tenant-email")).toHaveTextContent("none");
    });

    fireEvent.click(screen.getByText("login-current"));

    await waitFor(() => {
      expect(screen.getByTestId("landlord-email")).toHaveTextContent("admin@system.local");
      expect(screen.getByTestId("tenant-email")).toHaveTextContent("none");
    });

    fireEvent.click(screen.getByText("go-tenant"));

    await waitFor(() => {
      expect(screen.getByTestId("active-scope")).toHaveTextContent("tenant:acme");
      expect(screen.getByTestId("active-status")).toHaveTextContent("unauthenticated");
    });

    fireEvent.click(screen.getByText("login-current"));

    await waitFor(() => {
      expect(screen.getByTestId("tenant-email")).toHaveTextContent("admin@acme.local");
      expect(screen.getByTestId("landlord-email")).toHaveTextContent("admin@system.local");
    });

    fireEvent.click(screen.getByText("logout-current"));

    await waitFor(() => {
      expect(screen.getByTestId("tenant-email")).toHaveTextContent("none");
      expect(screen.getByTestId("landlord-email")).toHaveTextContent("admin@system.local");
    });
  });
});
