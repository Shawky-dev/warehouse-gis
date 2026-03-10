import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import WarehouseAislesPage from "@/features/tenant/warehouse/WarehouseAislesPage";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

function renderPage(permissions: string[] = []) {
  mockUseAuth.mockReturnValue({
    hasPermission: (permission: string) => permissions.includes(permission),
  });

  server.use(
    http.get(`${API_URL}/acme/warehouse-layouts/layout-1`, () =>
      HttpResponse.json({
        id: "layout-1",
        code: "WH1",
        name: "Main Warehouse",
        description: null,
        active: true,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
        deactivatedAt: null,
      })
    ),
    http.get(`${API_URL}/acme/warehouse-layouts/layout-1/aisles`, () =>
      HttpResponse.json({
        content: [
          {
            id: "aisle-1",
            layoutId: "layout-1",
            layoutCode: "WH1",
            code: "A03",
            name: "Cold lane",
            active: true,
            createdAt: "2026-01-01T00:00:00Z",
            updatedAt: "2026-01-01T00:00:00Z",
            deactivatedAt: null,
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      })
    )
  );

  return render(
    <I18nProvider initialLocale="en" storageKey="test-warehouse-aisles">
      <MemoryRouter initialEntries={["/acme/warehouse-layouts/layout-1/aisles"]}>
        <Routes>
          <Route path="/:tenantSlug/warehouse-layouts/:layoutId/aisles" element={<WarehouseAislesPage />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>
  );
}

describe("WarehouseAislesPage", () => {
  it("renders breadcrumb context from the parent layout", async () => {
    renderPage([TENANT_PERMISSIONS.WAREHOUSE_VIEW]);

    await waitFor(() => {
      expect(screen.getByText("WH1")).toBeInTheDocument();
      expect(screen.getByText("A03")).toBeInTheDocument();
      expect(screen.getByText("Layouts")).toBeInTheDocument();
    });
  });
});
