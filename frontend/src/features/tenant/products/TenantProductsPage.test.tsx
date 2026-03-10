import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import TenantProductsPage from "@/features/tenant/products/TenantProductsPage";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

const productPage = {
  content: [
    {
      id: "p-1",
      sku: "PROD-001",
      name: "Widget",
      description: null,
      baseUomId: "uom-1",
      baseUomCode: "KG",
      baseUomName: "Kilogram",
      trackLot: true,
      trackExpiry: false,
      active: true,
      suppliers: [{ supplierId: "s-1", supplierCode: "ACME", supplierName: "Acme Corp", primary: true }],
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
      deactivatedAt: null,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

function renderPage(permissions: string[] = []) {
  mockUseAuth.mockReturnValue({
    hasPermission: (p: string) => permissions.includes(p),
  });

  server.use(
    http.get(`${API_URL}/acme/products`, () => HttpResponse.json(productPage)),
    http.get(`${API_URL}/acme/uoms`, () => HttpResponse.json(emptyPage)),
    http.get(`${API_URL}/acme/suppliers`, () => HttpResponse.json(emptyPage))
  );

  return render(
    <I18nProvider initialLocale="en" storageKey="test-products">
      <MemoryRouter initialEntries={["/acme/products"]}>
        <Routes>
          <Route path="/:tenantSlug/products" element={<TenantProductsPage />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>
  );
}

describe("TenantProductsPage", () => {
  it("renders product list after load", async () => {
    renderPage([TENANT_PERMISSIONS.PRODUCTS_VIEW]);

    await waitFor(() => {
      expect(screen.getByText("PROD-001")).toBeInTheDocument();
      expect(screen.getByText("Widget")).toBeInTheDocument();
    });
  });

  it("shows create button when user has PRODUCTS_CREATE permission", async () => {
    renderPage([TENANT_PERMISSIONS.PRODUCTS_VIEW, TENANT_PERMISSIONS.PRODUCTS_CREATE]);

    await waitFor(() => {
      expect(screen.getByText("Create product")).toBeInTheDocument();
    });
  });

  it("hides create button when user lacks PRODUCTS_CREATE permission", async () => {
    renderPage([TENANT_PERMISSIONS.PRODUCTS_VIEW]);

    await waitFor(() => {
      expect(screen.getByText("PROD-001")).toBeInTheDocument();
    });

    expect(screen.queryByText("Create product")).not.toBeInTheDocument();
  });

  it("shows hard delete button when user has PRODUCTS_HARD_DELETE permission", async () => {
    renderPage([TENANT_PERMISSIONS.PRODUCTS_VIEW, TENANT_PERMISSIONS.PRODUCTS_HARD_DELETE]);

    await waitFor(() => {
      expect(screen.getByText("Hard delete")).toBeInTheDocument();
    });
  });

  it("shows base UOM code in list", async () => {
    renderPage([TENANT_PERMISSIONS.PRODUCTS_VIEW]);

    await waitFor(() => {
      expect(screen.getByText("KG")).toBeInTheDocument();
    });
  });
});
