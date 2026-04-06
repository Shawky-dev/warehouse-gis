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
import { ArcgisSketchOverlay } from "../ArcgisSketchOverlay";
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
}

const LOCATION_FILL = "rgba(107, 114, 128, 0.12)";
const LOCATION_STROKE = "#9ca3af";

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
}: ZoneMapViewProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const viewRef = useRef<MapView | null>(null);
    const zoneLayerRef = useRef<GraphicsLayer | null>(null);
    const locationLayerRef = useRef<GraphicsLayer | null>(null);
    const selectedGraphicRef = useRef<Graphic | null>(null);
    const selectedZoneIdRef = useRef<string | null | undefined>(selectedZoneId);
    const onZoneSelectRef = useRef(onZoneSelect);
    const onDeselectRef = useRef(onDeselect);
    const [sketchLayer, setSketchLayer] = useState<GraphicsLayer | null>(null);
    const [sketchView, setSketchView] = useState<MapView | null>(null);
    const [renderError, setRenderError] = useState<string | null>(null);

    useEffect(() => { onZoneSelectRef.current = onZoneSelect; }, [onZoneSelect]);
    useEffect(() => { onDeselectRef.current = onDeselect; }, [onDeselect]);
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

        const svgDataUri = `data:image/svg+xml,${encodeURIComponent(svgContent)}`;
        const mediaLayer = new MediaLayer({
            source: [
                new ImageElement({
                    image: svgDataUri,
                    georeference: new ExtentAndRotationGeoreference({ extent: warehouseExtent }),
                }),
            ],
            title: "Warehouse floor plan",
        });

        const zoneLayer = new GraphicsLayer({ id: "gis-zone-layer", title: "Zones" });
        const locationLayer = new GraphicsLayer({ id: "gis-location-layer", title: "Locations" });

        zoneLayerRef.current = zoneLayer;
        locationLayerRef.current = locationLayer;

        const esriMap = new EsriMap({
            layers: [mediaLayer, locationLayer, zoneLayer],
        });

        const view = new MapView({
            container: containerRef.current,
            map: esriMap,
            extent: warehouseExtent,
            spatialReference: { wkid: 4326 },
            background: { color: [248, 247, 244, 1] },
            ui: { components: ["zoom"] },
            constraints: { minScale: 5000 },
            popupEnabled: false,
        });
        viewRef.current = view;
        setSketchView(view);

        let cancelled = false;

        void (async () => {
            try {
                setRenderError(null);
                await view.when();
                if (cancelled) return;
                await view.goTo(warehouseExtent.expand(1.02), { animate: false });

                view.on("click", async (clickEvent) => {
                    const result = await view.hitTest(clickEvent);
                    const hit = result.results
                        .filter((r) => r.type === "graphic")
                        .map((r) => (r as { graphic: Graphic }).graphic)
                        .find(
                            (g) =>
                                g.attributes?.zoneId &&
                                !g.attributes?.isLabel &&
                                g.layer?.id === "gis-zone-layer"
                        );

                    if (hit) {
                        const hitGeometry = hit.geometry;
                        onZoneSelectRef.current(
                            hit.attributes.zoneId as string,
                            hit.attributes.zoneProps as ZoneFeatureProps
                        );
                        if (hitGeometry?.type === "polygon") {
                            const extent = (hitGeometry as Polygon).extent;
                            if (extent) {
                                void view.goTo(extent.expand(3), { animate: true, duration: 400 });
                            }
                        }
                    } else {
                        onDeselectRef.current();
                    }
                });
            } catch (err) {
                if (!cancelled) {
                    const message = err instanceof Error ? err.message : "Failed to render map.";
                    setRenderError(message);
                    console.error("ZoneMapView render error", err);
                }
            }
        })();

        return () => {
            cancelled = true;
            setSketchLayer(null);
            setSketchView(null);
            zoneLayerRef.current = null;
            locationLayerRef.current = null;
            selectedGraphicRef.current = null;
            view.destroy();
            viewRef.current = null;
        };
    }, [svgContent, anchorLon, anchorLat, widthMeters, lengthMeters]);

    // ── Sketch (draw mode) ────────────────────────────────────────────────────
    useEffect(() => {
        if (!drawPending || !viewRef.current || !zoneLayerRef.current) return;
        const tempLayer = new GraphicsLayer({ id: "sketch-temp" });
        viewRef.current.map?.add(tempLayer);
        setSketchLayer(tempLayer);

        return () => {
            setSketchLayer(null);
            viewRef.current?.map?.remove(tempLayer);
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [drawPending]);

    // ── Sync zone graphics when data changes ──────────────────────────────────
    useEffect(() => {
        const layer = zoneLayerRef.current;
        if (!layer) return;
        layer.graphics.removeAll();
        selectedGraphicRef.current = null;

        for (const feature of zonesGeoJson?.features ?? []) {
            const props = feature.properties;
            const color = getZoneColor(props.name);
            const rings = (feature.geometry as { coordinates: number[][][] }).coordinates;
            const polygon = new Polygon({ rings, spatialReference: { wkid: 4326 } });

            const isSelected = props.id === selectedZoneIdRef.current;
            const fillColor = isSelected ? color.fill.replace(", 0.15)", ", 0.35)") : color.fill;

            const graphic = new Graphic({
                geometry: polygon,
                symbol: new SimpleFillSymbol({
                    color: fillColor,
                    outline: { color: color.stroke, width: isSelected ? 3 : 2 },
                }),
                attributes: {
                    zoneId: props.id,
                    zoneProps: props,
                },
            });
            layer.graphics.add(graphic);
            if (isSelected) selectedGraphicRef.current = graphic;

            const centroid = polygon.centroid;
            if (centroid) {
                layer.graphics.add(
                    new Graphic({
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
                    })
                );
            }
        }
    }, [zonesGeoJson]);

    // ── Sync location graphics ────────────────────────────────────────────────
    useEffect(() => {
        const layer = locationLayerRef.current;
        if (!layer) return;
        layer.graphics.removeAll();

        for (const feature of locationsGeoJson?.features ?? []) {
            const rings = (feature.geometry as { coordinates: number[][][] }).coordinates;
            layer.graphics.add(
                new Graphic({
                    geometry: new Polygon({ rings, spatialReference: { wkid: 4326 } }),
                    symbol: new SimpleFillSymbol({
                        color: LOCATION_FILL,
                        outline: { color: LOCATION_STROKE, width: 1 },
                    }),
                    attributes: { locationId: feature.properties.locationId },
                })
            );
        }
    }, [locationsGeoJson]);

    // ── Highlight selected zone ───────────────────────────────────────────────
    useEffect(() => {
        const layer = zoneLayerRef.current;
        if (!layer) return;

        if (selectedGraphicRef.current) {
            const prev = selectedGraphicRef.current;
            const prevProps = prev.attributes?.zoneProps as ZoneFeatureProps | undefined;
            const color = getZoneColor(prevProps?.name);
            prev.symbol = new SimpleFillSymbol({
                color: color.fill,
                outline: { color: color.stroke, width: 2 },
            });
            selectedGraphicRef.current = null;
        }

        if (!selectedZoneId) return;

        const found = layer.graphics
            .toArray()
            .find(
                (g: Graphic) =>
                    g.attributes?.zoneId === selectedZoneId && !g.attributes?.isLabel
            );

        if (found) {
            const props = found.attributes?.zoneProps as ZoneFeatureProps | undefined;
            const color = getZoneColor(props?.name);
            found.symbol = new SimpleFillSymbol({
                color: color.fill.replace(", 0.15)", ", 0.35)"),
                outline: { color: color.stroke, width: 3 },
            });
            selectedGraphicRef.current = found;
        }
    }, [selectedZoneId]);

    return (
        <div className="relative h-full w-full">
            <div ref={containerRef} className="h-full w-full overflow-hidden" />
            <ArcgisSketchOverlay
                active={Boolean(drawPending && sketchView && sketchLayer)}
                className="absolute right-4 top-4 z-10 rounded-md border bg-background/95 shadow-sm"
                layer={sketchLayer}
                view={sketchView}
                onCancel={onDrawCancel}
                onCreateComplete={(graphic) => {
                    const ring = (graphic.geometry as Polygon).rings;
                    onDrawComplete?.(ring);
                }}
            />
            {renderError ? (
                <div className="absolute inset-x-4 top-4 rounded-md border bg-background/95 px-3 py-2 text-sm text-destructive shadow-sm">
                    {renderError}
                </div>
            ) : null}
        </div>
    );
}
