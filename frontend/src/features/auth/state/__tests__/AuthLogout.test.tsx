import { useEffect } from "react";
import { waitFor, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AuthProvider, useAuth } from "@/features/auth/state/AuthContext";
import { renderWithRouter } from "@/test/utils/renderWithRouter";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

function LogoutProbe() {
  const { status, user, logout } = useAuth();

  useEffect(() => {
    if (status === "authenticated") {
      void logout();
    }
  }, [logout, status]);

  return (
    <div>
      <p data-testid="status">{status}</p>
      <p data-testid="user-email">{user?.email ?? "none"}</p>
    </div>
  );
}

describe("Auth logout flow", () => {
  it("calls /landlord/auth/logout and clears local auth state", async () => {
    let logoutCalled = 0;

    server.use(
      http.post(`${API_URL}/landlord/auth/logout`, () => {
        logoutCalled += 1;
        return new HttpResponse(null, { status: 204 });
      })
    );

    renderWithRouter(
      <AuthProvider>
        <LogoutProbe />
      </AuthProvider>,
      ["/landlord"]
    );

    await waitFor(() => {
      expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated");
      expect(screen.getByTestId("user-email")).toHaveTextContent("none");
      expect(logoutCalled).toBe(1);
    });
  });
});
