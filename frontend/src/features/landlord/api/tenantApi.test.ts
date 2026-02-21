import { describe, expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { createTenant, extractTenantErrorMessage, getTenants } from "@/features/landlord/api/tenantApi";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

describe("tenantApi", () => {
  it("sends BOOTSTRAP header for getTenants", async () => {
    let tenantHeader: string | null = null;
    server.use(
      http.get(`${API_URL}/landlord/tenants`, ({ request }) => {
        tenantHeader = request.headers.get("x-tenant-id");
        return HttpResponse.json([]);
      })
    );

    await getTenants();

    expect(tenantHeader).toBe("BOOTSTRAP");
  });

  it("sends BOOTSTRAP header for createTenant", async () => {
    let tenantHeader: string | null = null;
    server.use(
      http.post(`${API_URL}/landlord/tenants`, async ({ request }) => {
        tenantHeader = request.headers.get("x-tenant-id");
        const body = (await request.json()) as { tenantId: string; schema: string };
        return HttpResponse.json(
          {
            tenantId: body.tenantId,
            schema: body.schema,
          },
          { status: 201 }
        );
      })
    );

    await expect(createTenant({ tenantId: "acme", schema: "acme" })).resolves.toBeUndefined();
    expect(tenantHeader).toBe("BOOTSTRAP");
  });

  it("extracts string error message from backend response", async () => {
    server.use(
      http.post(
        `${API_URL}/landlord/tenants`,
        () => new HttpResponse("Tenant already exists: acme", { status: 400 })
      )
    );

    try {
      await createTenant({ tenantId: "acme", schema: "acme" });
    } catch (error) {
      expect(extractTenantErrorMessage(error)).toBe("Tenant already exists: acme");
      return;
    }

    throw new Error("Expected createTenant to throw");
  });
});
