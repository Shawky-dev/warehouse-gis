import { describe, expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import {
  createLandlordRole,
  extractRbacErrorMessage,
  getLandlordUsers,
  reactivateLandlordUser,
  updateLandlordRole,
} from "@/features/landlord/api/rbacApi";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

describe("rbacApi", () => {
  it("sends BOOTSTRAP header for users list", async () => {
    let tenantHeader: string | null = null;
    server.use(
      http.get(`${API_URL}/landlord/users`, ({ request }) => {
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

    await getLandlordUsers({ page: 0, size: 20 });

    expect(tenantHeader).toBe("BOOTSTRAP");
  });

  it("sends locked flag in role create and update payloads", async () => {
    const capturedPayloads: unknown[] = [];

    server.use(
      http.post(`${API_URL}/landlord/roles`, async ({ request }) => {
        capturedPayloads.push(await request.json());
        return HttpResponse.json({
          code: "AUDITOR",
          name: "Auditor",
          description: "Read only",
          permissionCodes: ["landlord.users.view"],
          locked: true,
        });
      }),
      http.put(`${API_URL}/landlord/roles/AUDITOR`, async ({ request }) => {
        capturedPayloads.push(await request.json());
        return HttpResponse.json({
          code: "AUDITOR",
          name: "Auditor Updated",
          description: "Read only",
          permissionCodes: ["landlord.users.view"],
          locked: false,
        });
      })
    );

    await createLandlordRole({
      code: "AUDITOR",
      name: "Auditor",
      description: "Read only",
      permissionCodes: ["landlord.users.view"],
      locked: true,
    });
    await updateLandlordRole("AUDITOR", {
      name: "Auditor Updated",
      description: "Read only",
      permissionCodes: ["landlord.users.view"],
      locked: false,
    });

    expect(capturedPayloads).toEqual([
      {
        code: "AUDITOR",
        name: "Auditor",
        description: "Read only",
        permissionCodes: ["landlord.users.view"],
        locked: true,
      },
      {
        name: "Auditor Updated",
        description: "Read only",
        permissionCodes: ["landlord.users.view"],
        locked: false,
      },
    ]);
  });

  it("extracts backend message from structured RBAC error", async () => {
    server.use(
      http.post(`${API_URL}/landlord/roles`, () =>
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
      await createLandlordRole({
        code: "ADMIN",
        name: "Administrator",
        description: "Full access",
        permissionCodes: ["landlord.users.view"],
        locked: false,
      });
    } catch (error) {
      expect(extractRbacErrorMessage(error)).toBe("ADMIN role must remain locked");
      return;
    }

    throw new Error("Expected createLandlordRole to throw");
  });

  it("sends BOOTSTRAP header for reactivate user action", async () => {
    let tenantHeader: string | null = null;

    server.use(
      http.post(`${API_URL}/landlord/users/00000000-0000-0000-0000-000000000099/reactivate`, ({ request }) => {
        tenantHeader = request.headers.get("x-tenant-id");
        return new HttpResponse(null, { status: 204 });
      })
    );

    await reactivateLandlordUser("00000000-0000-0000-0000-000000000099");

    expect(tenantHeader).toBe("BOOTSTRAP");
  });
});
