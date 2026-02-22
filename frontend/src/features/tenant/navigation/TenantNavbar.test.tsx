import { describe, expect, it } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import { I18nProvider } from "@/i18n";
import { TenantNavbar } from "@/features/tenant/navigation/TenantNavbar";

describe("TenantNavbar", () => {
  it("shows only tenant nav items for v1 scope", () => {
    render(
      <I18nProvider initialLocale="en" storageKey="test-locale-tenant-navbar">
        <MemoryRouter initialEntries={["/acme"]}>
          <TenantNavbar />
        </MemoryRouter>
      </I18nProvider>
    );

    expect(screen.getByText("Dashboard")).toBeInTheDocument();
    expect(screen.getByText("Products")).toBeInTheDocument();
    expect(screen.queryByText("Warehouses")).not.toBeInTheDocument();
    expect(screen.queryByText("Users")).not.toBeInTheDocument();
    expect(screen.queryByText("Roles")).not.toBeInTheDocument();
  });
});
