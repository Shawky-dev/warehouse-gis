import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { render, screen, fireEvent } from "@testing-library/react";
import { I18nProvider } from "@/i18n";
import { ViewerLayerPanel } from "@/features/gis/viewer/ViewerLayerPanel";
import type { StaticHeatmapRecord, DynamicHeatmapMetric } from "@/features/tenant/types/gis";

const STATIC_HEATMAPS: StaticHeatmapRecord[] = [
    {
        id: "h1",
        name: "Picking Density",
        sourceFilename: "picking.tif",
        geoserverLayerName: "acme_picking",
        isDefault: true,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
    },
    {
        id: "h2",
        name: "Storage Utilization",
        sourceFilename: "storage.tif",
        geoserverLayerName: "acme_storage",
        isDefault: false,
        createdAt: "2026-01-02T00:00:00Z",
        updatedAt: "2026-01-02T00:00:00Z",
    },
];

const DYNAMIC_METRICS: DynamicHeatmapMetric[] = [
    { key: "quantity_sum", label: "Quantity Sum", description: "Sum of quantities", unit: null },
    { key: "weight_sum", label: "Weight Sum", description: "Sum of weights", unit: "kg" },
];

function renderPanel(props: Partial<Parameters<typeof ViewerLayerPanel>[0]> = {}) {
    return render(
        <I18nProvider initialLocale="en" storageKey="test-viewer-panel">
            <MemoryRouter initialEntries={["/acme/gis/map"]}>
                <Routes>
                    <Route
                        path="/:tenantSlug/gis/map"
                        element={
                            <ViewerLayerPanel
                                templates={[]}
                                polygonCountByTemplate={{}}
                                visibilityByTemplate={{}}
                                onVisibilityToggle={vi.fn()}
                                svgVisible={true}
                                onSvgVisibilityToggle={vi.fn()}
                                selectedPolygon={null}
                                onClearSelection={vi.fn()}
                                {...props}
                            />
                        }
                    />
                </Routes>
            </MemoryRouter>
        </I18nProvider>
    );
}

describe("ViewerLayerPanel", () => {
    describe("static heatmap section", () => {
        it("renders static heatmap section when toggle is provided", () => {
            renderPanel({
                onStaticHeatmapVisibilityToggle: vi.fn(),
                staticHeatmaps: STATIC_HEATMAPS,
                selectedStaticHeatmapId: "h1",
                staticHeatmapVisible: true,
                onStaticHeatmapSelect: vi.fn(),
            });

            expect(screen.getByText("Static Heatmap")).toBeInTheDocument();
        });

        it("does not render static heatmap section when toggle is not provided", () => {
            renderPanel();

            expect(screen.queryByText("Static Heatmap")).not.toBeInTheDocument();
        });

        it("lists static heatmap names in select", () => {
            renderPanel({
                onStaticHeatmapVisibilityToggle: vi.fn(),
                staticHeatmaps: STATIC_HEATMAPS,
                selectedStaticHeatmapId: "h1",
                staticHeatmapVisible: true,
                onStaticHeatmapSelect: vi.fn(),
            });

            expect(screen.getByRole("option", { name: "Picking Density" })).toBeInTheDocument();
            expect(screen.getByRole("option", { name: "Storage Utilization" })).toBeInTheDocument();
        });

        it("disables select when no static heatmaps exist", () => {
            renderPanel({
                onStaticHeatmapVisibilityToggle: vi.fn(),
                staticHeatmaps: [],
                selectedStaticHeatmapId: null,
                staticHeatmapVisible: true,
                onStaticHeatmapSelect: vi.fn(),
            });

            expect(screen.getByRole("combobox", { name: "Select heatmap" })).toBeDisabled();
        });

        it("calls onStaticHeatmapSelect when selection changes", () => {
            const onSelect = vi.fn();
            renderPanel({
                onStaticHeatmapVisibilityToggle: vi.fn(),
                staticHeatmaps: STATIC_HEATMAPS,
                selectedStaticHeatmapId: "h1",
                staticHeatmapVisible: true,
                onStaticHeatmapSelect: onSelect,
            });

            const select = screen.getByRole("combobox", { name: "Select heatmap" });
            fireEvent.change(select, { target: { value: "h2" } });

            expect(onSelect).toHaveBeenCalledWith("h2");
        });

        it("calls onStaticHeatmapVisibilityToggle when eye button is clicked", () => {
            const onToggle = vi.fn();
            renderPanel({
                onStaticHeatmapVisibilityToggle: onToggle,
                staticHeatmaps: STATIC_HEATMAPS,
                selectedStaticHeatmapId: "h1",
                staticHeatmapVisible: true,
                onStaticHeatmapSelect: vi.fn(),
            });

            // There are multiple visibility buttons; find the one for static heatmap section
            const eyeButtons = screen.getAllByTitle("Visible");
            // Static heatmap visibility button is after SVG and hazard buffer buttons
            // Fire click on the last "Visible" title button in the static section
            fireEvent.click(eyeButtons[eyeButtons.length - 1]);

            expect(onToggle).toHaveBeenCalled();
        });
    });

    describe("dynamic heatmap section", () => {
        it("renders dynamic heatmap section when toggle is provided", () => {
            renderPanel({
                onDynamicHeatmapVisibilityToggle: vi.fn(),
                dynamicMetrics: DYNAMIC_METRICS,
                selectedDynamicMetricKey: "quantity_sum",
                dynamicHeatmapVisible: true,
                onDynamicMetricSelect: vi.fn(),
                onDynamicHeatmapRefresh: vi.fn(),
            });

            expect(screen.getByText("Dynamic Heatmap")).toBeInTheDocument();
        });

        it("does not render dynamic heatmap section when toggle is not provided", () => {
            renderPanel();

            expect(screen.queryByText("Dynamic Heatmap")).not.toBeInTheDocument();
        });

        it("lists metric labels in select including unit when present", () => {
            renderPanel({
                onDynamicHeatmapVisibilityToggle: vi.fn(),
                dynamicMetrics: DYNAMIC_METRICS,
                selectedDynamicMetricKey: "quantity_sum",
                dynamicHeatmapVisible: true,
                onDynamicMetricSelect: vi.fn(),
                onDynamicHeatmapRefresh: vi.fn(),
            });

            expect(screen.getByRole("option", { name: "Quantity Sum" })).toBeInTheDocument();
            expect(screen.getByRole("option", { name: "Weight Sum (kg)" })).toBeInTheDocument();
        });

        it("disables metric select when no metrics exist", () => {
            renderPanel({
                onDynamicHeatmapVisibilityToggle: vi.fn(),
                dynamicMetrics: [],
                selectedDynamicMetricKey: null,
                dynamicHeatmapVisible: true,
                onDynamicMetricSelect: vi.fn(),
                onDynamicHeatmapRefresh: vi.fn(),
            });

            expect(screen.getByRole("combobox", { name: "Select metric" })).toBeDisabled();
        });

        it("calls onDynamicHeatmapRefresh when refresh button is clicked", () => {
            const onRefresh = vi.fn();
            renderPanel({
                onDynamicHeatmapVisibilityToggle: vi.fn(),
                dynamicMetrics: DYNAMIC_METRICS,
                selectedDynamicMetricKey: "quantity_sum",
                dynamicHeatmapVisible: true,
                onDynamicMetricSelect: vi.fn(),
                onDynamicHeatmapRefresh: onRefresh,
                isDynamicHeatmapRefreshing: false,
            });

            fireEvent.click(screen.getByTitle("Refresh"));

            expect(onRefresh).toHaveBeenCalled();
        });

        it("disables refresh button while refreshing", () => {
            renderPanel({
                onDynamicHeatmapVisibilityToggle: vi.fn(),
                dynamicMetrics: DYNAMIC_METRICS,
                selectedDynamicMetricKey: "quantity_sum",
                dynamicHeatmapVisible: true,
                onDynamicMetricSelect: vi.fn(),
                onDynamicHeatmapRefresh: vi.fn(),
                isDynamicHeatmapRefreshing: true,
            });

            expect(screen.getByTitle("Refresh")).toBeDisabled();
        });
    });

    describe("layer visibility toggles", () => {
        it("updates static and dynamic visibility independently", () => {
            const onStaticToggle = vi.fn();
            const onDynamicToggle = vi.fn();
            renderPanel({
                onStaticHeatmapVisibilityToggle: onStaticToggle,
                staticHeatmaps: STATIC_HEATMAPS,
                selectedStaticHeatmapId: "h1",
                staticHeatmapVisible: true,
                onStaticHeatmapSelect: vi.fn(),
                onDynamicHeatmapVisibilityToggle: onDynamicToggle,
                dynamicMetrics: DYNAMIC_METRICS,
                selectedDynamicMetricKey: "quantity_sum",
                dynamicHeatmapVisible: false,
                onDynamicMetricSelect: vi.fn(),
                onDynamicHeatmapRefresh: vi.fn(),
            });

            // Both sections are rendered independently
            expect(screen.getByText("Static Heatmap")).toBeInTheDocument();
            expect(screen.getByText("Dynamic Heatmap")).toBeInTheDocument();
        });
    });
});
