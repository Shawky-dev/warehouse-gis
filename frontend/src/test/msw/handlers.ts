import { http, HttpResponse } from "msw";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

export const handlers = [
  http.post(`${API_URL}/landlord/auth/login`, () => {
    return HttpResponse.json({
      accessToken: "test-access-token",
      tokenType: "Bearer",
      accessTokenExpiresAt: "2026-01-01T00:00:00Z",
      user: {
        id: "00000000-0000-0000-0000-000000000001",
        email: "admin@system.local",
        roles: ["ROLE_ADMIN"],
        permissions: [
          "landlord.tenants.view",
          "landlord.tenants.create",
          "landlord.users.view",
          "landlord.users.create",
          "landlord.users.edit",
          "landlord.users.reset_password",
          "landlord.users.deactivate",
          "landlord.users.reactivate",
          "landlord.roles.edit",
        ],
      },
    });
  }),

  http.post(`${API_URL}/landlord/auth/refresh`, () => {
    return HttpResponse.json({
      accessToken: "refreshed-access-token",
      tokenType: "Bearer",
      accessTokenExpiresAt: "2026-01-01T00:10:00Z",
      user: {
        id: "00000000-0000-0000-0000-000000000001",
        email: "admin@system.local",
        roles: ["ROLE_ADMIN"],
        permissions: [
          "landlord.tenants.view",
          "landlord.tenants.create",
          "landlord.users.view",
          "landlord.users.create",
          "landlord.users.edit",
          "landlord.users.reset_password",
          "landlord.users.deactivate",
          "landlord.users.reactivate",
          "landlord.roles.edit",
        ],
      },
    });
  }),

  http.post(`${API_URL}/landlord/auth/logout`, () => {
    return new HttpResponse(null, { status: 204 });
  }),
];
