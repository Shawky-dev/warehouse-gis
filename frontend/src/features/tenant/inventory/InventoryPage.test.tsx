import { describe, expect, it, vi, beforeEach } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { I18nProvider } from "@/i18n";
import InventoryPage from "@/features/tenant/inventory/InventoryPage";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";

const mockUseAuth = vi.fn();
const mockGetAllOnHand = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

vi.mock("@/features/tenant/api/inventoryApi", () => ({
  getAllOnHand: (...args: unknown[]) => mockGetAllOnHand(...args),
  getMovementsByLocation: vi.fn(),
  getMovementsByProduct: vi.fn(),
  receiveStock: vi.fn(),
  transferStock: vi.fn(),
  adjustStock: vi.fn(),
  extractInventoryErrorMessage: (_error: unknown, fallback: string) => fallback,
}));

function renderPage(permissions: string[]) {
  mockUseAuth.mockReturnValue({
    hasPermission: (permission: string) => permissions.includes(permission),
  });

  return render(
    <I18nProvider initialLocale="en" storageKey="test-locale-inventory-page">
      <MemoryRouter initialEntries={["/acme/inventory"]}>
        <Routes>
          <Route path="/:tenantSlug/inventory" element={<InventoryPage />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>
  );
}

describe("InventoryPage", () => {
  beforeEach(() => {
    mockUseAuth.mockReset();
    mockGetAllOnHand.mockReset();
    mockGetAllOnHand.mockResolvedValue([]);
  });

  it("hides the operations tab for view-only users", async () => {
    renderPage([TENANT_PERMISSIONS.INVENTORY_VIEW]);

    expect(screen.getByRole("button", { name: "On Hand" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Operations" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Movements" })).toBeInTheDocument();

    await waitFor(() => {
      expect(mockGetAllOnHand).toHaveBeenCalledWith("acme");
    });
  });

  it("defaults to the first permitted operation for operation-only users", () => {
    renderPage([TENANT_PERMISSIONS.INVENTORY_TRANSFER]);

    expect(screen.queryByRole("button", { name: "On Hand" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Operations" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Movements" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("From Location")).toBeInTheDocument();
    expect(screen.getByLabelText("To Location")).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Location$/)).not.toBeInTheDocument();
    expect(mockGetAllOnHand).not.toHaveBeenCalled();
  });
});
