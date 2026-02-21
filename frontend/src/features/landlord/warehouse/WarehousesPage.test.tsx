import { describe, expect, it } from "vitest";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { renderWithRouter } from "@/test/utils/renderWithRouter";
import WarehousesPage from "@/features/landlord/warehouse/WarehousesPage";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

describe("WarehousesPage", () => {
  it("renders warehouses from GET /landlord/tenants", async () => {
    server.use(
      http.get(`${API_URL}/landlord/tenants`, () =>
        HttpResponse.json([
          { tenantId: "acme", schema: "acme_schema" },
          { tenantId: "beta", schema: "beta_schema" },
        ])
      )
    );

    renderWithRouter(<WarehousesPage />);

    expect(await screen.findByText("acme")).toBeInTheDocument();
    expect(screen.getByText("beta_schema")).toBeInTheDocument();
  });

  it("validates schema format before submit", async () => {
    server.use(http.get(`${API_URL}/landlord/tenants`, () => HttpResponse.json([])));

    renderWithRouter(<WarehousesPage />);

    fireEvent.change(screen.getByLabelText("Warehouse ID"), { target: { value: "acme" } });
    fireEvent.change(screen.getByLabelText("Schema"), { target: { value: "acme-1" } });
    fireEvent.click(screen.getByRole("button", { name: "Create Warehouse" }));

    expect(await screen.findByText("Schema can only contain letters, numbers, and underscore.")).toBeInTheDocument();
  });

  it("creates warehouse then refreshes list", async () => {
    const tenants = [{ tenantId: "acme", schema: "acme_schema" }];

    server.use(
      http.get(`${API_URL}/landlord/tenants`, () => HttpResponse.json(tenants)),
      http.post(`${API_URL}/landlord/tenants`, async ({ request }) => {
        const body = (await request.json()) as { tenantId: string; schema: string };
        tenants.push({ tenantId: body.tenantId, schema: body.schema });
        return new HttpResponse(null, { status: 204 });
      })
    );

    renderWithRouter(<WarehousesPage />);

    expect(await screen.findByText("acme")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Warehouse ID"), { target: { value: "globex" } });
    fireEvent.change(screen.getByLabelText("Schema"), { target: { value: "globex_schema" } });
    fireEvent.click(screen.getByRole("button", { name: "Create Warehouse" }));

    await waitFor(() => {
      expect(screen.getByText('Warehouse "globex" created successfully.')).toBeInTheDocument();
    });
    expect(screen.getByText("globex_schema")).toBeInTheDocument();
  });

  it("renders Arabic labels when locale is ar", async () => {
    server.use(http.get(`${API_URL}/landlord/tenants`, () => HttpResponse.json([])));

    renderWithRouter(<WarehousesPage />, ["/"], "ar");

    expect(await screen.findByText("المستودعات")).toBeInTheDocument();
    expect(screen.getByLabelText("معرف المستودع")).toBeInTheDocument();
    expect(screen.getByLabelText("المخطط")).toBeInTheDocument();
  });
});
