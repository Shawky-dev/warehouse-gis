import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { I18nProvider } from "@/i18n";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { TenantNavbar } from "@/features/tenant/navigation/TenantNavbar";
const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

function renderNavbar(permissions: string[] = [TENANT_PERMISSIONS.WAREHOUSE_VIEW]) {
  mockUseAuth.mockReturnValue({
    hasPermission: (permission: string) => permissions.includes(permission),
    hasAnyPermission: (requested: string[]) => requested.some((permission) => permissions.includes(permission)),
  });

  return render(
    <I18nProvider initialLocale="en" storageKey="test-tenant-navbar-warehouse">
      <MemoryRouter initialEntries={["/acme/warehouse-layouts?layoutId=layout-active&path=block-aisle,block-side&tab=builder&mode=active"]}>
        <Routes>
          <Route path="/:tenantSlug/*" element={<TenantNavbar />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>
  );
}

describe("TenantNavbar", () => {
  it("renders layouts and templates entries under the warehouse layouts group", async () => {
    const user = userEvent.setup();
    renderNavbar();

    await user.click(screen.getByRole("button", { name: /Warehouse Layouts/i }));

    await waitFor(() => {
      expect(screen.getByRole("link", { name: "Layouts" })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: "Templates" })).toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "Aisle" })).not.toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "Side" })).not.toBeInTheDocument();
    });
  });

  it("shows the inventory entry when the user has any inventory permission", () => {
    renderNavbar([TENANT_PERMISSIONS.INVENTORY_TRANSFER]);

    expect(screen.getByRole("button", { name: /Inventory/i })).toBeInTheDocument();
  });

  it("shows Heatmaps nav item when user has GIS_HEATMAPS_MANAGE permission", async () => {
    const user = userEvent.setup();
    renderNavbar([TENANT_PERMISSIONS.GIS_FLOOR_PLAN_VIEW, TENANT_PERMISSIONS.GIS_HEATMAPS_MANAGE]);

    await user.click(screen.getByRole("button", { name: /GIS/i }));

    await waitFor(() => {
      expect(screen.getByRole("link", { name: "Heatmaps" })).toBeInTheDocument();
    });
  });

  it("hides Heatmaps nav item when user lacks GIS_HEATMAPS_MANAGE permission", async () => {
    const user = userEvent.setup();
    renderNavbar([TENANT_PERMISSIONS.GIS_FLOOR_PLAN_VIEW]);

    await user.click(screen.getByRole("button", { name: /GIS/i }));

    await waitFor(() => {
      expect(screen.queryByRole("link", { name: "Heatmaps" })).not.toBeInTheDocument();
    });
  });
});
