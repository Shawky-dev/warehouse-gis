import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import { I18nProvider } from "@/i18n";
import { tenantRoutes } from "@/features/tenant/routes";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";

const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

vi.mock("@/features/tenant/user/TenantUsersPage", () => ({
  default: () => <p>tenant-users-page</p>,
}));

vi.mock("@/features/tenant/roles/TenantRolesPage", () => ({
  default: () => <p>tenant-roles-page</p>,
}));

function renderTenantRoute(path: string) {
  return render(
    <I18nProvider initialLocale="en" storageKey="test-locale-tenant-routes">
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          {tenantRoutes.map((route) => (
            <Route key={route.path} path={route.path} element={route.element} />
          ))}
        </Routes>
      </MemoryRouter>
    </I18nProvider>
  );
}

afterEach(() => {
  mockUseAuth.mockReset();
});

describe("tenant routes RBAC", () => {
  it("blocks users route when tenant.users.view permission is missing", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: () => false,
    });

    renderTenantRoute("/acme/users");

    expect(screen.queryByText("tenant-users-page")).not.toBeInTheDocument();
    expect(screen.getByText("Access denied")).toBeInTheDocument();
  });

  it("renders users route when tenant.users.view permission is present", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.USERS_VIEW,
    });

    renderTenantRoute("/acme/users");

    expect(screen.getByText("tenant-users-page")).toBeInTheDocument();
  });

  it("blocks roles route when tenant.roles.edit permission is missing", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: () => false,
    });

    renderTenantRoute("/acme/roles");

    expect(screen.queryByText("tenant-roles-page")).not.toBeInTheDocument();
    expect(screen.getByText("Access denied")).toBeInTheDocument();
  });

  it("renders roles route when tenant.roles.edit permission is present", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.ROLES_EDIT,
    });

    renderTenantRoute("/acme/roles");

    expect(screen.getByText("tenant-roles-page")).toBeInTheDocument();
  });
});
