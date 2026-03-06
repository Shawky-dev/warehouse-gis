import { describe, expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import {
  createTenantRole,
  extractTenantRbacErrorMessage,
  getTenantUsers,
  updateTenantRole,
} from "@/features/tenant/api/rbacApi";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

describe("tenant rbacApi", () => {
  it("uses tenant slug in users URL and X-TENANT-ID header", async () => {
    let tenantHeader: string | null = null;

    server.use(
      http.get(`${API_URL}/acme/users`, ({ request }) => {
        tenantHeader = request.headers.get("x-tenant-id");
        return HttpResponse.json({
          content: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        });
      })
    );

    await getTenantUsers("AcMe", { page: 0, size: 20 });

    expect(tenantHeader).toBe("acme");
  });

  it("sends locked flag in tenant role create and update payloads", async () => {
    const capturedPayloads: unknown[] = [];

    server.use(
      http.post(`${API_URL}/acme/roles`, async ({ request }) => {
        capturedPayloads.push(await request.json());
        return HttpResponse.json({
          code: "AUDITOR",
          name: "Auditor",
          description: "Read only",
          permissionCodes: ["tenant.users.view"],
          locked: true,
        });
      }),
      http.put(`${API_URL}/acme/roles/AUDITOR`, async ({ request }) => {
        capturedPayloads.push(await request.json());
        return HttpResponse.json({
          code: "AUDITOR",
          name: "Auditor Updated",
          description: "Read only",
          permissionCodes: ["tenant.users.view"],
          locked: false,
        });
      })
    );

    await createTenantRole("acme", {
      code: "AUDITOR",
      name: "Auditor",
      description: "Read only",
      permissionCodes: ["tenant.users.view"],
      locked: true,
    });
    await updateTenantRole("acme", "AUDITOR", {
      name: "Auditor Updated",
      description: "Read only",
      permissionCodes: ["tenant.users.view"],
      locked: false,
    });

    expect(capturedPayloads).toEqual([
      {
        code: "AUDITOR",
        name: "Auditor",
        description: "Read only",
        permissionCodes: ["tenant.users.view"],
        locked: true,
      },
      {
        name: "Auditor Updated",
        description: "Read only",
        permissionCodes: ["tenant.users.view"],
        locked: false,
      },
    ]);
  });

  it("extracts backend message from structured tenant RBAC error", async () => {
    server.use(
      http.post(`${API_URL}/acme/roles`, () =>
        HttpResponse.json(
          {
            code: "BAD_REQUEST",
            message: "ADMIN role must remain locked",
          },
          { status: 400 }
        )
      )
    );

    try {
      await createTenantRole("acme", {
        code: "ADMIN",
        name: "Administrator",
        description: "Full access",
        permissionCodes: ["tenant.users.view"],
        locked: false,
      });
    } catch (error) {
      expect(extractTenantRbacErrorMessage(error)).toBe("ADMIN role must remain locked");
      return;
    }

    throw new Error("Expected createTenantRole to throw");
  });
});
