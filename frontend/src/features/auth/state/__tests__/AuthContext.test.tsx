import { useEffect } from "react";
import { waitFor, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AuthProvider, useAuth } from "@/features/auth/state/AuthContext";
import { renderWithRouter } from "@/test/utils/renderWithRouter";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";

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

describe("AuthContext", () => {
  it("bootstraps session successfully via /landlord/auth/refresh", async () => {
    renderWithRouter(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>
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
      </AuthProvider>
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
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByTestId("status")).toHaveTextContent("authenticated");
      expect(screen.getByTestId("user-email")).toHaveTextContent("admin@system.local");
    });
  });
});
