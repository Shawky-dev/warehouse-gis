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
  it("shows only dashboard for users without any permissions", () => {
    mockUseAuth.mockReturnValue({
      hasPermission: () => false,
    });

    renderTenantNavbar();

    expect(screen.getByText("Dashboard")).toBeInTheDocument();
    expect(screen.queryByText("Products")).not.toBeInTheDocument();
    expect(screen.queryByText("Users")).not.toBeInTheDocument();
    expect(screen.queryByText("Roles")).not.toBeInTheDocument();
    expect(screen.queryByText("Units of Measure")).not.toBeInTheDocument();
    expect(screen.queryByText("Suppliers")).not.toBeInTheDocument();
    expect(screen.queryByText("Audit Logs")).not.toBeInTheDocument();
  });

  it("shows users and roles links when RBAC permissions are granted", () => {
    const permissions: string[] = [TENANT_PERMISSIONS.USERS_VIEW, TENANT_PERMISSIONS.ROLES_EDIT];

    mockUseAuth.mockReturnValue({
      hasPermission: (permission: string) => permissions.includes(permission),
    });

    renderTenantNavbar();

    expect(screen.getByText("Users")).toBeInTheDocument();
    expect(screen.getByText("Roles")).toBeInTheDocument();
  });

  it("shows UOMs, Suppliers, Products links when F0 permissions are granted", () => {
    const permissions: string[] = [
      TENANT_PERMISSIONS.PRODUCTS_VIEW,
      TENANT_PERMISSIONS.UOMS_VIEW,
      TENANT_PERMISSIONS.SUPPLIERS_VIEW,
    ];

    mockUseAuth.mockReturnValue({
      hasPermission: (permission: string) => permissions.includes(permission),
    });

    renderTenantNavbar();

    expect(screen.getByText("Products")).toBeInTheDocument();
    expect(screen.getByText("Units of Measure")).toBeInTheDocument();
    expect(screen.getByText("Suppliers")).toBeInTheDocument();
  });

  it("shows Audit Logs link when audit.view permission is granted", () => {
    mockUseAuth.mockReturnValue({
      hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.AUDIT_VIEW,
    });

    renderTenantNavbar();

    expect(screen.getByText("Audit Logs")).toBeInTheDocument();
  });

  it("hides Audit Logs link when audit.view permission is missing", () => {
    mockUseAuth.mockReturnValue({
      hasPermission: () => false,
    });

    renderTenantNavbar();

    expect(screen.queryByText("Audit Logs")).not.toBeInTheDocument();
  });
});
