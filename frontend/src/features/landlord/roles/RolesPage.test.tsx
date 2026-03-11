import { describe, expect, it } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { renderWithRouter } from "@/test/utils/renderWithRouter";
import RolesPage from "@/features/landlord/roles/RolesPage";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

describe("RolesPage", () => {
  it("groups landlord permissions and filters them with search", async () => {
    const user = userEvent.setup();

    server.use(
      http.get(`${API_URL}/landlord/roles`, () =>
        HttpResponse.json([
          {
            code: "MANAGER",
            name: "Manager",
            description: "Operations",
            permissionCodes: ["landlord.tenants.view"],
            locked: false,
          },
        ])
      ),
      http.get(`${API_URL}/landlord/permissions`, () =>
        HttpResponse.json([
          {
            code: "landlord.tenants.view",
            description: "View tenants in landlord scope",
          },
          {
            code: "landlord.tenants.create",
            description: "Create tenants in landlord scope",
          },
          {
            code: "landlord.users.edit",
            description: "Edit users in landlord scope",
          },
        ])
      )
    );

    renderWithRouter(<RolesPage />);

    expect(await screen.findByRole("heading", { name: "Tenants" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Users" })).toBeInTheDocument();

    await user.type(screen.getByLabelText("Search permissions"), "tenants");

    expect(screen.getByRole("heading", { name: "Tenants" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Users" })).not.toBeInTheDocument();
    expect(screen.getByText("landlord.tenants.create")).toBeInTheDocument();
  });

  it("locks ADMIN role editing in UI", async () => {
    server.use(
      http.get(`${API_URL}/landlord/roles`, () =>
        HttpResponse.json([
          {
            code: "ADMIN",
            name: "Administrator",
            description: "Full access",
            permissionCodes: ["landlord.users.view"],
            locked: true,
          },
        ])
      ),
      http.get(`${API_URL}/landlord/permissions`, () =>
        HttpResponse.json([
          {
            code: "landlord.users.view",
            description: "View users",
          },
        ])
      )
    );

    renderWithRouter(<RolesPage />);

    expect(await screen.findByDisplayValue("ADMIN")).toBeDisabled();
    expect(screen.getByText("ADMIN is permanently locked and cannot be edited.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save role" })).toBeDisabled();
  });

  it("sends locked flag in create role form submission", async () => {
    const user = userEvent.setup();
    let createPayload: unknown = null;
    let created = false;

    server.use(
      http.get(`${API_URL}/landlord/roles`, () => {
        if (!created) {
          return HttpResponse.json([]);
        }
        return HttpResponse.json([
          {
            code: "AUDITOR",
            name: "Auditor",
            description: "Read-only",
            permissionCodes: ["landlord.users.view"],
            locked: true,
          },
        ]);
      }),
      http.get(`${API_URL}/landlord/permissions`, () =>
        HttpResponse.json([
          {
            code: "landlord.users.view",
            description: "View users",
          },
          {
            code: "landlord.tenants.create",
            description: "Create tenants",
          },
        ])
      ),
      http.post(`${API_URL}/landlord/roles`, async ({ request }) => {
        createPayload = await request.json();
        created = true;
        return HttpResponse.json({
          code: "AUDITOR",
          name: "Auditor",
          description: "Read-only",
          permissionCodes: ["landlord.users.view"],
          locked: true,
        });
      })
    );

    renderWithRouter(<RolesPage />);

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
        permissionCodes: ["landlord.users.view"],
        locked: true,
      });
    });
  });
});
