import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import WarehouseSidesPage from "@/features/tenant/warehouse/WarehouseSidesPage";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

describe("WarehouseSidesPage", () => {
  it("renders both side rows without edit actions", async () => {
    const permissions: string[] = [TENANT_PERMISSIONS.WAREHOUSE_VIEW, TENANT_PERMISSIONS.WAREHOUSE_EDIT];
    mockUseAuth.mockReturnValue({
      hasPermission: (permission: string) => permissions.includes(permission),
    });

    server.use(
      http.get(`${API_URL}/acme/aisles/aisle-1/sides`, () =>
        HttpResponse.json({
          content: [
            {
              id: "side-l",
              aisleId: "aisle-1",
              aisleCode: "A03",
              layoutCode: "WH1",
              side: "L",
              active: true,
              createdAt: "2026-01-01T00:00:00Z",
              updatedAt: "2026-01-01T00:00:00Z",
              deactivatedAt: null,
            },
            {
              id: "side-r",
              aisleId: "aisle-1",
              aisleCode: "A03",
              layoutCode: "WH1",
              side: "R",
              active: true,
              createdAt: "2026-01-01T00:00:00Z",
              updatedAt: "2026-01-01T00:00:00Z",
              deactivatedAt: null,
            },
          ],
        })
      )
    );

    render(
      <I18nProvider initialLocale="en" storageKey="test-warehouse-sides">
        <MemoryRouter initialEntries={["/acme/aisles/aisle-1/sides"]}>
          <Routes>
            <Route path="/:tenantSlug/aisles/:aisleId/sides" element={<WarehouseSidesPage />} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("L")).toBeInTheDocument();
      expect(screen.getByText("R")).toBeInTheDocument();
    });

    expect(screen.queryByText("Edit")).not.toBeInTheDocument();
  });
});
