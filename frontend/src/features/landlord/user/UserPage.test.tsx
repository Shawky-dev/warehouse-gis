import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { renderWithRouter } from "@/test/utils/renderWithRouter";
import { LANDLORD_PERMISSIONS } from "@/features/auth/shared/permissions";
import UserPage from "@/features/landlord/user/UserPage";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

function mockAuth(permissions: string[], roles: string[] = ["ROLE_ADMIN"]) {
  mockUseAuth.mockReturnValue({
    user: {
      email: "admin@system.local",
      permissions,
      roles,
    },
    hasPermission: (permission: string) => permissions.includes(permission),
    hasRole: (role: string) => roles.includes(role),
  });
}

describe("UserPage", () => {
  beforeEach(() => {
    mockUseAuth.mockReset();
  });

  it("renders landlord users from API", async () => {
    mockAuth([LANDLORD_PERMISSIONS.USERS_VIEW]);
    server.use(
      http.get(`${API_URL}/landlord/users`, () =>
        HttpResponse.json({
          content: [
            {
              id: "00000000-0000-0000-0000-000000000010",
              email: "manager@system.local",
              role: "MANAGER",
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
      )
    );

    renderWithRouter(<UserPage />);

    expect(await screen.findByText("manager@system.local")).toBeInTheDocument();
    expect(screen.getByText("MANAGER")).toBeInTheDocument();
  });

  it("hides create action when user lacks users.create permission", async () => {
    mockAuth([LANDLORD_PERMISSIONS.USERS_VIEW]);
    server.use(
      http.get(`${API_URL}/landlord/users`, () =>
        HttpResponse.json({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        })
      )
    );

    renderWithRouter(<UserPage />);

    expect(await screen.findByText("No users found.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create user" })).not.toBeInTheDocument();
  });

  it("shows backend forbidden message when assigning locked role", async () => {
    mockAuth([LANDLORD_PERMISSIONS.USERS_VIEW, LANDLORD_PERMISSIONS.USERS_CREATE]);
    const user = userEvent.setup();

    server.use(
      http.get(`${API_URL}/landlord/users`, () =>
        HttpResponse.json({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        })
      ),
      http.get(`${API_URL}/landlord/roles`, () =>
        HttpResponse.json([
          {
            code: "ADMIN",
            name: "Administrator",
            description: "Full access",
            permissionCodes: [],
            locked: true,
          },
        ])
      ),
      http.post(`${API_URL}/landlord/users`, () =>
        HttpResponse.json(
          {
            code: "FORBIDDEN",
            message: "Locked roles can only be assigned by admins",
          },
          { status: 403 }
        )
      )
    );

    renderWithRouter(<UserPage />);

    await screen.findByText("No users found.");
    await user.click(screen.getByRole("button", { name: "Create user" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("Email"), "new.admin@system.local");
    await user.type(within(dialog).getByLabelText("Password"), "password123");
    await user.click(within(dialog).getByRole("button", { name: "Create user" }));

    expect(await screen.findByText("Locked roles can only be assigned by admins")).toBeInTheDocument();
  });

  it("reactivates an inactive user when permission is granted", async () => {
    mockAuth([LANDLORD_PERMISSIONS.USERS_VIEW, LANDLORD_PERMISSIONS.USERS_REACTIVATE]);
    const user = userEvent.setup();
    let reactivatedUserId: string | null = null;

    server.use(
      http.get(`${API_URL}/landlord/users`, () =>
        HttpResponse.json({
          content: [
            {
              id: "00000000-0000-0000-0000-000000000099",
              email: "inactive@system.local",
              role: "MANAGER",
              active: false,
              createdAt: "2026-01-01T00:00:00Z",
              updatedAt: "2026-01-01T00:00:00Z",
              deactivatedAt: "2026-01-10T00:00:00Z",
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        })
      ),
      http.post(`${API_URL}/landlord/users/00000000-0000-0000-0000-000000000099/reactivate`, () => {
        reactivatedUserId = "00000000-0000-0000-0000-000000000099";
        return new HttpResponse(null, { status: 204 });
      })
    );

    renderWithRouter(<UserPage />);

    await screen.findByText("inactive@system.local");
    await user.click(screen.getByRole("button", { name: "Reactivate" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Reactivate" }));

    expect(reactivatedUserId).toBe("00000000-0000-0000-0000-000000000099");
    expect(await screen.findByText("User reactivated successfully.")).toBeInTheDocument();
  });

  it("prevents non-admin users from deactivating admin accounts", async () => {
    mockAuth(
      [LANDLORD_PERMISSIONS.USERS_VIEW, LANDLORD_PERMISSIONS.USERS_DEACTIVATE],
      ["ROLE_MANAGER"]
    );
    let deactivateCalled = false;

    server.use(
      http.get(`${API_URL}/landlord/users`, () =>
        HttpResponse.json({
          content: [
            {
              id: "00000000-0000-0000-0000-000000000077",
              email: "root@system.local",
              role: "ADMIN",
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
      http.post(`${API_URL}/landlord/users/00000000-0000-0000-0000-000000000077/deactivate`, () => {
        deactivateCalled = true;
        return new HttpResponse(null, { status: 204 });
      })
    );

    renderWithRouter(<UserPage />);

    await screen.findByText("root@system.local");
    const deactivateButton = screen.getByRole("button", { name: "Deactivate" });
    expect(deactivateButton).toBeDisabled();
    expect(deactivateCalled).toBe(false);
  });
});
