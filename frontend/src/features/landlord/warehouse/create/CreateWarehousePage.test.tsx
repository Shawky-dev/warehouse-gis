import { describe, expect, it } from "vitest";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { renderWithRouter } from "@/test/utils/renderWithRouter";
import CreateWarehousePage from "@/features/landlord/warehouse/create/CreateWarehousePage";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

describe("CreateWarehousePage", () => {
  it("validates schema format before submit", async () => {
    renderWithRouter(<CreateWarehousePage />);

    fireEvent.change(screen.getByLabelText("Warehouse ID"), { target: { value: "acme" } });
    fireEvent.change(screen.getByLabelText("Schema"), { target: { value: "acme-1" } });
    fireEvent.click(screen.getByRole("button", { name: "Create Warehouse" }));

    expect(await screen.findByText("Schema can only contain letters, numbers, and underscore.")).toBeInTheDocument();
  });

  it("creates warehouse successfully", async () => {
    let createdWarehouseId = "";

    server.use(
      http.post(`${API_URL}/landlord/tenants`, async ({ request }) => {
        const body = (await request.json()) as { tenantId: string };
        createdWarehouseId = body.tenantId;
        return new HttpResponse(null, { status: 204 });
      })
    );

    renderWithRouter(<CreateWarehousePage />);

    fireEvent.change(screen.getByLabelText("Warehouse ID"), { target: { value: "globex" } });
    fireEvent.change(screen.getByLabelText("Schema"), { target: { value: "globex_schema" } });
    fireEvent.change(screen.getByLabelText("Tenant Admin Email"), { target: { value: "admin@globex.local" } });
    fireEvent.change(screen.getByLabelText("Tenant Admin Password"), { target: { value: "admin1234" } });
    fireEvent.click(screen.getByRole("button", { name: "Create Warehouse" }));

    await waitFor(() => {
      expect(screen.getByText('Warehouse "globex" created successfully.')).toBeInTheDocument();
    });
    expect(createdWarehouseId).toBe("globex");
  });

  it("renders Arabic labels when locale is ar", async () => {
    renderWithRouter(<CreateWarehousePage />, ["/"], "ar");

    expect(await screen.findByText("المستودعات")).toBeInTheDocument();
    expect(screen.getByLabelText("معرف المستودع")).toBeInTheDocument();
    expect(screen.getByLabelText("المخطط")).toBeInTheDocument();
    expect(screen.getByLabelText("بريد مدير المستأجر")).toBeInTheDocument();
  });
});
