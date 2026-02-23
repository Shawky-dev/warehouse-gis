import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { I18nProvider } from "@/i18n";
import { LANDLORD_PERMISSIONS } from "@/features/auth/shared/permissions";
import { LandlordNavbar } from "@/features/landlord/navigation/LandlordNavbar";

const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
  useAuth: () => mockUseAuth(),
}));

describe("LandlordNavbar RBAC", () => {
  it("hides unauthorized landlord nav links", async () => {
    const user = userEvent.setup();
    const permissions: string[] = [LANDLORD_PERMISSIONS.TENANTS_VIEW, LANDLORD_PERMISSIONS.USERS_VIEW];

    mockUseAuth.mockReturnValue({
      hasPermission: (permission: string) => permissions.includes(permission),
      hasRole: () => false,
    });

    renderNavbar("/landlord/warehouses/list");

    expect(await screen.findByText("Warehouse List")).toBeInTheDocument();
    expect(screen.queryByText("Create Warehouse")).not.toBeInTheDocument();

    await user.click(screen.getByText("Accounts"));
    expect(await screen.findByText("Users")).toBeInTheDocument();
    expect(screen.queryByText("Roles")).not.toBeInTheDocument();
  });

  it("hides empty groups when user has no related permissions", () => {
    mockUseAuth.mockReturnValue({
      hasPermission: () => false,
      hasRole: () => false,
    });

    renderNavbar("/landlord");

    expect(screen.getByText("Home")).toBeInTheDocument();
    expect(screen.queryByText("Warehouses")).not.toBeInTheDocument();
    expect(screen.queryByText("Accounts")).not.toBeInTheDocument();
  });

  it("shows roles link when roles.edit permission is present", async () => {
    const user = userEvent.setup();
    const permissions: string[] = [LANDLORD_PERMISSIONS.ROLES_EDIT];

    mockUseAuth.mockReturnValue({
      hasPermission: (permission: string) => permissions.includes(permission),
      hasRole: () => false,
    });

    renderNavbar("/landlord");

    await user.click(screen.getByText("Accounts"));
    expect(await screen.findByText("Roles")).toBeInTheDocument();
  });
});

function renderNavbar(path: string) {
  return render(
    <I18nProvider initialLocale="en" storageKey="test-locale-landlord-navbar">
      <MemoryRouter initialEntries={[path]}>
        <LandlordNavbar />
      </MemoryRouter>
    </I18nProvider>
  );
}
