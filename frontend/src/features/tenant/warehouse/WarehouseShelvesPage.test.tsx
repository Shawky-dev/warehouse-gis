import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import WarehouseShelvesPage from "@/features/tenant/warehouse/WarehouseShelvesPage";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

describe("WarehouseShelvesPage", () => {
  it("renders location codes", async () => {
    mockUseAuth.mockReturnValue({
      hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.WAREHOUSE_VIEW,
    });

    server.use(
      http.get(`${API_URL}/acme/bay-levels/level-1/shelves`, () =>
        HttpResponse.json({
          content: [
            {
              id: "shelf-1",
              levelId: "level-1",
              shelfNum: 3,
              locationCode: "WH1-A03-L-B01-L2-S3",
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
      <I18nProvider initialLocale="en" storageKey="test-warehouse-shelves">
        <MemoryRouter initialEntries={["/acme/bay-levels/level-1/shelves"]}>
          <Routes>
            <Route path="/:tenantSlug/bay-levels/:levelId/shelves" element={<WarehouseShelvesPage />} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("WH1-A03-L-B01-L2-S3")).toBeInTheDocument();
    });
  });
});
