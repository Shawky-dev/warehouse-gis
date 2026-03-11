import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { TenantNavbar } from "@/features/tenant/navigation/TenantNavbar";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

function renderNavbar() {
  mockUseAuth.mockReturnValue({
    hasPermission: (permission: string) => permission === TENANT_PERMISSIONS.WAREHOUSE_VIEW,
  });

  server.use(
    http.get(`${API_URL}/acme/warehouse-layouts`, ({ request }) => {
      const url = new URL(request.url);
      const active = url.searchParams.get("active");
      return HttpResponse.json({
        content: active === "true"
          ? [
            {
              id: "layout-active",
              name: "Main Layout",
              description: "Active layout",
              isActive: true,
              createdAt: "2026-03-01T00:00:00Z",
              updatedAt: "2026-03-01T00:00:00Z",
            },
          ]
          : [],
        page: 0,
        size: 100,
        totalElements: active === "true" ? 1 : 0,
        totalPages: 1,
      });
    }),
    http.get(`${API_URL}/acme/block-templates`, () =>
      HttpResponse.json({
        content: [
          {
            id: "template-aisle",
            name: "Aisle",
            identifierFormat: "ALPHA",
            sideConfig: "LR",
            sideOptions: null,
            required: true,
            description: "Aisle",
            iconName: "AlignJustify",
            createdAt: "2026-03-01T00:00:00Z",
            updatedAt: "2026-03-01T00:00:00Z",
          },
          {
            id: "template-side",
            name: "Side",
            identifierFormat: "ALPHA",
            sideConfig: "AB",
            sideOptions: null,
            required: true,
            description: "Side",
            iconName: "GitBranch",
            createdAt: "2026-03-01T00:00:00Z",
            updatedAt: "2026-03-01T00:00:00Z",
          },
        ],
        page: 0,
        size: 100,
        totalElements: 2,
        totalPages: 1,
      })
    ),
    http.get(`${API_URL}/acme/warehouse-layouts/layout-active/blocks`, () =>
      HttpResponse.json([
        {
          block: {
            id: "block-aisle",
            layoutId: "layout-active",
            blockTemplateId: "template-aisle",
            parentId: null,
            position: 0,
            createdAt: "2026-03-01T00:00:00Z",
            updatedAt: "2026-03-01T00:00:00Z",
          },
          children: [
            {
              block: {
                id: "block-side",
                layoutId: "layout-active",
                blockTemplateId: "template-side",
                parentId: "block-aisle",
                position: 0,
                createdAt: "2026-03-01T00:00:00Z",
                updatedAt: "2026-03-01T00:00:00Z",
              },
              children: [],
            },
          ],
        },
      ])
    )
  );

  return render(
    <I18nProvider initialLocale="en" storageKey="test-tenant-navbar-warehouse">
      <MemoryRouter initialEntries={["/acme/warehouse-layouts?layoutId=layout-active&path=block-aisle,block-side&tab=builder&mode=active"]}>
        <Routes>
          <Route path="/:tenantSlug/*" element={<TenantNavbar />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>
  );
}

describe("TenantNavbar", () => {
  it("renders the active layout tree under the warehouse layouts group with icon metadata", async () => {
    renderNavbar();

    await waitFor(() => {
      expect(screen.getByText("Layouts")).toBeInTheDocument();
      const aisleLink = screen.getByRole("link", { name: "Aisle" });
      const sideLink = screen.getByRole("link", { name: "Side" });
      expect(aisleLink).toHaveAttribute("data-warehouse-icon", "AlignJustify");
      expect(sideLink).toHaveAttribute("data-warehouse-icon", "GitBranch");
    });
  });
});
