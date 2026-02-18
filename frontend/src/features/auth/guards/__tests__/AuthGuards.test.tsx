import { describe, expect, it, vi, afterEach } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import { RequireAuth } from "@/features/auth/guards/RequireAuth";
import { RequireRole } from "@/features/auth/guards/RequireRole";
import { PublicOnly } from "@/features/auth/guards/PublicOnly";

const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

function renderGuard(path: string, element: React.ReactElement) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<p>login-page</p>} />
        <Route path="*" element={element} />
      </Routes>
    </MemoryRouter>
  );
}

afterEach(() => {
  mockUseAuth.mockReset();
});

describe("Auth guards", () => {
  it("RequireAuth redirects unauthenticated users to login", () => {
    mockUseAuth.mockReturnValue({
      status: "unauthenticated",
      isAuthenticated: false,
    });

    renderGuard(
      "/protected",
      <RequireAuth>
        <p>protected-content</p>
      </RequireAuth>
    );

    expect(screen.queryByText("protected-content")).not.toBeInTheDocument();
    expect(screen.getByText("login-page")).toBeInTheDocument();
  });

  it("RequireRole blocks non-admin users", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasRole: () => false,
    });

    renderGuard(
      "/admin",
      <RequireRole role="ROLE_ADMIN">
        <p>admin-content</p>
      </RequireRole>
    );

    expect(screen.queryByText("admin-content")).not.toBeInTheDocument();
    expect(screen.getByText("login-page")).toBeInTheDocument();
  });

  it("PublicOnly redirects authenticated users away from login", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      isAuthenticated: true,
    });

    renderGuard(
      "/login",
      <PublicOnly>
        <p>public-content</p>
      </PublicOnly>
    );

    expect(screen.queryByText("public-content")).not.toBeInTheDocument();
  });
});
