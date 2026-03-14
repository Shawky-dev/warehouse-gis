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

vi.mock("@/features/tenant/uom/TenantUomsPage", () => ({
  default: () => <p>tenant-uoms-page</p>,
}));

vi.mock("@/features/tenant/supplier/TenantSuppliersPage", () => ({
  default: () => <p>tenant-suppliers-page</p>,
}));

vi.mock("@/features/tenant/products/TenantProductsPage", () => ({
  default: () => <p>tenant-products-page</p>,
}));

vi.mock("@/features/tenant/audit/TenantAuditLogsPage", () => ({
  default: () => <p>tenant-audit-page</p>,
}));

vi.mock("@/features/tenant/warehouse/WarehouseLayoutsPage", () => ({
  default: () => <p>tenant-warehouse-layouts-page</p>,
}));

vi.mock("@/features/tenant/inventory/InventoryPage", () => ({
  default: () => <p>tenant-inventory-page</p>,
}));

vi.mock("@/features/tenant/receipts/ReceiptsPage", () => ({
  default: () => <p>tenant-receipts-page</p>,
}));

vi.mock("@/features/tenant/dispatches/DispatchesPage", () => ({
  default: () => <p>tenant-dispatches-page</p>,
}));

vi.mock("@/features/tenant/counting/CountSessionsPage", () => ({
  default: () => <p>tenant-count-sessions-page</p>,
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

  it("blocks uoms route when tenant.uoms.view permission is missing", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: () => false,
    });

    renderTenantRoute("/acme/uoms");

    expect(screen.queryByText("tenant-uoms-page")).not.toBeInTheDocument();
    expect(screen.getByText("Access denied")).toBeInTheDocument();
  });

  it("renders uoms route when tenant.uoms.view permission is present", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.UOMS_VIEW,
    });

    renderTenantRoute("/acme/uoms");

    expect(screen.getByText("tenant-uoms-page")).toBeInTheDocument();
  });

  it("blocks suppliers route when tenant.suppliers.view permission is missing", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: () => false,
    });

    renderTenantRoute("/acme/suppliers");

    expect(screen.queryByText("tenant-suppliers-page")).not.toBeInTheDocument();
    expect(screen.getByText("Access denied")).toBeInTheDocument();
  });

  it("renders suppliers route when tenant.suppliers.view permission is present", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.SUPPLIERS_VIEW,
    });

    renderTenantRoute("/acme/suppliers");

    expect(screen.getByText("tenant-suppliers-page")).toBeInTheDocument();
  });

  it("blocks products route when tenant.products.view permission is missing", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: () => false,
    });

    renderTenantRoute("/acme/products");

    expect(screen.queryByText("tenant-products-page")).not.toBeInTheDocument();
    expect(screen.getByText("Access denied")).toBeInTheDocument();
  });

  it("renders products route when tenant.products.view permission is present", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.PRODUCTS_VIEW,
    });

    renderTenantRoute("/acme/products");

    expect(screen.getByText("tenant-products-page")).toBeInTheDocument();
  });

  it("blocks warehouse-layouts route when tenant.warehouse.view permission is missing", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: () => false,
    });

    renderTenantRoute("/acme/warehouse-layouts");

    expect(screen.queryByText("tenant-warehouse-layouts-page")).not.toBeInTheDocument();
    expect(screen.getByText("Access denied")).toBeInTheDocument();
  });

  it("renders warehouse-layouts route when tenant.warehouse.view permission is present", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.WAREHOUSE_VIEW,
    });

    renderTenantRoute("/acme/warehouse-layouts");

    expect(screen.getByText("tenant-warehouse-layouts-page")).toBeInTheDocument();
  });

  it("blocks warehouse templates route when tenant.warehouse.view permission is missing", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: () => false,
    });

    renderTenantRoute("/acme/warehouse-layouts/templates");

    expect(screen.queryByText("tenant-warehouse-layouts-page")).not.toBeInTheDocument();
    expect(screen.getByText("Access denied")).toBeInTheDocument();
  });

  it("renders warehouse templates route when tenant.warehouse.view permission is present", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.WAREHOUSE_VIEW,
    });

    renderTenantRoute("/acme/warehouse-layouts/templates");

    expect(screen.getByText("tenant-warehouse-layouts-page")).toBeInTheDocument();
  });

  it("blocks audit-logs route when tenant.audit.view permission is missing", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: () => false,
    });

    renderTenantRoute("/acme/audit-logs");

    expect(screen.queryByText("tenant-audit-page")).not.toBeInTheDocument();
    expect(screen.getByText("Access denied")).toBeInTheDocument();
  });

  it("renders audit-logs route when tenant.audit.view permission is present", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.AUDIT_VIEW,
    });

    renderTenantRoute("/acme/audit-logs");

    expect(screen.getByText("tenant-audit-page")).toBeInTheDocument();
  });

  it("blocks inventory route when all inventory permissions are missing", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: () => false,
      hasAnyPermission: () => false,
    });

    renderTenantRoute("/acme/inventory");

    expect(screen.queryByText("tenant-inventory-page")).not.toBeInTheDocument();
    expect(screen.getByText("Access denied")).toBeInTheDocument();
  });

  it("renders inventory route when any inventory permission is present", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: () => false,
      hasAnyPermission: (permissions: string[]) =>
        permissions.includes(TENANT_PERMISSIONS.INVENTORY_TRANSFER),
    });

    renderTenantRoute("/acme/inventory");

    expect(screen.getByText("tenant-inventory-page")).toBeInTheDocument();
  });

  it("renders inventory operations subsection when any inventory permission is present", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: () => false,
      hasAnyPermission: (permissions: string[]) =>
        permissions.includes(TENANT_PERMISSIONS.INVENTORY_TRANSFER),
    });

    renderTenantRoute("/acme/inventory/operations");

    expect(screen.getByText("tenant-inventory-page")).toBeInTheDocument();
  });

  it("blocks receipts route when tenant.receipts.view permission is missing", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: () => false,
      hasAnyPermission: () => false,
    });

    renderTenantRoute("/acme/receipts");

    expect(screen.queryByText("tenant-receipts-page")).not.toBeInTheDocument();
    expect(screen.getByText("Access denied")).toBeInTheDocument();
  });

  it("renders receipts route when tenant.receipts.view permission is present", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.RECEIPTS_VIEW,
      hasAnyPermission: () => false,
    });

    renderTenantRoute("/acme/receipts");

    expect(screen.getByText("tenant-receipts-page")).toBeInTheDocument();
  });

  it("blocks dispatches route when tenant.dispatches.view permission is missing", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: () => false,
      hasAnyPermission: () => false,
    });

    renderTenantRoute("/acme/dispatches");

    expect(screen.queryByText("tenant-dispatches-page")).not.toBeInTheDocument();
    expect(screen.getByText("Access denied")).toBeInTheDocument();
  });

  it("renders dispatches route when tenant.dispatches.view permission is present", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.DISPATCHES_VIEW,
      hasAnyPermission: () => false,
    });

    renderTenantRoute("/acme/dispatches");

    expect(screen.getByText("tenant-dispatches-page")).toBeInTheDocument();
  });

  it("blocks count sessions route when tenant.counting.view permission is missing", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: () => false,
      hasAnyPermission: () => false,
    });

    renderTenantRoute("/acme/count-sessions");

    expect(screen.queryByText("tenant-count-sessions-page")).not.toBeInTheDocument();
    expect(screen.getByText("Access denied")).toBeInTheDocument();
  });

  it("renders count sessions route when tenant.counting.view permission is present", () => {
    mockUseAuth.mockReturnValue({
      status: "authenticated",
      hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.COUNTING_VIEW,
      hasAnyPermission: () => false,
    });

    renderTenantRoute("/acme/count-sessions");

    expect(screen.getByText("tenant-count-sessions-page")).toBeInTheDocument();
  });
});
