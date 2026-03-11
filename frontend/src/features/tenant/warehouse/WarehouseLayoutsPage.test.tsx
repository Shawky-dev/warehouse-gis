import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import { I18nProvider } from "@/i18n";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import WarehouseLayoutsPage from "@/features/tenant/warehouse/WarehouseLayoutsPage";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const mockUseAuth = vi.fn();

let currentTree: Array<Record<string, unknown>> = [];
let lastBatchAddRequest: Record<string, unknown> | null = null;
let lastCopyRequest: Record<string, unknown> | null = null;

vi.mock("@/features/auth/context/AuthContext", () => ({
    useAuth: () => mockUseAuth(),
}));

function installWarehouseHandlers() {
    currentTree = [
        {
            block: {
                id: "block-aisle",
                layoutId: "layout-fork",
                blockTemplateId: "template-aisle",
                parentId: null,
                position: 0,
                identifier: "A",
                side: null,
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
                        identifier: "A",
                        side: "A",
                        createdAt: "2026-03-02T00:00:00Z",
                        updatedAt: "2026-03-02T00:00:00Z",
                    },
                    children: [],
                },
            ],
        },
    ];
    lastBatchAddRequest = null;
    lastCopyRequest = null;

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
        http.get(`${API_URL}/acme/warehouse-layouts/layout-fork/blocks`, () => HttpResponse.json(currentTree)),
        http.post(`${API_URL}/acme/warehouse-layouts/layout-fork/blocks/batch`, async ({ request }) => {
            const payload = await request.json() as Record<string, unknown>;
            lastBatchAddRequest = payload;
            currentTree = [
                {
                    ...currentTree[0],
                    children: [
                        ...(currentTree[0] as { children: unknown[] }).children,
                        {
                            block: {
                                id: "batch-block-1",
                                layoutId: "layout-fork",
                                blockTemplateId: payload.blockTemplateId,
                                parentId: payload.parentId,
                                position: payload.position ?? 1,
                                identifier: "B",
                                side: null,
                                createdAt: "2026-03-02T00:00:00Z",
                                updatedAt: "2026-03-02T00:00:00Z",
                            },
                            children: [],
                        },
                    ],
                },
            ];
            return HttpResponse.json({
                createdBlocks: [
                    {
                        id: "batch-block-1",
                        layoutId: "layout-fork",
                        blockTemplateId: payload.blockTemplateId,
                        parentId: payload.parentId,
                        position: payload.position ?? 1,
                        identifier: "B",
                        side: null,
                        createdAt: "2026-03-02T00:00:00Z",
                        updatedAt: "2026-03-02T00:00:00Z",
                    },
                ],
                totalCreated: payload.count,
                rootCount: payload.count,
            });
        }),
        http.post(`${API_URL}/acme/warehouse-layouts/layout-fork/blocks/copy-subtree`, async ({ request }) => {
            const payload = await request.json() as Record<string, unknown>;
            lastCopyRequest = payload;
            return HttpResponse.json({
                createdBlocks: [
                    {
                        id: "copied-root-1",
                        layoutId: "layout-fork",
                        blockTemplateId: "template-side",
                        parentId: payload.targetParentId,
                        position: payload.position ?? 1,
                        identifier: "B",
                        side: "A",
                        createdAt: "2026-03-02T00:00:00Z",
                        updatedAt: "2026-03-02T00:00:00Z",
                    },
                ],
                totalCreated: 2,
                rootCount: payload.copies,
            });
        })
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
            expect(screen.getByText(/Identifier: A/i)).toBeInTheDocument();
        });
    });

    it("submits batch block creation from the add block dialog", async () => {
        const user = userEvent.setup();

        renderPage(
            "/acme/warehouse-layouts?layoutId=layout-fork&mode=fork&path=block-aisle&tab=builder",
            [TENANT_PERMISSIONS.WAREHOUSE_VIEW, TENANT_PERMISSIONS.WAREHOUSE_EDIT]
        );

        await screen.findByRole("button", { name: "Add block" });
        await user.click(screen.getByRole("button", { name: "Add block" }));
        await user.clear(screen.getByLabelText("Quantity"));
        await user.type(screen.getByLabelText("Quantity"), "3");
        await user.clear(screen.getByLabelText("Insert position"));
        await user.type(screen.getByLabelText("Insert position"), "1");
        await user.click(screen.getByRole("button", { name: "Create block(s)" }));

        await waitFor(() => {
            expect(lastBatchAddRequest).toMatchObject({
                blockTemplateId: "template-aisle",
                parentId: "block-aisle",
                position: 1,
                count: 3,
            });
        });
    });

    it("copies a selected subtree and pastes it multiple times", async () => {
        const user = userEvent.setup();

        renderPage(
            "/acme/warehouse-layouts?layoutId=layout-fork&mode=fork&path=block-aisle,block-side&tab=builder",
            [TENANT_PERMISSIONS.WAREHOUSE_VIEW, TENANT_PERMISSIONS.WAREHOUSE_EDIT]
        );

        await screen.findByRole("button", { name: "Copy subtree" });
        await user.click(screen.getByRole("button", { name: "Copy subtree" }));
        await user.click(screen.getAllByRole("button", { name: "Paste subtree" })[0]);
        await user.clear(screen.getByLabelText("Number of copies"));
        await user.type(screen.getByLabelText("Number of copies"), "2");
        await user.clear(screen.getByLabelText("Insert position"));
        await user.type(screen.getByLabelText("Insert position"), "1");
        await user.click(screen.getByRole("button", { name: "Paste copy" }));

        await waitFor(() => {
            expect(lastCopyRequest).toMatchObject({
                sourceBlockId: "block-side",
                targetParentId: "block-aisle",
                position: 1,
                copies: 2,
            });
        });
    });

    it("collapses and expands nested blocks in the builder tree", async () => {
        const user = userEvent.setup();

        renderPage(
            "/acme/warehouse-layouts?layoutId=layout-fork&mode=fork&path=block-aisle&tab=builder",
            [TENANT_PERMISSIONS.WAREHOUSE_VIEW, TENANT_PERMISSIONS.WAREHOUSE_EDIT]
        );

        await screen.findByText("Aisle · A");
        expect(screen.getByText("Side · A · A")).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Collapse block" }));
        expect(screen.queryByText("Side · A · A")).not.toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Expand block" }));
        expect(screen.getByText("Side · A · A")).toBeInTheDocument();
    });
});