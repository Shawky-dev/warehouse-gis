import { describe, expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import {
  createUom,
  createSupplier,
  createProduct,
  extractF0ErrorMessage,
  listUoms,
  listSuppliers,
  listProducts,
  listAuditLogs,
  softDeleteUom,
  hardDeleteUom,
} from "@/features/tenant/api/f0Api";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

const uomResult = {
  id: "uom-1",
  code: "KG",
  name: "Kilogram",
  symbol: "kg",
  active: true,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  deactivatedAt: null,
};

describe("f0Api – UOM", () => {
  it("uses tenant slug in uoms URL and X-TENANT-ID header", async () => {
    let capturedHeader: string | null = null;

    server.use(
      http.get(`${API_URL}/acme/uoms`, ({ request }) => {
        capturedHeader = request.headers.get("x-tenant-id");
        return HttpResponse.json(emptyPage);
      })
    );

    await listUoms("AcMe", { page: 0, size: 20 });

    expect(capturedHeader).toBe("acme");
  });

  it("sends correct payload on createUom", async () => {
    let capturedPayload: unknown = null;

    server.use(
      http.post(`${API_URL}/acme/uoms`, async ({ request }) => {
        capturedPayload = await request.json();
        return HttpResponse.json(uomResult);
      })
    );

    await createUom("acme", { code: "KG", name: "Kilogram", symbol: "kg" });

    expect(capturedPayload).toEqual({ code: "KG", name: "Kilogram", symbol: "kg" });
  });

  it("calls soft-delete endpoint for uom", async () => {
    let called = false;

    server.use(
      http.post(`${API_URL}/acme/uoms/uom-1/soft-delete`, () => {
        called = true;
        return new HttpResponse(null, { status: 204 });
      })
    );

    await softDeleteUom("acme", "uom-1");

    expect(called).toBe(true);
  });

  it("calls hard-delete endpoint for uom", async () => {
    let called = false;

    server.use(
      http.delete(`${API_URL}/acme/uoms/uom-1`, () => {
        called = true;
        return new HttpResponse(null, { status: 204 });
      })
    );

    await hardDeleteUom("acme", "uom-1");

    expect(called).toBe(true);
  });
});

describe("f0Api – Supplier", () => {
  it("uses tenant slug in suppliers URL and X-TENANT-ID header", async () => {
    let capturedHeader: string | null = null;

    server.use(
      http.get(`${API_URL}/acme/suppliers`, ({ request }) => {
        capturedHeader = request.headers.get("x-tenant-id");
        return HttpResponse.json(emptyPage);
      })
    );

    await listSuppliers("AcMe", {});

    expect(capturedHeader).toBe("acme");
  });

  it("sends correct payload on createSupplier", async () => {
    let capturedPayload: unknown = null;

    server.use(
      http.post(`${API_URL}/acme/suppliers`, async ({ request }) => {
        capturedPayload = await request.json();
        return HttpResponse.json({
          id: "s-1",
          code: "ACME_CORP",
          name: "Acme Corp",
          contactName: "John",
          contactEmail: "john@acme.com",
          contactPhone: null,
          notes: null,
          active: true,
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
          deactivatedAt: null,
        });
      })
    );

    await createSupplier("acme", {
      code: "ACME_CORP",
      name: "Acme Corp",
      contactName: "John",
      contactEmail: "john@acme.com",
    });

    expect(capturedPayload).toMatchObject({ code: "ACME_CORP", name: "Acme Corp" });
  });
});

describe("f0Api – Product", () => {
  it("uses tenant slug in products URL and X-TENANT-ID header", async () => {
    let capturedHeader: string | null = null;

    server.use(
      http.get(`${API_URL}/acme/products`, ({ request }) => {
        capturedHeader = request.headers.get("x-tenant-id");
        return HttpResponse.json(emptyPage);
      })
    );

    await listProducts("AcMe", {});

    expect(capturedHeader).toBe("acme");
  });

  it("sends supplierIds and primarySupplierId in product create payload", async () => {
    let capturedPayload: unknown = null;

    server.use(
      http.post(`${API_URL}/acme/products`, async ({ request }) => {
        capturedPayload = await request.json();
        return HttpResponse.json({
          id: "p-1",
          sku: "PROD-001",
          name: "Widget",
          description: null,
          baseUomId: "uom-1",
          baseUomCode: "KG",
          baseUomName: "Kilogram",
          trackLot: true,
          trackExpiry: false,
          active: true,
          suppliers: [{ supplierId: "s-1", supplierCode: "ACME", supplierName: "Acme", primary: true }],
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
          deactivatedAt: null,
        });
      })
    );

    await createProduct("acme", {
      sku: "PROD-001",
      name: "Widget",
      baseUomId: "uom-1",
      trackLot: true,
      supplierIds: ["s-1"],
      primarySupplierId: "s-1",
    });

    expect(capturedPayload).toMatchObject({
      sku: "PROD-001",
      supplierIds: ["s-1"],
      primarySupplierId: "s-1",
    });
  });
});

describe("f0Api – Audit", () => {
  it("uses tenant slug in audit-logs URL and X-TENANT-ID header", async () => {
    let capturedHeader: string | null = null;

    server.use(
      http.get(`${API_URL}/acme/audit-logs`, ({ request }) => {
        capturedHeader = request.headers.get("x-tenant-id");
        return HttpResponse.json(emptyPage);
      })
    );

    await listAuditLogs("AcMe", {});

    expect(capturedHeader).toBe("acme");
  });
});

describe("f0Api – extractF0ErrorMessage", () => {
  it("extracts message from structured { code, message } error response", async () => {
    server.use(
      http.post(`${API_URL}/acme/uoms`, () =>
        HttpResponse.json({ code: "CONFLICT", message: "UOM code already exists: KG" }, { status: 409 })
      )
    );

    try {
      await createUom("acme", { code: "KG", name: "Kilogram" });
    } catch (error) {
      expect(extractF0ErrorMessage(error)).toBe("UOM code already exists: KG");
      return;
    }

    throw new Error("Expected createUom to throw");
  });

  it("returns null for non-axios errors", () => {
    expect(extractF0ErrorMessage(new Error("generic"))).toBeNull();
  });
});
