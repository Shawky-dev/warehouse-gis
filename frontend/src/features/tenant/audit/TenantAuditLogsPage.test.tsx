import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import TenantAuditLogsPage from "@/features/tenant/audit/TenantAuditLogsPage";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => ({ hasPermission: () => true }),
}));

const auditPage = {
  content: [
    {
      id: "log-1",
      occurredAt: "2026-01-15T10:30:00Z",
      actorEmail: "admin@acme.com",
      actorRoles: '["ADMIN"]',
      action: "UOM_CREATE",
      entityType: "UOM",
      entityId: "uom-1",
      beforeState: null,
      afterState: '{"code":"KG"}',
      tenantId: "acme",
      requestPath: "/acme/uoms",
      requestMethod: "POST",
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

function renderPage() {
  server.use(
    http.get(`${API_URL}/acme/audit-logs`, () => HttpResponse.json(auditPage))
  );

  return render(
    <I18nProvider initialLocale="en" storageKey="test-audit">
      <MemoryRouter initialEntries={["/acme/audit-logs"]}>
        <Routes>
          <Route path="/:tenantSlug/audit-logs" element={<TenantAuditLogsPage />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>
  );
}

describe("TenantAuditLogsPage", () => {
  it("renders audit log entries after load", async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("UOM_CREATE")).toBeInTheDocument();
      expect(screen.getByText("admin@acme.com")).toBeInTheDocument();
      expect(screen.getByText("UOM")).toBeInTheDocument();
    });
  });

  it("renders the page title", async () => {
    renderPage();

    await waitFor(() => {
      expect(screen.getByText("Audit Logs")).toBeInTheDocument();
    });
  });

  it("renders empty state when no logs found", async () => {
    server.use(
      http.get(`${API_URL}/acme/audit-logs`, () =>
        HttpResponse.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      )
    );

    render(
      <I18nProvider initialLocale="en" storageKey="test-audit-empty">
        <MemoryRouter initialEntries={["/acme/audit-logs"]}>
          <Routes>
            <Route path="/:tenantSlug/audit-logs" element={<TenantAuditLogsPage />} />
          </Routes>
        </MemoryRouter>
      </I18nProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("No audit events found.")).toBeInTheDocument();
    });
  });
});
