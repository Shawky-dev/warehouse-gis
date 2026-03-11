import { describe, expect, it } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import TenantRolesPage from "@/features/tenant/roles/TenantRolesPage";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

function renderTenantRolesPage(path = "/acme/roles") {
  return render(
    <I18nProvider initialLocale="en" storageKey="test-locale-tenant-roles-page">
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/:tenantSlug/roles" element={<TenantRolesPage />} />
        </Routes>
      </MemoryRouter>
    </I18nProvider>
  );
}

describe("TenantRolesPage", () => {
  it("groups tenant permissions and filters them with search", async () => {
    const user = userEvent.setup();

    server.use(
      http.get(`${API_URL}/acme/roles`, () =>
        HttpResponse.json([
          {
            code: "MANAGER",
            name: "Manager",
            description: "Operations",
            permissionCodes: ["tenant.users.view"],
            locked: false,
          },
        ])
      ),
      http.get(`${API_URL}/acme/permissions`, () =>
        HttpResponse.json([
          {
            code: "tenant.users.view",
            description: "View users in tenant scope",
          },
          {
            code: "tenant.users.create",
            description: "Create users in tenant scope",
          },
          {
            code: "tenant.warehouse.layout.manage",
            description: "Manage layouts in tenant scope",
          },
        ])
      )
    );

    renderTenantRolesPage();

    expect(await screen.findByRole("heading", { name: "Users" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Warehouse / Layout" })).toBeInTheDocument();

    await user.type(screen.getByLabelText("Search permissions"), "warehouse");

    expect(screen.queryByRole("heading", { name: "Users" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Warehouse / Layout" })).toBeInTheDocument();
    expect(screen.getByText("tenant.warehouse.layout.manage")).toBeInTheDocument();
  });

  it("locks ADMIN role editing in UI", async () => {
    server.use(
      http.get(`${API_URL}/acme/roles`, () =>
        HttpResponse.json([
          {
            code: "ADMIN",
            name: "Administrator",
            description: "Full access",
            permissionCodes: ["tenant.users.view"],
            locked: true,
          },
        ])
      ),
      http.get(`${API_URL}/acme/permissions`, () =>
        HttpResponse.json([
          {
            code: "tenant.users.view",
            description: "View users",
          },
        ])
      )
    );

    renderTenantRolesPage();

    expect(await screen.findByDisplayValue("ADMIN")).toBeDisabled();
    expect(screen.getByText("ADMIN is permanently locked and cannot be edited.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save role" })).toBeDisabled();
  });

  it("sends locked flag in tenant create role form submission", async () => {
    const user = userEvent.setup();
    let createPayload: unknown = null;
    let created = false;

    server.use(
      http.get(`${API_URL}/acme/roles`, () => {
        if (!created) {
          return HttpResponse.json([]);
        }
        return HttpResponse.json([
          {
            code: "AUDITOR",
            name: "Auditor",
            description: "Read-only",
            permissionCodes: ["tenant.users.view"],
            locked: true,
          },
        ]);
      }),
      http.get(`${API_URL}/acme/permissions`, () =>
        HttpResponse.json([
          {
            code: "tenant.users.view",
            description: "View users",
          },
          {
            code: "tenant.warehouse.layout.manage",
            description: "Manage layouts",
          },
        ])
      ),
      http.post(`${API_URL}/acme/roles`, async ({ request }) => {
        createPayload = await request.json();
        created = true;
        return HttpResponse.json({
          code: "AUDITOR",
          name: "Auditor",
          description: "Read-only",
          permissionCodes: ["tenant.users.view"],
          locked: true,
        });
      })
    );

    renderTenantRolesPage();

    await screen.findByText("No roles found.");
    await user.click(screen.getByRole("button", { name: "Create role" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("Code"), "auditor");
    await user.type(within(dialog).getByLabelText("Name"), "Auditor");
    await user.type(within(dialog).getByLabelText("Search permissions"), "users");
    const checkboxes = within(dialog).getAllByRole("checkbox");
    await user.click(checkboxes[0]);
    await user.click(checkboxes[1]);
    await user.click(within(dialog).getByRole("button", { name: "Create role" }));

    await waitFor(() => {
      expect(createPayload).toEqual({
        code: "AUDITOR",
        name: "Auditor",
        description: null,
        permissionCodes: ["tenant.users.view"],
        locked: true,
      });
    });
  });
});
