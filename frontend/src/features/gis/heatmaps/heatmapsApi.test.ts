import { describe, expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/test/msw/server";
import {
    deleteStaticHeatmap,
    extractHeatmapErrorMessage,
    getDynamicHeatmapPoints,
    listDynamicHeatmapMetrics,
    listStaticHeatmaps,
    setDefaultStaticHeatmap,
    uploadStaticHeatmap,
} from "@/features/gis/heatmaps/heatmapsApi";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

const STATIC_HEATMAP = {
    id: "11111111-1111-1111-1111-111111111111",
    name: "Picking Density",
    sourceFilename: "picking-q1.tif",
    geoserverLayerName: "acme_picking_density",
    isDefault: true,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
};

const DYNAMIC_METRIC = {
    key: "quantity_sum",
    label: "Quantity Sum",
    description: "Sum of quantities per location",
    unit: null,
};

describe("heatmapsApi", () => {
    describe("listStaticHeatmaps", () => {
        it("sends tenant header and returns list", async () => {
            let capturedHeader: string | null = null;
            server.use(
                http.get(`${API_URL}/acme/gis/heatmaps/static`, ({ request }) => {
                    capturedHeader = request.headers.get("x-tenant-id");
                    return HttpResponse.json([STATIC_HEATMAP]);
                })
            );

            const result = await listStaticHeatmaps("acme");
            expect(capturedHeader).toBe("acme");
            expect(result).toHaveLength(1);
            expect(result[0].geoserverLayerName).toBe("acme_picking_density");
        });
    });

    describe("uploadStaticHeatmap", () => {
        it("sends multipart form data and returns created record", async () => {
            let capturedName: string | null = null;
            server.use(
                http.post(`${API_URL}/acme/gis/heatmaps/static`, async ({ request }) => {
                    const body = await request.formData();
                    capturedName = body.get("name") as string;
                    return HttpResponse.json(STATIC_HEATMAP, { status: 201 });
                })
            );

            const file = new File([""], "test.tif", { type: "image/tiff" });
            const result = await uploadStaticHeatmap("acme", "Picking Density", file);
            expect(capturedName).toBe("Picking Density");
            expect(result.id).toBe(STATIC_HEATMAP.id);
        });
    });

    describe("setDefaultStaticHeatmap", () => {
        it("sends PUT request to default endpoint and returns updated record", async () => {
            const id = STATIC_HEATMAP.id;
            server.use(
                http.put(`${API_URL}/acme/gis/heatmaps/static/${id}/default`, () => {
                    return HttpResponse.json({ ...STATIC_HEATMAP, isDefault: true });
                })
            );

            const result = await setDefaultStaticHeatmap("acme", id);
            expect(result.isDefault).toBe(true);
        });
    });

    describe("deleteStaticHeatmap", () => {
        it("sends DELETE request and resolves on 204", async () => {
            const id = STATIC_HEATMAP.id;
            server.use(
                http.delete(`${API_URL}/acme/gis/heatmaps/static/${id}`, () => {
                    return new HttpResponse(null, { status: 204 });
                })
            );

            await expect(deleteStaticHeatmap("acme", id)).resolves.toBeUndefined();
        });
    });

    describe("listDynamicHeatmapMetrics", () => {
        it("returns metrics preserving null unit", async () => {
            server.use(
                http.get(`${API_URL}/acme/gis/heatmaps/dynamic/metrics`, () => {
                    return HttpResponse.json([DYNAMIC_METRIC]);
                })
            );

            const result = await listDynamicHeatmapMetrics("acme");
            expect(result).toHaveLength(1);
            expect(result[0].key).toBe("quantity_sum");
            expect(result[0].unit).toBeNull();
        });
    });

    describe("getDynamicHeatmapPoints", () => {
        it("sends metric query param and returns GeoJSON", async () => {
            let capturedMetric: string | null = null;
            server.use(
                http.get(`${API_URL}/acme/gis/heatmaps/dynamic/points`, ({ request }) => {
                    capturedMetric = new URL(request.url).searchParams.get("metric");
                    return HttpResponse.json({
                        type: "FeatureCollection",
                        features: [
                            {
                                type: "Feature",
                                geometry: { type: "Point", coordinates: [1.0, 2.0] },
                                properties: {
                                    locationId: "loc-1",
                                    label: "A-01",
                                    positionPath: "A > 01",
                                    metricKey: "quantity_sum",
                                    weight: 0.8,
                                    rawValue: 42,
                                },
                            },
                        ],
                    });
                })
            );

            const result = await getDynamicHeatmapPoints("acme", "quantity_sum");
            expect(capturedMetric).toBe("quantity_sum");
            expect(result.type).toBe("FeatureCollection");
            expect(result.features).toHaveLength(1);
            expect(result.features[0].properties.rawValue).toBe(42);
        });
    });

    describe("extractHeatmapErrorMessage", () => {
        it("extracts message from backend error response", async () => {
            server.use(
                http.get(`${API_URL}/acme/gis/heatmaps/static`, () => {
                    return HttpResponse.json({ message: "Heatmap not found" }, { status: 404 });
                })
            );

            let thrownError: unknown;
            try {
                await listStaticHeatmaps("acme");
            } catch (e) {
                thrownError = e;
            }

            expect(extractHeatmapErrorMessage(thrownError)).toBe("Heatmap not found");
        });

        it("returns null for non-axios errors", () => {
            expect(extractHeatmapErrorMessage(new Error("regular error"))).toBeNull();
        });
    });
});
