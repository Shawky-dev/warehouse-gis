import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from "react";
import esriConfig from "@arcgis/core/config.js";
import Extent from "@arcgis/core/geometry/Extent.js";
import Polygon from "@arcgis/core/geometry/Polygon.js";
import Graphic from "@arcgis/core/Graphic.js";
import GraphicsLayer from "@arcgis/core/layers/GraphicsLayer.js";
import MediaLayer from "@arcgis/core/layers/MediaLayer.js";
import ImageElement from "@arcgis/core/layers/support/ImageElement.js";
import ExtentAndRotationGeoreference from "@arcgis/core/layers/support/ExtentAndRotationGeoreference.js";
import EsriMap from "@arcgis/core/Map.js";
import SimpleFillSymbol from "@arcgis/core/symbols/SimpleFillSymbol.js";
import TextSymbol from "@arcgis/core/symbols/TextSymbol.js";
import MapView from "@arcgis/core/views/MapView.js";
import Sketch from "@arcgis/core/widgets/Sketch.js";
import { getTemplateColor } from "./templateColors";
import { resolveZoneDisplayColor } from "../zones/zoneTypeColors";
import type { ExistingPolygon, EditorTemplate } from "./useEditorState";
import type { GeoJsonFeatureCollection, ZoneFeatureProps } from "../zones/zonesApi";
import type { HazardBufferFeatureProps } from "@/features/tenant/types/gis";

esriConfig.assetsPath = `${import.meta.env.BASE_URL}assets`;

function getLabelFontSize(depth: number): number {
    if (depth <= 0) return 15;
    if (depth === 1) return 13;
    if (depth === 2) return 11;
    return 9;
}

function getFillSymbol(templateName: string, selected: boolean): SimpleFillSymbol {
    const color = getTemplateColor(templateName);
    const match = color.fill.match(/rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)/);
    const r = match ? parseInt(match[1]) : 139;
    const g = match ? parseInt(match[2]) : 92;
    const b = match ? parseInt(match[3]) : 246;
    return new SimpleFillSymbol({
        color: [r, g, b, selected ? 0.35 : 0.15],
        outline: { color: color.stroke, width: selected ? 3 : 2 },
    });
}

export interface WarehouseMapViewHandle {
    cancelPendingPolygon: () => void;
}

interface WarehouseMapViewProps {
    svgContent: string;
    anchorLon: number;
    anchorLat: number;
    widthMeters: number;
    lengthMeters: number;
    editorMode?: boolean;
    templates?: EditorTemplate[];
    activeTemplateName?: string | null;
    onPolygonComplete?: (rings: number[][][], templateName: string) => void;
    onPolygonSelect?: (gisBlockId: string | null, templateName: string | null, label: string | null) => void;
    selectedGisBlockId?: string | null;
    visibilityByTemplate?: Record<string, boolean>;
    existingPolygons?: ExistingPolygon[];
    svgVisible?: boolean;
    editingGisBlockId?: string | null;
    onEditComplete?: (gisBlockId: string, rings: number[][][]) => void;
    onEditCancel?: () => void;
    zonesGeoJson?: GeoJsonFeatureCollection<ZoneFeatureProps> | null;
    hazardBuffersGeoJson?: GeoJsonFeatureCollection<HazardBufferFeatureProps> | null;
    highlightAreaIds?: string[];
    zonesLayerVisible?: boolean;
    hazardBuffersLayerVisible?: boolean;
}

export const WarehouseMapView = forwardRef<WarehouseMapViewHandle, WarehouseMapViewProps>(
    function WarehouseMapView(
        {
            svgContent,
            anchorLon,
            anchorLat,
            widthMeters,
            lengthMeters,
            editorMode,
            templates,
            activeTemplateName,
            onPolygonComplete,
            onPolygonSelect,
            selectedGisBlockId,
            visibilityByTemplate,
            existingPolygons,
            svgVisible,
            editingGisBlockId,
            onEditComplete,
            onEditCancel,
            zonesGeoJson,
            hazardBuffersGeoJson,
            highlightAreaIds,
            zonesLayerVisible,
            hazardBuffersLayerVisible,
        },
        ref
    ) {
        const containerRef = useRef<HTMLDivElement>(null);
        const mapViewRef = useRef<MapView | null>(null);
        const sketchVMRef = useRef<Sketch | null>(null);
        const sketchLayerRef = useRef<GraphicsLayer | null>(null);
        const layersByTemplateRef = useRef<Map<string, GraphicsLayer>>(new Map());
        const viewerZonesLayerRef = useRef<GraphicsLayer | null>(null);
        const viewerHazardBuffersLayerRef = useRef<GraphicsLayer | null>(null);
        const viewerHighlightLayerRef = useRef<GraphicsLayer | null>(null);
        const svgLayerRef = useRef<MediaLayer | null>(null);

        const activeTemplateNameRef = useRef<string | null>(activeTemplateName ?? null);
        const selectedGisBlockIdRef = useRef<string | null | undefined>(selectedGisBlockId);
        const onPolygonCompleteRef = useRef(onPolygonComplete);
        const onPolygonSelectRef = useRef(onPolygonSelect);
        const onEditCompleteRef = useRef(onEditComplete);
        const onEditCancelRef = useRef(onEditCancel);

        const editingMetaRef = useRef<{
            graphic: Graphic;
            gisBlockId: string;
            templateName: string;
            originalRings: number[][][];
        } | null>(null);

        const [renderError, setRenderError] = useState<string | null>(null);

        useEffect(() => {
            onPolygonCompleteRef.current = onPolygonComplete;
        }, [onPolygonComplete]);
        useEffect(() => {
            onPolygonSelectRef.current = onPolygonSelect;
        }, [onPolygonSelect]);
        useEffect(() => {
            onEditCompleteRef.current = onEditComplete;
        }, [onEditComplete]);
        useEffect(() => {
            onEditCancelRef.current = onEditCancel;
        }, [onEditCancel]);
        useEffect(() => {
            activeTemplateNameRef.current = activeTemplateName ?? null;
        }, [activeTemplateName]);
        useEffect(() => {
            selectedGisBlockIdRef.current = selectedGisBlockId;
        }, [selectedGisBlockId]);

        useImperativeHandle(ref, () => ({
            cancelPendingPolygon() {
                sketchVMRef.current?.cancel();
            },
        }));

        useEffect(() => {
            if (!containerRef.current) return;

            const lonSpan = widthMeters / 111_000;
            const latSpan = lengthMeters / 111_000;
            const warehouseExtent = new Extent({
                xmin: anchorLon,
                ymin: anchorLat,
                xmax: anchorLon + lonSpan,
                ymax: anchorLat + latSpan,
                spatialReference: { wkid: 4326 },
            });

            const mediaLayer = new MediaLayer({
                source: [
                    new ImageElement({
                        image: `data:image/svg+xml,${encodeURIComponent(svgContent)}`,
                        georeference: new ExtentAndRotationGeoreference({ extent: warehouseExtent }),
                    }),
                ],
                title: "Warehouse floor plan",
                visible: svgVisible ?? true,
            });
            svgLayerRef.current = mediaLayer;

            const layers = new Map<string, GraphicsLayer>();
            for (const tpl of templates ?? []) {
                layers.set(tpl.name, new GraphicsLayer({ id: `gis-layer-${tpl.name}`, title: tpl.name }));
            }
            layersByTemplateRef.current = layers;

            const sketchLayer = new GraphicsLayer({ id: "gis-sketch", title: "Sketch" });
            sketchLayerRef.current = sketchLayer;

            const viewerZonesLayer = new GraphicsLayer({ id: "viewer-zones", title: "Zones", visible: zonesLayerVisible ?? true });
            const viewerHazardBuffersLayer = new GraphicsLayer({ id: "viewer-hazard-buffers", title: "Hazard Buffers", visible: hazardBuffersLayerVisible ?? true });
            const viewerHighlightLayer = new GraphicsLayer({ id: "viewer-highlight", title: "Highlight" });
            viewerZonesLayerRef.current = viewerZonesLayer;
            viewerHazardBuffersLayerRef.current = viewerHazardBuffersLayer;
            viewerHighlightLayerRef.current = viewerHighlightLayer;

            const esriMap = new EsriMap({
                layers: [mediaLayer, viewerZonesLayer, viewerHazardBuffersLayer, ...Array.from(layers.values()), sketchLayer, viewerHighlightLayer],
            });

            const mapView = new MapView({
                container: containerRef.current,
                map: esriMap,
                extent: warehouseExtent,
                spatialReference: { wkid: 4326 },
                background: { color: [248, 247, 244, 1] },
                ui: { components: ["zoom"] },
                constraints: { minScale: 5000 },
                popupEnabled: false,
            });
            mapViewRef.current = mapView;

            async function init() {
                setRenderError(null);
                await mapView.when();
                await mapView.goTo(warehouseExtent.expand(1.02), { animate: false });

                if (editorMode) {
                    const sketch = new Sketch({
                        view: mapView,
                        layer: sketchLayer,
                        creationMode: "single",
                        visibleElements: {
                            createTools: { point: false, polyline: false },
                            selectionTools: { "lasso-selection": false, "rectangle-selection": false },
                            undoRedoMenu: true,
                            settingsMenu: false,
                        },
                    });
                    sketch.viewModel.updateOnGraphicClick = false;
                    mapView.ui.add(sketch, "top-right");
                    sketchVMRef.current = sketch;

                    sketch.on("create", (event) => {
                        if (event.state === "complete" && event.graphic?.geometry) {
                            const tplName = activeTemplateNameRef.current ?? "";
                            const geomJson = event.graphic.geometry.toJSON() as { rings?: number[][][] };
                            const rings = geomJson.rings ?? [];
                            sketchLayer.remove(event.graphic);
                            onPolygonCompleteRef.current?.(rings, tplName);
                        }
                    });

                    sketch.on("update", (event) => {
                        const meta = editingMetaRef.current;
                        if (!meta) return;

                        if (event.state === "complete") {
                            const updatedGraphic = event.graphics[0];
                            if (!updatedGraphic?.geometry) return;

                            const geomJson = updatedGraphic.geometry.toJSON() as { rings?: number[][][] };
                            const newRings = geomJson.rings ?? [];

                            sketchLayer.remove(updatedGraphic);
                            const templateLayer = layersByTemplateRef.current.get(meta.templateName);
                            if (templateLayer) {
                                const isSelected = selectedGisBlockIdRef.current === meta.gisBlockId;
                                updatedGraphic.symbol = getFillSymbol(meta.templateName, isSelected);
                                templateLayer.add(updatedGraphic);

                                const updatedPolygon = updatedGraphic.geometry as Polygon;
                                const centroid = updatedPolygon.centroid;
                                if (centroid) {
                                    const oldLabel = templateLayer.graphics.toArray()
                                        .find((g: Graphic) => g.attributes?.isLabel && g.attributes?.gisBlockId === meta.gisBlockId);
                                    if (oldLabel) templateLayer.remove(oldLabel);

                                    const depth = (updatedGraphic.attributes?.depth as number | undefined) ?? 0;
                                    templateLayer.add(new Graphic({
                                        geometry: centroid,
                                        symbol: new TextSymbol({
                                            text: updatedGraphic.attributes?.label as string ?? "",
                                            color: getTemplateColor(meta.templateName).stroke,
                                            haloColor: "white",
                                            haloSize: "1.5px",
                                            font: { size: getLabelFontSize(depth), weight: "bold" },
                                            horizontalAlignment: "center",
                                            verticalAlignment: "middle",
                                        }),
                                        attributes: { isLabel: true, gisBlockId: meta.gisBlockId },
                                    }));
                                }
                            }

                            editingMetaRef.current = null;
                            onEditCompleteRef.current?.(meta.gisBlockId, newRings);
                        } else if ((event.state as string) === "complete" && event.aborted) {
                            const cancelledGraphic = event.graphics[0];
                            if (cancelledGraphic) {
                                sketchLayer.remove(cancelledGraphic);
                                const templateLayer = layersByTemplateRef.current.get(meta.templateName);
                                if (templateLayer) {
                                    const restoredPolygon = new Polygon({
                                        rings: meta.originalRings,
                                        spatialReference: { wkid: 4326 },
                                    });
                                    cancelledGraphic.geometry = restoredPolygon;
                                    const isSelected = selectedGisBlockIdRef.current === meta.gisBlockId;
                                    cancelledGraphic.symbol = getFillSymbol(meta.templateName, isSelected);
                                    templateLayer.add(cancelledGraphic);
                                }
                            }
                            editingMetaRef.current = null;
                            onEditCancelRef.current?.();
                        }
                    });
                }

                if (layers.size > 0) {
                    const templateLayers = Array.from(layers.values());
                    mapView.on("click", async (clickEvent) => {
                        const result = await mapView.hitTest(clickEvent, { include: templateLayers });
                        const hit = result.results
                            .filter((r) => r.type === "graphic")
                            .map((r) => (r as { graphic: Graphic }).graphic)
                            .find((g) => g.geometry?.type === "polygon" && g.attributes?.gisBlockId);

                        if (hit) {
                            onPolygonSelectRef.current?.(
                                hit.attributes.gisBlockId as string,
                                hit.attributes.templateName as string,
                                hit.attributes.label as string
                            );
                            const extent = (hit.geometry as Polygon).extent;
                            if (extent) {
                                void mapView.goTo(extent.expand(3), { animate: true, duration: 400 });
                            }
                        } else {
                            onPolygonSelectRef.current?.(null, null, null);
                        }
                    });
                }
            }

            init().catch((err) => {
                const message = err instanceof Error ? err.message : "Failed to render floor plan.";
                setRenderError(message);
                console.error("WarehouseMapView init error", err);
            });

            return () => {
                sketchVMRef.current?.destroy();
                sketchVMRef.current = null;
                sketchLayerRef.current = null;
                layersByTemplateRef.current = new Map();
                svgLayerRef.current = null;
                editingMetaRef.current = null;
                viewerZonesLayerRef.current = null;
                viewerHazardBuffersLayerRef.current = null;
                viewerHighlightLayerRef.current = null;
                mapView.destroy();
                mapViewRef.current = null;
            };
        }, [svgContent, anchorLon, anchorLat, widthMeters, lengthMeters, editorMode, templates]);

        useEffect(() => {
            const sketch = sketchVMRef.current;
            if (!sketch) return;
            const tplName = activeTemplateName ?? "";
            const color = getTemplateColor(tplName);
            const match = color.fill.match(/rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)/);
            const r = match ? parseInt(match[1]) : 139;
            const g = match ? parseInt(match[2]) : 92;
            const b = match ? parseInt(match[3]) : 246;
            sketch.viewModel.polygonSymbol = new SimpleFillSymbol({
                color: [r, g, b, 0.2],
                outline: { color: color.stroke, width: 2 },
            });
        }, [activeTemplateName]);

        useEffect(() => {
            const sketchVM = sketchVMRef.current;
            const sketchLayer = sketchLayerRef.current;
            if (!sketchVM || !sketchLayer) return;

            if (!editingGisBlockId) {
                if (editingMetaRef.current) {
                    sketchVM.cancel();
                }
                return;
            }

            let targetGraphic: Graphic | null = null;
            let targetTemplateName = "";
            for (const [name, layer] of layersByTemplateRef.current.entries()) {
                const found = layer.graphics.toArray()
                    .find((g: Graphic) => g.geometry?.type === "polygon" && g.attributes?.gisBlockId === editingGisBlockId);
                if (found) {
                    targetGraphic = found;
                    targetTemplateName = name;
                    break;
                }
            }

            if (!targetGraphic) return;

            const templateLayer = layersByTemplateRef.current.get(targetTemplateName);
            if (!templateLayer) return;

            if (!targetGraphic.geometry) return;
            const geomJson = targetGraphic.geometry.toJSON() as { rings?: number[][][] };
            const originalRings = geomJson.rings ?? [];

            editingMetaRef.current = {
                graphic: targetGraphic,
                gisBlockId: editingGisBlockId,
                templateName: targetTemplateName,
                originalRings,
            };

            templateLayer.remove(targetGraphic);
            sketchLayer.add(targetGraphic);
            void sketchVM.update(targetGraphic);
        }, [editingGisBlockId]);

        useEffect(() => {
            if (layersByTemplateRef.current.size === 0) return;

            for (const layer of layersByTemplateRef.current.values()) {
                layer.graphics.removeAll();
            }

            const currentSelectedId = selectedGisBlockIdRef.current;

            for (const ep of existingPolygons ?? []) {
                const layer = layersByTemplateRef.current.get(ep.templateName);
                if (!layer) continue;
                const color = getTemplateColor(ep.templateName);
                const polygon = new Polygon({ rings: ep.rings, spatialReference: { wkid: 4326 } });
                const isSelected = ep.gisBlockId === currentSelectedId;

                layer.add(new Graphic({
                    geometry: polygon,
                    symbol: getFillSymbol(ep.templateName, isSelected),
                    attributes: {
                        gisBlockId: ep.gisBlockId,
                        templateName: ep.templateName,
                        label: ep.label,
                        depth: ep.depth,
                    },
                }));

                const centroid = polygon.centroid;
                if (centroid) {
                    const fontSize = getLabelFontSize(ep.depth);
                    layer.add(new Graphic({
                        geometry: centroid,
                        symbol: new TextSymbol({
                            text: ep.label,
                            color: color.stroke,
                            haloColor: "white",
                            haloSize: "1.5px",
                            font: { size: fontSize, weight: "bold" },
                            horizontalAlignment: "center",
                            verticalAlignment: "middle",
                            xoffset: 0,
                            yoffset: 0,
                        }),
                        attributes: { isLabel: true, gisBlockId: ep.gisBlockId },
                    }));
                }
            }
        }, [existingPolygons]);

        useEffect(() => {
            for (const layer of layersByTemplateRef.current.values()) {
                for (const g of layer.graphics.toArray()) {
                    if (g.geometry?.type === "polygon" && g.attributes?.gisBlockId) {
                        const isSelected = g.attributes.gisBlockId === selectedGisBlockId;
                        g.symbol = getFillSymbol(g.attributes.templateName as string, isSelected);
                    }
                }
            }
        }, [selectedGisBlockId]);

        useEffect(() => {
            if (!visibilityByTemplate) return;
            for (const [name, layer] of layersByTemplateRef.current.entries()) {
                layer.visible = visibilityByTemplate[name] ?? true;
            }
        }, [visibilityByTemplate]);

        useEffect(() => {
            if (svgLayerRef.current) {
                svgLayerRef.current.visible = svgVisible ?? true;
            }
        }, [svgVisible]);

        useEffect(() => {
            const layer = viewerZonesLayerRef.current;
            if (!layer) return;
            layer.graphics.removeAll();
            for (const feature of zonesGeoJson?.features ?? []) {
                const props = feature.properties;
                const { fill, stroke } = resolveZoneDisplayColor(props.displayColor, props.name);
                const rings = (feature.geometry as { coordinates: number[][][] }).coordinates;
                const polygon = new Polygon({ rings, spatialReference: { wkid: 4326 } });
                layer.add(new Graphic({
                    geometry: polygon,
                    symbol: new SimpleFillSymbol({ color: fill, outline: { color: stroke, width: 1.5 } }),
                    attributes: { viewerZoneId: props.id },
                }));
            }
        }, [zonesGeoJson]);

        useEffect(() => {
            const layer = viewerHazardBuffersLayerRef.current;
            if (!layer) return;
            layer.graphics.removeAll();
            for (const feature of hazardBuffersGeoJson?.features ?? []) {
                const rings = (feature.geometry as { coordinates: number[][][] }).coordinates;
                layer.add(new Graphic({
                    geometry: new Polygon({ rings, spatialReference: { wkid: 4326 } }),
                    symbol: new SimpleFillSymbol({
                        color: [220, 38, 38, 0.2],
                        outline: { color: [220, 38, 38], width: 1.5 },
                    }),
                    attributes: { viewerBufferId: feature.properties.id },
                }));
            }
        }, [hazardBuffersGeoJson]);

        useEffect(() => {
            const layer = viewerHighlightLayerRef.current;
            if (!layer) return;
            layer.graphics.removeAll();
            if (!highlightAreaIds?.length) return;
            const allFeatures = [
                ...(zonesGeoJson?.features ?? []),
                ...(hazardBuffersGeoJson?.features ?? []),
            ];
            for (const feature of allFeatures) {
                const id: string = (feature.properties as { id: string }).id;
                if (!highlightAreaIds.includes(id)) continue;
                const rings = (feature.geometry as { coordinates: number[][][] }).coordinates;
                layer.add(new Graphic({
                    geometry: new Polygon({ rings, spatialReference: { wkid: 4326 } }),
                    symbol: new SimpleFillSymbol({
                        color: [250, 204, 21, 0.3],
                        outline: { color: [234, 179, 8], width: 3 },
                    }),
                }));
            }
        }, [highlightAreaIds, zonesGeoJson, hazardBuffersGeoJson]);

        useEffect(() => {
            if (viewerZonesLayerRef.current) {
                viewerZonesLayerRef.current.visible = zonesLayerVisible ?? true;
            }
        }, [zonesLayerVisible]);

        useEffect(() => {
            if (viewerHazardBuffersLayerRef.current) {
                viewerHazardBuffersLayerRef.current.visible = hazardBuffersLayerVisible ?? true;
            }
        }, [hazardBuffersLayerVisible]);

        return (
            <div className="relative">
                <div
                    ref={containerRef}
                    className="w-full overflow-hidden rounded-md border"
                    style={{ height: "600px" }}
                />
                {renderError ? (
                    <div className="absolute inset-x-4 top-4 rounded-md border bg-background/95 px-3 py-2 text-sm text-destructive shadow-sm">
                        {renderError}
                    </div>
                ) : null}
            </div>
        );
    }
);
