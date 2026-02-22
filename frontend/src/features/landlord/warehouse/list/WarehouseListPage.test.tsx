import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { renderWithRouter } from "@/test/utils/renderWithRouter";
import WarehouseListPage from "@/features/landlord/warehouse/list/WarehouseListPage";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

describe("WarehouseListPage", () => {
  it("renders warehouses from GET /landlord/tenants", async () => {
    server.use(
      http.get(`${API_URL}/landlord/tenants`, () =>
        HttpResponse.json([
          { tenantId: "acme", schema: "acme_schema" },
          { tenantId: "beta", schema: "beta_schema" },
        ])
      )
    );

    renderWithRouter(<WarehouseListPage />);

    expect(await screen.findByText("acme")).toBeInTheDocument();
    expect(screen.getByText("beta_schema")).toBeInTheDocument();
  });

  it("renders empty state", async () => {
    server.use(http.get(`${API_URL}/landlord/tenants`, () => HttpResponse.json([])));

    renderWithRouter(<WarehouseListPage />);

    expect(await screen.findByText("No warehouses found.")).toBeInTheDocument();
  });
});
