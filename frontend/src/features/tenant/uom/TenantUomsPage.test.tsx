import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import TenantUomsPage from "@/features/tenant/uom/TenantUomsPage";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

const uomPage = {
  content: [
    {
      id: "uom-1",
      code: "KG",
      name: "Kilogram",
      symbol: "kg",
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
};

function renderPage(permissions: string[] = []) {
  mockUseAuth.mockReturnValue({
    hasPermission: (p: string) => permissions.includes(p),
  });

  server.use(
    http.get(`${API_URL}/acme/uoms`, () => HttpResponse.json(uomPage))
  );

  return render(
    <I18nProvider initialLocale="en" storageKey="test-uoms">
      <MemoryRouter initialEntries={["/acme/uoms"]}>
        <Routes>
          <Route path="/:tenantSlug/uoms" element={<TenantUomsPage />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>
  );
}

describe("TenantUomsPage", () => {
  it("renders UOM list after load", async () => {
    renderPage([TENANT_PERMISSIONS.UOMS_VIEW]);

    await waitFor(() => {
      expect(screen.getByText("KG")).toBeInTheDocument();
      expect(screen.getByText("Kilogram")).toBeInTheDocument();
    });
  });

  it("shows create button when user has UOMS_CREATE permission", async () => {
    renderPage([TENANT_PERMISSIONS.UOMS_VIEW, TENANT_PERMISSIONS.UOMS_CREATE]);

    await waitFor(() => {
      expect(screen.getByText("Create UOM")).toBeInTheDocument();
    });
  });

  it("hides create button when user lacks UOMS_CREATE permission", async () => {
    renderPage([TENANT_PERMISSIONS.UOMS_VIEW]);

    await waitFor(() => {
      expect(screen.getByText("KG")).toBeInTheDocument();
    });

    expect(screen.queryByText("Create UOM")).not.toBeInTheDocument();
  });

  it("shows deactivate button for active UOM when user has UOMS_SOFT_DELETE", async () => {
    renderPage([TENANT_PERMISSIONS.UOMS_VIEW, TENANT_PERMISSIONS.UOMS_SOFT_DELETE]);

    await waitFor(() => {
      expect(screen.getByText("Deactivate")).toBeInTheDocument();
    });
  });
});
