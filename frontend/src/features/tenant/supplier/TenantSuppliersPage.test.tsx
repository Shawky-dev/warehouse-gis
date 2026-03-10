import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import TenantSuppliersPage from "@/features/tenant/supplier/TenantSuppliersPage";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

const supplierPage = {
  content: [
    {
      id: "s-1",
      code: "ACME_CORP",
      name: "Acme Corp",
      contactName: "John",
      contactEmail: "john@acme.com",
      contactPhone: null,
      notes: null,
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
    http.get(`${API_URL}/acme/suppliers`, () => HttpResponse.json(supplierPage))
  );

  return render(
    <I18nProvider initialLocale="en" storageKey="test-suppliers">
      <MemoryRouter initialEntries={["/acme/suppliers"]}>
        <Routes>
          <Route path="/:tenantSlug/suppliers" element={<TenantSuppliersPage />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>
  );
}

describe("TenantSuppliersPage", () => {
  it("renders supplier list after load", async () => {
    renderPage([TENANT_PERMISSIONS.SUPPLIERS_VIEW]);

    await waitFor(() => {
      expect(screen.getByText("ACME_CORP")).toBeInTheDocument();
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
    });
  });

  it("shows create button when user has SUPPLIERS_CREATE permission", async () => {
    renderPage([TENANT_PERMISSIONS.SUPPLIERS_VIEW, TENANT_PERMISSIONS.SUPPLIERS_CREATE]);

    await waitFor(() => {
      expect(screen.getByText("Create supplier")).toBeInTheDocument();
    });
  });

  it("hides create button when user lacks SUPPLIERS_CREATE permission", async () => {
    renderPage([TENANT_PERMISSIONS.SUPPLIERS_VIEW]);

    await waitFor(() => {
      expect(screen.getByText("ACME_CORP")).toBeInTheDocument();
    });

    expect(screen.queryByText("Create supplier")).not.toBeInTheDocument();
  });

  it("shows hard delete button when user has SUPPLIERS_HARD_DELETE permission", async () => {
    renderPage([TENANT_PERMISSIONS.SUPPLIERS_VIEW, TENANT_PERMISSIONS.SUPPLIERS_HARD_DELETE]);

    await waitFor(() => {
      expect(screen.getByText("Hard delete")).toBeInTheDocument();
    });
  });
});
