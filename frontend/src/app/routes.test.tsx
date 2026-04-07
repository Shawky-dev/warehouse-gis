import { beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import App from "@/app/App";
import { server } from "@/test/msw/server";
import { http, HttpResponse } from "msw";

vi.mock("@/features/gis/floorplans/WarehouseMapView", () => ({
  WarehouseMapView: () => <div>map-view</div>,
}));

vi.mock("@/features/gis/viewer/WarehouseMapPage", () => ({
  default: () => <div>warehouse-map-page</div>,
}));

vi.mock("@/features/gis/floorplans/FloorPlansPage", () => ({
  default: () => <div>floor-plans-page</div>,
}));

vi.mock("@/features/gis/zones/ZoneManagementPage", () => ({
  default: () => <div>zones-page</div>,
}));

vi.mock("@/features/gis/hazardBuffers/HazardBuffersPage", () => ({
  default: () => <div>hazard-buffers-page</div>,
}));

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

function createMockStorage(): Storage {
  const store = new Map<string, string>();

  return {
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
      store.set(key, value);
    },
    removeItem: (key: string) => {
      store.delete(key);
    },
    clear: () => {
      store.clear();
    },
    key: (index: number) => Array.from(store.keys())[index] ?? null,
    get length() {
      return store.size;
    },
  };
}

function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>
  );
}

describe("App routing", () => {
  beforeEach(() => {
    Object.defineProperty(window, "localStorage", {
      configurable: true,
      value: createMockStorage(),
    });
  });

  it("renders neutral scope landing page at /", () => {
    renderApp("/");

    expect(screen.getByText("Landlord")).toBeInTheDocument();
    expect(screen.getByText("Tenant")).toBeInTheDocument();
  });

  it("renders landlord login page at /landlord/auth/login", async () => {
    server.use(
      http.post(`${API_URL}/landlord/auth/refresh`, () => {
        return HttpResponse.json({ code: "UNAUTHORIZED" }, { status: 401 });
      })
    );

    renderApp("/landlord/auth/login");

    await waitFor(() => {
      expect(screen.getByText("Landlord sign in")).toBeInTheDocument();
    });
  });

  it("renders tenant login page at /:tenantSlug/auth/login", async () => {
    server.use(
      http.post(`${API_URL}/acme/auth/refresh`, () => {
        return HttpResponse.json({ code: "UNAUTHORIZED" }, { status: 401 });
      })
    );

    renderApp("/acme/auth/login");

    await waitFor(() => {
      expect(screen.getByText("Tenant sign in (acme)")).toBeInTheDocument();
    });
  });
});
