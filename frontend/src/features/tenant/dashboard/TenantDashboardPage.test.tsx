import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { I18nProvider } from "@/i18n";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import TenantDashboardPage from "@/features/tenant/dashboard/TenantDashboardPage";

const mockUseAuth = vi.fn();
const mockGetDashboardSection = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

vi.mock("@/features/tenant/dashboard/dashboardApi", () => ({
  getDashboardSection: (...args: unknown[]) => mockGetDashboardSection(...args),
  extractDashboardErrorMessage: (_error: unknown, fallback: string) => fallback,
}));

function renderPage(permissions: string[]) {
  mockUseAuth.mockReturnValue({
    status: "authenticated",
    hasPermission: (permission: string) => permissions.includes(permission),
  });

  return render(
    <I18nProvider initialLocale="en" storageKey="test-tenant-dashboard-page">
      <MemoryRouter initialEntries={["/acme"]}>
        <Routes>
          <Route path="/:tenantSlug" element={<TenantDashboardPage />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>
  );
}

describe("TenantDashboardPage", () => {
  beforeEach(() => {
    mockUseAuth.mockReset();
    mockGetDashboardSection.mockReset();
    mockGetDashboardSection.mockResolvedValue({
      section: "spatial-kpis",
      stats: [],
      highlights: [],
      generatedAt: "2026-01-01T00:00:00Z",
    });
  });

  it("shows an access-denied state when the user has no dashboard permissions", () => {
    renderPage([]);

    expect(screen.getByText("Access denied")).toBeInTheDocument();
    expect(screen.getByText("You do not have access to any dashboard workspaces for this tenant yet.")).toBeInTheDocument();
  });

  it("shows only permitted sections and lazy-loads the active tab", async () => {
    mockGetDashboardSection.mockResolvedValue({
      section: "spatial-kpis",
      stats: [],
      highlights: [],
      generatedAt: "2026-01-01T00:00:00Z",
    });

    renderPage([TENANT_PERMISSIONS.INVENTORY_VIEW, TENANT_PERMISSIONS.AUDIT_VIEW]);

    expect(screen.getByRole("tab", { name: "Overview" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Activity" })).toBeInTheDocument();
    expect(screen.queryByText("Master data")).not.toBeInTheDocument();
    expect(screen.queryByText("Choose a permitted workspace tab while dashboard widgets are being wired up.")).not.toBeInTheDocument();

    await waitFor(() => {
      expect(mockGetDashboardSection).toHaveBeenCalledWith("acme", "spatial-kpis");
    });

    expect(await screen.findByText("No dashboard data yet.")).toBeInTheDocument();

    mockGetDashboardSection.mockResolvedValueOnce({
      section: "activity",
      stats: [],
      highlights: [],
      generatedAt: "2026-01-01T00:00:00Z",
    });

    fireEvent.click(screen.getByRole("tab", { name: "Activity" }));

    await waitFor(() => {
      expect(mockGetDashboardSection).toHaveBeenCalledWith("acme", "activity");
    });
  });

  it("renders an error state and retries the active section", async () => {
    mockGetDashboardSection
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce({
        section: "spatial-kpis",
        stats: [],
        highlights: [],
        generatedAt: "2026-01-01T00:00:00Z",
      });

    renderPage([TENANT_PERMISSIONS.WAREHOUSE_VIEW]);

    expect((await screen.findAllByText("Failed to load dashboard section.")).length).toBeGreaterThan(0);

    fireEvent.click(screen.getByRole("button", { name: "Retry" }));

    await waitFor(() => {
      expect(mockGetDashboardSection).toHaveBeenCalledTimes(2);
    });

    expect(await screen.findByText("No dashboard data yet.")).toBeInTheDocument();
  });

  it("renders dashboard widgets when the section returns rich widget payloads", async () => {
    mockGetDashboardSection.mockResolvedValue({
      section: "activity",
      stats: [],
      highlights: [],
      widgets: [
        {
          type: "alert-list",
          id: "recent-activity",
          title: "Recent activity feed",
          description: "Latest audit rows.",
          items: [
            {
              id: "activity-1",
              label: "PRODUCT_UPDATE Product",
              value: "ops@example.com",
              category: "product",
              hint: "2026-01-01T00:00:00Z - PATCH at /products/1 - entity 1",
            },
          ],
        },
      ],
      generatedAt: "2026-01-01T00:00:00Z",
    });

    renderPage([TENANT_PERMISSIONS.AUDIT_VIEW]);

    expect((await screen.findAllByText("Recent activity feed")).length).toBeGreaterThan(0);
    expect(screen.getByText("PRODUCT_UPDATE Product")).toBeInTheDocument();
    expect(screen.getByText("ops@example.com")).toBeInTheDocument();
  });
});
