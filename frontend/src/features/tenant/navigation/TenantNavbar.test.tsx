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

function renderNavbar() {
  mockUseAuth.mockReturnValue({
    hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.WAREHOUSE_VIEW,
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
  it("renders only the layouts entry under the warehouse layouts group", async () => {
    const user = userEvent.setup();
    renderNavbar();

    await user.click(screen.getByRole("button", { name: /Warehouse Layouts/i }));

    await waitFor(() => {
      expect(screen.getByRole("link", { name: "Layouts" })).toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "Aisle" })).not.toBeInTheDocument();
      expect(screen.queryByRole("link", { name: "Side" })).not.toBeInTheDocument();
    });
  });
});
