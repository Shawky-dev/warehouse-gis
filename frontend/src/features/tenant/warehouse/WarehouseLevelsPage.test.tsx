import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import WarehouseLevelsPage from "@/features/tenant/warehouse/WarehouseLevelsPage";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

describe("WarehouseLevelsPage", () => {
  it("renders list-only level actions", async () => {
    const permissions: string[] = [TENANT_PERMISSIONS.WAREHOUSE_VIEW, TENANT_PERMISSIONS.WAREHOUSE_EDIT];
    mockUseAuth.mockReturnValue({
      hasPermission: (permission: string) => permissions.includes(permission),
    });

    server.use(
      http.get(`${API_URL}/acme/bays/bay-1/levels`, () =>
        HttpResponse.json({
          content: [
            {
              id: "level-1",
              bayId: "bay-1",
              bayCode: "B01",
              levelNum: 2,
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
      <I18nProvider initialLocale="en" storageKey="test-warehouse-levels">
        <MemoryRouter initialEntries={["/acme/bays/bay-1/levels"]}>
          <Routes>
            <Route path="/:tenantSlug/bays/:bayId/levels" element={<WarehouseLevelsPage />} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("L2")).toBeInTheDocument();
      expect(screen.getByText("Enter")).toBeInTheDocument();
    });

    expect(screen.queryByText("Edit")).not.toBeInTheDocument();
  });
});
