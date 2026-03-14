import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { I18nProvider } from "@/i18n";
import InventoryPage from "@/features/tenant/inventory/InventoryPage";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";

const mockUseAuth = vi.fn();
const mockGetStock = vi.fn();
const mockGetMovements = vi.fn();
const mockGetProductLookups = vi.fn();
const mockGetLocationLookups = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

vi.mock("@/features/tenant/api/inventoryApi", () => ({
  getStock: (...args: unknown[]) => mockGetStock(...args),
  getMovements: (...args: unknown[]) => mockGetMovements(...args),
  getProductLookups: (...args: unknown[]) => mockGetProductLookups(...args),
  getLocationLookups: (...args: unknown[]) => mockGetLocationLookups(...args),
  receiveStock: vi.fn(),
  transferStock: vi.fn(),
  adjustStock: vi.fn(),
  extractInventoryErrorMessage: (_error: unknown, fallback: string) => fallback,
}));

function renderPage(
  permissions: string[],
  locale: "en" | "ar" = "en",
  path = "/acme/inventory/stock"
) {
  mockUseAuth.mockReturnValue({
    hasPermission: (permission: string) => permissions.includes(permission),
  });

  return render(
    <I18nProvider initialLocale={locale} storageKey={`test-locale-inventory-page-${locale}`}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/:tenantSlug/inventory/stock" element={<InventoryPage section="stock" />} />
          <Route path="/:tenantSlug/inventory/operations" element={<InventoryPage section="operations" />} />
          <Route path="/:tenantSlug/inventory/movements" element={<InventoryPage section="movements" />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>
  );
}

describe("InventoryPage", () => {
  beforeEach(() => {
    mockUseAuth.mockReset();
    mockGetStock.mockReset();
    mockGetMovements.mockReset();
    mockGetProductLookups.mockReset();
    mockGetLocationLookups.mockReset();

    mockGetStock.mockResolvedValue([]);
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

  it("renders stock section for view-only users and loads stock rows", async () => {
    renderPage([TENANT_PERMISSIONS.INVENTORY_VIEW]);

    expect(screen.getByText("Filters")).toBeInTheDocument();
    expect(screen.queryByText("Inventory Operations")).not.toBeInTheDocument();
    expect(screen.queryByText("Movements")).not.toBeInTheDocument();

    await waitFor(() => {
      expect(mockGetStock).toHaveBeenCalledWith("acme", { locationId: undefined, productId: undefined });
    });
  });

  it("shows picker-based transfer flow for operation-only users", async () => {
    renderPage([TENANT_PERMISSIONS.INVENTORY_TRANSFER], "en", "/acme/inventory/operations");

    expect(screen.getByText("Inventory Operations")).toBeInTheDocument();
    expect(screen.queryByText("Filters")).not.toBeInTheDocument();
    expect(screen.getByLabelText("From Location")).toBeInTheDocument();
    expect(screen.getByLabelText("To Location")).toBeInTheDocument();
    expect(screen.getByLabelText("Product")).toBeInTheDocument();
    expect(screen.queryByDisplayValue("UUID")).not.toBeInTheDocument();

    await waitFor(() => {
      expect(mockGetProductLookups).toHaveBeenCalledWith("acme", { search: undefined });
      expect(mockGetLocationLookups).toHaveBeenCalledWith("acme", { search: undefined });
    });
  });

  it("supports RTL labels on operations section", async () => {
    renderPage(
      [TENANT_PERMISSIONS.INVENTORY_VIEW, TENANT_PERMISSIONS.INVENTORY_RECEIVE],
      "ar",
      "/acme/inventory/operations"
    );

    expect(screen.getByText("المخزون")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "استلام" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "استلام" }));

    expect(await screen.findByText("عمليات المخزون")).toBeInTheDocument();
    expect(screen.getByLabelText("المنتج")).toBeInTheDocument();
  });
});
