import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import WarehouseBaysPage from "@/features/tenant/warehouse/WarehouseBaysPage";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

describe("WarehouseBaysPage", () => {
  it("shows generated location codes after bulk create", async () => {
    const permissions: string[] = [TENANT_PERMISSIONS.WAREHOUSE_VIEW, TENANT_PERMISSIONS.WAREHOUSE_EDIT];
    mockUseAuth.mockReturnValue({
      hasPermission: (permission: string) => permissions.includes(permission),
    });

    server.use(
      http.get(`${API_URL}/acme/sides/side-1/bays`, () =>
        HttpResponse.json({
          content: [
            {
              id: "bay-1",
              sideId: "side-1",
              side: "L",
              bayCode: "B01",
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
      ),
      http.post(`${API_URL}/acme/sides/side-1/bays/bulk`, () =>
        HttpResponse.json({
          locationCodes: ["WH1-A03-L-B02-L1-S1", "WH1-A03-L-B02-L1-S2"],
        })
      )
    );

    render(
      <I18nProvider initialLocale="en" storageKey="test-warehouse-bays">
        <MemoryRouter initialEntries={["/acme/sides/side-1/bays"]}>
          <Routes>
            <Route path="/:tenantSlug/sides/:sideId/bays" element={<WarehouseBaysPage />} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>
    );

    const user = userEvent.setup();

    await waitFor(() => {
      expect(screen.getByText("B01")).toBeInTheDocument();
    });

    await user.click(screen.getByText("Bulk create"));
    await user.type(screen.getByLabelText("Bay codes"), "B02");
    await user.clear(screen.getByLabelText("Levels per bay"));
    await user.type(screen.getByLabelText("Levels per bay"), "1");
    await user.clear(screen.getByLabelText("Shelves per level"));
    await user.type(screen.getByLabelText("Shelves per level"), "2");
    await user.click(screen.getAllByText("Bulk create")[1]);

    await waitFor(() => {
      expect(screen.getByText("WH1-A03-L-B02-L1-S1")).toBeInTheDocument();
    });
  });
});
