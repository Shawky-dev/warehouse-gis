import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import WarehouseLayoutsPage from "@/features/tenant/warehouse/WarehouseLayoutsPage";

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
    http.get(`${API_URL}/acme/warehouse-layouts`, () =>
      HttpResponse.json({
        content: [
          {
            id: "layout-1",
            code: "WH1",
            name: "Main Warehouse",
            description: "Primary layout",
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
    <I18nProvider initialLocale="en" storageKey="test-warehouse-layouts">
      <MemoryRouter initialEntries={["/acme/warehouse-layouts"]}>
        <Routes>
          <Route path="/:tenantSlug/warehouse-layouts" element={<WarehouseLayoutsPage />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>
  );
}

describe("WarehouseLayoutsPage", () => {
  it("renders layouts list and create action", async () => {
    renderPage([TENANT_PERMISSIONS.WAREHOUSE_VIEW, TENANT_PERMISSIONS.WAREHOUSE_EDIT]);

    await waitFor(() => {
      expect(screen.getByText("WH1")).toBeInTheDocument();
      expect(screen.getByText("Create layout")).toBeInTheDocument();
    });
  });
});
