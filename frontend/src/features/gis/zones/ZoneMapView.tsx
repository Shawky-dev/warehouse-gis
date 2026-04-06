import { useEffect, useRef, useState } from "react";
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
import { getZoneColor } from "./zoneTypeColors";
import type {
    GeoJsonFeatureCollection,
    ZoneFeatureProps,
    LocationFeatureProps,
} from "./zonesApi";

esriConfig.assetsPath = `${import.meta.env.BASE_URL}assets`;

// ── Props ─────────────────────────────────────────────────────────────────────

interface ZoneMapViewProps {
    svgContent: string;
    anchorLon: number;
    anchorLat: number;
    widthMeters: number;
    lengthMeters: number;
    zonesGeoJson: GeoJsonFeatureCollection<ZoneFeatureProps> | null;
    locationsGeoJson: GeoJsonFeatureCollection<LocationFeatureProps> | null;
    selectedZoneId?: string | null;
    onZoneSelect: (zoneId: string, props: ZoneFeatureProps) => void;
    onDeselect: () => void;
    /** When set, renders a draw toolbar to sketch a new zone polygon. */
    drawPending?: boolean;
    onDrawComplete?: (coordinates: number[][][]) => void;
    onDrawCancel?: () => void;
    /** When set, the zone with this id is moved to the sketch layer for repositioning */
    editingZoneId?: string | null;
    onMoveComplete?: (zoneId: string, rings: number[][][]) => void;
    onMoveCanceled?: () => void;
}

const LOCATION_FILL: [number, number, number, number] = [107, 114, 128, 0.12];
const LOCATION_STROKE = "#9ca3af";

function getZoneFillColor(name: string | null | undefined, selected: boolean): [number, number, number, number] {
    const color = getZoneColor(name);
    const match = color.fill.match(/rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)/);
    const r = match ? parseInt(match[1]) : 107;
    const g = match ? parseInt(match[2]) : 114;
    const b = match ? parseInt(match[3]) : 128;
    return [r, g, b, selected ? 0.35 : 0.15];
}

// ── Component ─────────────────────────────────────────────────────────────────

export function ZoneMapView({
    svgContent,
    anchorLon,
    anchorLat,
    widthMeters,
    lengthMeters,
    zonesGeoJson,
    locationsGeoJson,
    selectedZoneId,
    onZoneSelect,
    onDeselect,
    drawPending,
    onDrawComplete,
    onDrawCancel,
    editingZoneId,
    onMoveComplete,
    onMoveCanceled,
}: ZoneMapViewProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const mapViewRef = useRef<MapView | null>(null);
    const sketchVMRef = useRef<Sketch | null>(null);
    const sketchLayerRef = useRef<GraphicsLayer | null>(null);
    const zoneLayerRef = useRef<GraphicsLayer | null>(null);
    const locationLayerRef = useRef<GraphicsLayer | null>(null);

    const selectedZoneIdRef = useRef<string | null | undefined>(selectedZoneId);
    const onZoneSelectRef = useRef(onZoneSelect);
    const onDeselectRef = useRef(onDeselect);
    const onDrawCompleteRef = useRef(onDrawComplete);
    const onDrawCancelRef = useRef(onDrawCancel);
    const onMoveCompleteRef = useRef(onMoveComplete);
    const onMoveCanceledRef = useRef(onMoveCanceled);

    const movingZoneRef = useRef<{ zoneId: string } | null>(null);

    const [renderError, setRenderError] = useState<string | null>(null);

    useEffect(() => { onZoneSelectRef.current = onZoneSelect; }, [onZoneSelect]);
    useEffect(() => { onDeselectRef.current = onDeselect; }, [onDeselect]);
    useEffect(() => { onDrawCompleteRef.current = onDrawComplete; }, [onDrawComplete]);
    useEffect(() => { onDrawCancelRef.current = onDrawCancel; }, [onDrawCancel]);
    useEffect(() => { onMoveCompleteRef.current = onMoveComplete; }, [onMoveComplete]);
    useEffect(() => { onMoveCanceledRef.current = onMoveCanceled; }, [onMoveCanceled]);
    useEffect(() => { selectedZoneIdRef.current = selectedZoneId; }, [selectedZoneId]);

    // ── Build map on mount ────────────────────────────────────────────────────
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
        });

        const zoneLayer = new GraphicsLayer({ id: "gis-zone-layer", title: "Zones" });
        const locationLayer = new GraphicsLayer({ id: "gis-location-layer", title: "Locations" });
        const sketchLayer = new GraphicsLayer({ id: "gis-zone-sketch", title: "Sketch" });

        zoneLayerRef.current = zoneLayer;
        locationLayerRef.current = locationLayer;
        sketchLayerRef.current = sketchLayer;

        const esriMap = new EsriMap({
            layers: [mediaLayer, locationLayer, zoneLayer, sketchLayer],
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
                const rings = (event.graphic.geometry as Polygon).rings;
                sketchLayer.remove(event.graphic);
                onDrawCompleteRef.current?.(rings);
            } else if (event.state === "cancel") {
                onDrawCancelRef.current?.();
            }
        });

        sketch.on("update", (event) => {
            const meta = movingZoneRef.current;
            if (!meta) return;
            if ((event.state as string) === "complete" && !event.aborted) {
                const movedGraphic = event.graphics[0];
                if (!movedGraphic?.geometry) return;
                const geomJson = movedGraphic.geometry.toJSON() as { rings?: number[][][] };
                const newRings = geomJson.rings ?? [];
                sketchLayer.remove(movedGraphic);
                zoneLayer.add(movedGraphic);
                movingZoneRef.current = null;
                onMoveCompleteRef.current?.(meta.zoneId, newRings);
            } else if ((event.state as string) === "complete" && event.aborted) {
                const graphic = event.graphics[0];
                if (graphic) { sketchLayer.remove(graphic); zoneLayer.add(graphic); }
                movingZoneRef.current = null;
                onMoveCanceledRef.current?.();
            }
        });

        async function init() {
            setRenderError(null);
            await mapView.when();
            await mapView.goTo(warehouseExtent.expand(1.02), { animate: false });

            mapView.on("click", async (clickEvent) => {
                const result = await mapView.hitTest(clickEvent, { include: [zoneLayer] });
                const hit = result.results
                    .filter((r) => r.type === "graphic")
                    .map((r) => (r as { graphic: Graphic }).graphic)
                    .find((g) => g.geometry?.type === "polygon" && g.attributes?.zoneId);

                if (hit) {
                    onZoneSelectRef.current(
                        hit.attributes.zoneId as string,
                        hit.attributes.zoneProps as ZoneFeatureProps
                    );
                    const extent = (hit.geometry as Polygon).extent;
                    if (extent) {
                        void mapView.goTo(extent.expand(3), { animate: true, duration: 400 });
                    }
                } else {
                    onDeselectRef.current();
                }
            });
        }

        init().catch((err) => {
            const message = err instanceof Error ? err.message : "Failed to render map.";
            setRenderError(message);
            console.error("ZoneMapView init error", err);
        });

        return () => {
            sketch.destroy();
            sketchVMRef.current = null;
            sketchLayerRef.current = null;
            zoneLayerRef.current = null;
            locationLayerRef.current = null;
            mapView.destroy();
            mapViewRef.current = null;
        };
    }, [svgContent, anchorLon, anchorLat, widthMeters, lengthMeters]);

    // ── Effect: activate / deactivate draw mode ───────────────────────────────
    useEffect(() => {
        const sketchVM = sketchVMRef.current;
        if (!sketchVM) return;
        if (drawPending) {
            void sketchVM.create("polygon");
        } else {
            sketchVM.cancel();
        }
    }, [drawPending]);
    // ── Effect: activate / deactivate move-zone edit mode ────────────────────
    useEffect(() => {
        const sketch = sketchVMRef.current;
        const sketchLayer = sketchLayerRef.current;
        const zoneLayer = zoneLayerRef.current;
        if (!sketch || !sketchLayer || !zoneLayer) return;
        if (!editingZoneId) {
            // If there's an active move in progress, cancel it
            if (movingZoneRef.current) sketch.cancel();
            return;
        }
        const targetGraphic = zoneLayer.graphics.toArray()
            .find((g: Graphic) => g.attributes?.zoneId === editingZoneId);
        if (!targetGraphic) return;
        movingZoneRef.current = { zoneId: editingZoneId };
        zoneLayer.remove(targetGraphic);
        sketchLayer.add(targetGraphic);
        void sketch.update(targetGraphic);
    }, [editingZoneId]);
    // ── Effect: sync zone graphics when data changes ───────────────────────────
    useEffect(() => {
        const layer = zoneLayerRef.current;
        if (!layer) return;
        layer.graphics.removeAll();

        const currentSelectedId = selectedZoneIdRef.current;

        for (const feature of zonesGeoJson?.features ?? []) {
            const props = feature.properties;
            const color = getZoneColor(props.name);
            const rings = (feature.geometry as { coordinates: number[][][] }).coordinates;
            const polygon = new Polygon({ rings, spatialReference: { wkid: 4326 } });
            const isSelected = props.id === currentSelectedId;

            layer.add(new Graphic({
                geometry: polygon,
                symbol: new SimpleFillSymbol({
                    color: getZoneFillColor(props.name, isSelected),
                    outline: { color: color.stroke, width: isSelected ? 3 : 2 },
                }),
                attributes: { zoneId: props.id, zoneProps: props },
            }));

            const centroid = polygon.centroid;
            if (centroid) {
                layer.add(new Graphic({
                    geometry: centroid,
                    symbol: new TextSymbol({
                        text: props.name,
                        color: color.stroke,
                        haloColor: "white",
                        haloSize: "1.5px",
                        font: { size: 11, weight: "bold" },
                        horizontalAlignment: "center",
                        verticalAlignment: "middle",
                    }),
                    attributes: { isLabel: true, zoneId: props.id },
                }));
            }
        }
    }, [zonesGeoJson]);

    // ── Effect: sync location graphics ────────────────────────────────────────
    useEffect(() => {
        const layer = locationLayerRef.current;
        if (!layer) return;
        layer.graphics.removeAll();

        for (const feature of locationsGeoJson?.features ?? []) {
            const rings = (feature.geometry as { coordinates: number[][][] }).coordinates;
            layer.add(new Graphic({
                geometry: new Polygon({ rings, spatialReference: { wkid: 4326 } }),
                symbol: new SimpleFillSymbol({
                    color: LOCATION_FILL,
                    outline: { color: LOCATION_STROKE, width: 1 },
                }),
                attributes: { locationId: feature.properties.locationId },
            }));
        }
    }, [locationsGeoJson]);

    // ── Effect: highlight selected zone ───────────────────────────────────────
    useEffect(() => {
        const layer = zoneLayerRef.current;
        if (!layer) return;

        for (const g of layer.graphics.toArray()) {
            if (g.geometry?.type === "polygon" && g.attributes?.zoneId) {
                const isSelected = g.attributes.zoneId === selectedZoneId;
                const color = getZoneColor(g.attributes.zoneProps?.name as string | undefined);
                g.symbol = new SimpleFillSymbol({
                    color: getZoneFillColor(g.attributes.zoneProps?.name as string | undefined, isSelected),
                    outline: { color: color.stroke, width: isSelected ? 3 : 2 },
                });
            }
        }
    }, [selectedZoneId]);

    return (
        <div className="relative h-full w-full">
            <div ref={containerRef} className="h-full w-full overflow-hidden" />
            {renderError ? (
                <div className="absolute inset-x-4 top-4 rounded-md border bg-background/95 px-3 py-2 text-sm text-destructive shadow-sm">
                    {renderError}
                </div>
            ) : null}
        </div>
    );
}
