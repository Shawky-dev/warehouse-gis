import { describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import { I18nProvider } from "@/i18n";
import { TenantNavbar } from "@/features/tenant/navigation/TenantNavbar";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";

const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

function renderTenantNavbar(path = "/acme") {
  return render(
    <I18nProvider initialLocale="en" storageKey="test-locale-tenant-navbar">
      <MemoryRouter initialEntries={[path]}>
        <TenantNavbar />
      </MemoryRouter>
    </I18nProvider>
  );
}

describe("TenantNavbar RBAC", () => {
  it("shows dashboard and products for users without tenant RBAC permissions", () => {
    mockUseAuth.mockReturnValue({
      hasPermission: () => false,
    });

    renderTenantNavbar();

    expect(screen.getByText("Dashboard")).toBeInTheDocument();
    expect(screen.getByText("Products")).toBeInTheDocument();
    expect(screen.queryByText("Users")).not.toBeInTheDocument();
    expect(screen.queryByText("Roles")).not.toBeInTheDocument();
  });

  it("shows users and roles links when tenant permissions are granted", () => {
    const permissions: string[] = [TENANT_PERMISSIONS.USERS_VIEW, TENANT_PERMISSIONS.ROLES_EDIT];

    mockUseAuth.mockReturnValue({
      hasPermission: (permission: string) => permissions.includes(permission),
    });

    renderTenantNavbar();

    expect(screen.getByText("Users")).toBeInTheDocument();
    expect(screen.getByText("Roles")).toBeInTheDocument();
  });
});
