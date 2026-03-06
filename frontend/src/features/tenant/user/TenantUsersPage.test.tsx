import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import TenantUsersPage from "@/features/tenant/user/TenantUsersPage";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

function mockAuth(permissions: string[], roles: string[] = ["ROLE_ADMIN"]) {
  mockUseAuth.mockReturnValue({
    user: {
      email: "admin@acme.local",
      permissions,
      roles,
    },
    hasPermission: (permission: string) => permissions.includes(permission),
    hasRole: (role: string) => roles.includes(role),
  });
}

function renderTenantUsersPage(path = "/acme/users") {
  return render(
    <I18nProvider initialLocale="en" storageKey="test-locale-tenant-users-page">
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/:tenantSlug/users" element={<TenantUsersPage />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>
  );
}

describe("TenantUsersPage", () => {
  beforeEach(() => {
    mockUseAuth.mockReset();
  });

  it("renders tenant users from tenant endpoint", async () => {
    mockAuth([TENANT_PERMISSIONS.USERS_VIEW]);

    server.use(
      http.get(`${API_URL}/acme/users`, () =>
        HttpResponse.json({
          content: [
            {
              id: "00000000-0000-0000-0000-000000000010",
              email: "manager@acme.local",
              role: "MANAGER",
              active: true,
              createdAt: "2026-01-01T00:00:00Z",
              updatedAt: "2026-01-01T00:00:00Z",
              deactivatedAt: null,
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        })
      )
    );

    renderTenantUsersPage();

    expect(await screen.findByText("manager@acme.local")).toBeInTheDocument();
    expect(screen.getByText("MANAGER")).toBeInTheDocument();
  });

  it("hides create action without tenant.users.create permission", async () => {
    mockAuth([TENANT_PERMISSIONS.USERS_VIEW]);

    server.use(
      http.get(`${API_URL}/acme/users`, () =>
        HttpResponse.json({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        })
      )
    );

    renderTenantUsersPage();

    expect(await screen.findByText("No users found.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create user" })).not.toBeInTheDocument();
  });
});
