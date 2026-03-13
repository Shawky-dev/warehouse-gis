import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { I18nProvider } from "@/i18n";
import InventoryPage from "@/features/tenant/inventory/InventoryPage";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";

const mockUseAuth = vi.fn();
const mockGetOnHand = vi.fn();
const mockGetMovements = vi.fn();
const mockGetProductLookups = vi.fn();
const mockGetLocationLookups = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

vi.mock("@/features/tenant/api/inventoryApi", () => ({
  getOnHand: (...args: unknown[]) => mockGetOnHand(...args),
  getMovements: (...args: unknown[]) => mockGetMovements(...args),
  getProductLookups: (...args: unknown[]) => mockGetProductLookups(...args),
  getLocationLookups: (...args: unknown[]) => mockGetLocationLookups(...args),
  receiveStock: vi.fn(),
  transferStock: vi.fn(),
  adjustStock: vi.fn(),
  extractInventoryErrorMessage: (_error: unknown, fallback: string) => fallback,
}));

function renderPage(permissions: string[], locale: "en" | "ar" = "en") {
  mockUseAuth.mockReturnValue({
    hasPermission: (permission: string) => permissions.includes(permission),
  });

  return render(
    <I18nProvider initialLocale={locale} storageKey={`test-locale-inventory-page-${locale}`}>
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
    mockGetOnHand.mockReset();
    mockGetMovements.mockReset();
    mockGetProductLookups.mockReset();
    mockGetLocationLookups.mockReset();

    mockGetOnHand.mockResolvedValue([]);
    mockGetMovements.mockResolvedValue({
      content: [],
      page: 0,
      size: 25,
      totalElements: 0,
      totalPages: 0,
    });
    mockGetProductLookups.mockResolvedValue({
      content: [
        {
          id: "product-1",
          sku: "SKU-1",
          name: "Sample Product",
          baseUomCode: "EA",
          trackLot: true,
          trackExpiry: true,
          active: true,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    mockGetLocationLookups.mockResolvedValue({
      content: [
        {
          id: "location-1",
          layoutId: "layout-1",
          layoutName: "Main Layout",
          label: "Shelf · 1",
          pathLabel: "Aisle · 1 / Shelf · 1",
          identifier: "1",
          side: null,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
  });

  it("hides the operations tab for view-only users and loads on-hand rows", async () => {
    renderPage([TENANT_PERMISSIONS.INVENTORY_VIEW]);

    expect(screen.getByRole("button", { name: "On Hand" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Operations" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Movements" })).toBeInTheDocument();

    await waitFor(() => {
      expect(mockGetOnHand).toHaveBeenCalledWith("acme", { locationId: undefined, productId: undefined });
    });
  });

  it("shows picker-based transfer flow for operation-only users", async () => {
    renderPage([TENANT_PERMISSIONS.INVENTORY_TRANSFER]);

    expect(screen.getByRole("button", { name: "Operations" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "On Hand" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("From Location")).toBeInTheDocument();
    expect(screen.getByLabelText("To Location")).toBeInTheDocument();
    expect(screen.getByLabelText("Product")).toBeInTheDocument();
    expect(screen.queryByDisplayValue("UUID")).not.toBeInTheDocument();

    await waitFor(() => {
      expect(mockGetProductLookups).toHaveBeenCalledWith("acme", { search: undefined });
      expect(mockGetLocationLookups).toHaveBeenCalledWith("acme", { search: undefined });
    });
  });

  it("supports RTL labels without breaking the inventory tabs", async () => {
    renderPage([TENANT_PERMISSIONS.INVENTORY_VIEW, TENANT_PERMISSIONS.INVENTORY_RECEIVE], "ar");

    expect(screen.getByText("المخزون")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "الرصيد الحالي" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "العمليات" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "العمليات" }));

    expect(await screen.findByText("عمليات المخزون")).toBeInTheDocument();
    expect(screen.getByLabelText("المنتج")).toBeInTheDocument();
  });
});
