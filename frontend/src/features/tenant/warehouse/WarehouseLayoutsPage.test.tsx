import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import WarehouseLayoutsPage from "@/features/tenant/warehouse/WarehouseLayoutsPage";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const mockUseAuth = vi.fn();

vi.mock("@/features/auth/context/AuthContext", () => ({
    useAuth: () => mockUseAuth(),
}));

function installWarehouseHandlers() {
    server.use(
        http.get(`${API_URL}/acme/warehouse-layouts`, () =>
            HttpResponse.json({
                content: [
                    {
                        id: "layout-active",
                        name: "Main Layout",
                        description: "Active warehouse layout",
                        isActive: true,
                        createdAt: "2026-03-01T00:00:00Z",
                        updatedAt: "2026-03-01T00:00:00Z",
                    },
                    {
                        id: "layout-fork",
                        name: "Fork Layout",
                        description: "Draft alternative",
                        isActive: false,
                        createdAt: "2026-03-02T00:00:00Z",
                        updatedAt: "2026-03-02T00:00:00Z",
                    },
                ],
                page: 0,
                size: 100,
                totalElements: 2,
                totalPages: 1,
            })
        ),
        http.get(`${API_URL}/acme/block-templates`, () =>
            HttpResponse.json({
                content: [
                    {
                        id: "template-aisle",
                        name: "Aisle",
                        identifierFormat: "ALPHA",
                        sideConfig: "LR",
                        sideOptions: null,
                        required: true,
                        description: "Aisle block",
                        iconName: "AlignJustify",
                        createdAt: "2026-03-01T00:00:00Z",
                        updatedAt: "2026-03-01T00:00:00Z",
                    },
                    {
                        id: "template-side",
                        name: "Side",
                        identifierFormat: "ALPHA",
                        sideConfig: "AB",
                        sideOptions: null,
                        required: true,
                        description: "Side block",
                        iconName: "GitBranch",
                        createdAt: "2026-03-01T00:00:00Z",
                        updatedAt: "2026-03-01T00:00:00Z",
                    },
                ],
                page: 0,
                size: 100,
                totalElements: 2,
                totalPages: 1,
            })
        ),
        http.get(`${API_URL}/acme/warehouse-layouts/layout-fork/blocks`, () =>
            HttpResponse.json([
                {
                    block: {
                        id: "block-aisle",
                        layoutId: "layout-fork",
                        blockTemplateId: "template-aisle",
                        parentId: null,
                        position: 0,
                        createdAt: "2026-03-02T00:00:00Z",
                        updatedAt: "2026-03-02T00:00:00Z",
                    },
                    children: [
                        {
                            block: {
                                id: "block-side",
                                layoutId: "layout-fork",
                                blockTemplateId: "template-side",
                                parentId: "block-aisle",
                                position: 0,
                                createdAt: "2026-03-02T00:00:00Z",
                                updatedAt: "2026-03-02T00:00:00Z",
                            },
                            children: [],
                        },
                    ],
                },
            ])
        )
    );
}

function renderPage(initialEntry = "/acme/warehouse-layouts", permissions: string[] = []) {
    mockUseAuth.mockReturnValue({
        hasPermission: (permission: string) => permissions.includes(permission),
    });

    installWarehouseHandlers();

    return render(
        <I18nProvider initialLocale="en" storageKey="test-warehouse-layouts">
            <MemoryRouter initialEntries={[initialEntry]}>
                <Routes>
                    <Route path="/:tenantSlug/warehouse-layouts" element={<WarehouseLayoutsPage />} />
                </Routes>
            </MemoryRouter>
        </I18nProvider>
    );
}

describe("WarehouseLayoutsPage", () => {
    it("renders layouts list and classic preset action", async () => {
        renderPage("/acme/warehouse-layouts", [
            TENANT_PERMISSIONS.WAREHOUSE_VIEW,
            TENANT_PERMISSIONS.WAREHOUSE_EDIT,
        ]);

        await waitFor(() => {
            expect(screen.getAllByText("Main Layout").length).toBeGreaterThan(0);
            expect(screen.getByRole("button", { name: "Create classic preset" })).toBeInTheDocument();
        });
    });

    it("shows fork editing state and selected block inspector from query params", async () => {
        renderPage(
            "/acme/warehouse-layouts?layoutId=layout-fork&mode=fork&path=block-aisle,block-side&tab=builder",
            [TENANT_PERMISSIONS.WAREHOUSE_VIEW, TENANT_PERMISSIONS.WAREHOUSE_EDIT]
        );

        await waitFor(() => {
            expect(screen.getByText(/You are editing a non-active layout/i)).toBeInTheDocument();
            expect(screen.getByText("Block inspector")).toBeInTheDocument();
            expect(screen.getAllByText("Side").length).toBeGreaterThan(0);
        });
    });
});