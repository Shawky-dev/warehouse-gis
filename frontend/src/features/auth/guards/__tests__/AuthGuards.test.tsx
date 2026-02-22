import { describe, expect, it, vi, afterEach } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import { RequireAuth } from "@/features/auth/guards/RequireAuth";
import { RequireRole } from "@/features/auth/guards/RequireRole";
import { PublicOnly } from "@/features/auth/guards/PublicOnly";
import { I18nProvider } from "@/i18n";

const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

function renderGuard(path: string, element: React.ReactElement) {
  return render(
    <I18nProvider initialLocale="en" storageKey="test-locale-auth-guards">
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/landlord/auth/login" element={<p>login-page</p>} />
          <Route path="/:tenantSlug/auth/login" element={<p>tenant-login-page</p>} />
          <Route path="/landlord" element={<p>landlord-home</p>} />
          <Route path="*" element={element} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>
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
      "/acme/products",
      <RequireAuth>
        <p>protected-content</p>
      </RequireAuth>
    );

    expect(screen.queryByText("protected-content")).not.toBeInTheDocument();
    expect(screen.getByText("tenant-login-page")).toBeInTheDocument();
  });

  it("RequireRole blocks non-admin users", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasRole: () => false,
    });

    renderGuard(
      "/landlord/roles",
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
      "/landlord/auth/public",
      <PublicOnly>
        <p>public-content</p>
      </PublicOnly>
    );

    expect(screen.queryByText("public-content")).not.toBeInTheDocument();
    expect(screen.getByText("landlord-home")).toBeInTheDocument();
  });
});
