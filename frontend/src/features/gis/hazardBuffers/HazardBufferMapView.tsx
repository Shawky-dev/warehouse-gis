import { useEffect, useRef, useState } from "react";
import esriConfig from "@arcgis/core/config.js";
import Extent from "@arcgis/core/geometry/Extent.js";
import Polygon from "@arcgis/core/geometry/Polygon.js";
import * as centroidOperator from "@arcgis/core/geometry/operators/centroidOperator.js";
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
import type { GeoJsonFeatureCollection } from "@/features/gis/zones/zonesApi";
import type { HazardBufferFeatureProps } from "./hazardBuffersApi";

esriConfig.assetsPath = `${import.meta.env.BASE_URL}assets`;

interface HazardBufferMapViewProps {
    svgContent: string;
    anchorLon: number;
    anchorLat: number;
    widthMeters: number;
    lengthMeters: number;
    buffersGeoJson: GeoJsonFeatureCollection<HazardBufferFeatureProps> | null;
    selectedBufferId?: string | null;
    drawPending?: boolean;
    onBufferSelect: (bufferId: string, props: HazardBufferFeatureProps) => void;
    onDeselect: () => void;
    onDrawComplete?: (coordinates: number[][][]) => void;
    onDrawCancel?: () => void;
    editingBufferId?: string | null;
    onMoveComplete?: (bufferId: string, rings: number[][][]) => void;
    onMoveCanceled?: () => void;
}

const HAZARD_FILL: [number, number, number, number] = [220, 38, 38, 0.2];
const HAZARD_FILL_SELECTED: [number, number, number, number] = [220, 38, 38, 0.35];
const HAZARD_STROKE: [number, number, number] = [220, 38, 38];

function symbolForBuffer(selected: boolean) {
    return new SimpleFillSymbol({
        style: "diagonal-cross",
        color: selected ? HAZARD_FILL_SELECTED : HAZARD_FILL,
        outline: { color: HAZARD_STROKE, width: selected ? 3 : 1.5 },
    });
}

export function HazardBufferMapView({
    svgContent,
    anchorLon,
    anchorLat,
    widthMeters,
    lengthMeters,
    buffersGeoJson,
    selectedBufferId,
    drawPending,
    onBufferSelect,
    onDeselect,
    onDrawComplete,
    onDrawCancel,
    editingBufferId,
    onMoveComplete,
    onMoveCanceled,
}: HazardBufferMapViewProps) {
    const containerRef = useRef<HTMLDivElement>(null);
    const mapViewRef = useRef<MapView | null>(null);
    const sketchRef = useRef<Sketch | null>(null);
    const sketchLayerRef = useRef<GraphicsLayer | null>(null);
    const bufferLayerRef = useRef<GraphicsLayer | null>(null);

    const selectedBufferIdRef = useRef<string | null | undefined>(selectedBufferId);
    const onBufferSelectRef = useRef(onBufferSelect);
    const onDeselectRef = useRef(onDeselect);
    const onDrawCompleteRef = useRef(onDrawComplete);
    const onDrawCancelRef = useRef(onDrawCancel);
    const onMoveCompleteRef = useRef(onMoveComplete);
    const onMoveCanceledRef = useRef(onMoveCanceled);
    const movingBufferRef = useRef<{ bufferId: string } | null>(null);

    const [renderError, setRenderError] = useState<string | null>(null);

    useEffect(() => { selectedBufferIdRef.current = selectedBufferId; }, [selectedBufferId]);
    useEffect(() => { onBufferSelectRef.current = onBufferSelect; }, [onBufferSelect]);
    useEffect(() => { onDeselectRef.current = onDeselect; }, [onDeselect]);
    useEffect(() => { onDrawCompleteRef.current = onDrawComplete; }, [onDrawComplete]);
    useEffect(() => { onDrawCancelRef.current = onDrawCancel; }, [onDrawCancel]);
    useEffect(() => { onMoveCompleteRef.current = onMoveComplete; }, [onMoveComplete]);
    useEffect(() => { onMoveCanceledRef.current = onMoveCanceled; }, [onMoveCanceled]);

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

        const bufferLayer = new GraphicsLayer({ id: "gis-hazard-buffer-layer", title: "Hazard Buffers" });
        const sketchLayer = new GraphicsLayer({ id: "gis-hazard-buffer-sketch", title: "Sketch" });
        bufferLayerRef.current = bufferLayer;
        sketchLayerRef.current = sketchLayer;

        const esriMap = new EsriMap({ layers: [mediaLayer, bufferLayer, sketchLayer] });
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
        sketchRef.current = sketch;

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
            const meta = movingBufferRef.current;
            if (!meta) return;
            if ((event.state as string) === "complete" && !event.aborted) {
                const movedGraphic = event.graphics[0];
                if (!movedGraphic?.geometry) return;
                const geomJson = movedGraphic.geometry.toJSON() as { rings?: number[][][] };
                sketchLayer.remove(movedGraphic);
                bufferLayer.add(movedGraphic);
                movingBufferRef.current = null;
                onMoveCompleteRef.current?.(meta.bufferId, geomJson.rings ?? []);
            } else if ((event.state as string) === "complete" && event.aborted) {
                const graphic = event.graphics[0];
                if (graphic) {
                    sketchLayer.remove(graphic);
                    bufferLayer.add(graphic);
                }
                movingBufferRef.current = null;
                onMoveCanceledRef.current?.();
            }
        });

        async function init() {
            setRenderError(null);
            await mapView.when();
            await mapView.goTo(warehouseExtent.expand(1.02), { animate: false });
            mapView.on("click", async (clickEvent) => {
                const result = await mapView.hitTest(clickEvent, { include: [bufferLayer] });
                const hit = result.results
                    .filter((r) => r.type === "graphic")
                    .map((r) => (r as { graphic: Graphic }).graphic)
                    .find((g) => g.geometry?.type === "polygon" && g.attributes?.bufferId);

                if (hit) {
                    onBufferSelectRef.current(
                        hit.attributes.bufferId as string,
                        hit.attributes.bufferProps as HazardBufferFeatureProps
                    );
                    const extent = (hit.geometry as Polygon).extent;
                    if (extent) void mapView.goTo(extent.expand(3), { animate: true, duration: 400 });
                } else {
                    onDeselectRef.current();
                }
            });
        }

        init().catch((err) => {
            const message = err instanceof Error ? err.message : "Failed to render map.";
            setRenderError(message);
            console.error("HazardBufferMapView init error", err);
        });

        return () => {
            sketch.destroy();
            sketchRef.current = null;
            sketchLayerRef.current = null;
            bufferLayerRef.current = null;
            mapView.destroy();
            mapViewRef.current = null;
        };
    }, [svgContent, anchorLon, anchorLat, widthMeters, lengthMeters]);

    useEffect(() => {
        const sketch = sketchRef.current;
        if (!sketch) return;
        if (drawPending) {
            void sketch.create("polygon");
        } else {
            sketch.cancel();
        }
    }, [drawPending]);

    useEffect(() => {
        const sketch = sketchRef.current;
        const sketchLayer = sketchLayerRef.current;
        const bufferLayer = bufferLayerRef.current;
        if (!sketch || !sketchLayer || !bufferLayer) return;
        if (!editingBufferId) {
            if (movingBufferRef.current) sketch.cancel();
            return;
        }
        const targetGraphic = bufferLayer.graphics.toArray()
            .find((g: Graphic) => g.attributes?.bufferId === editingBufferId);
        if (!targetGraphic) return;
        movingBufferRef.current = { bufferId: editingBufferId };
        bufferLayer.remove(targetGraphic);
        sketchLayer.add(targetGraphic);
        void sketch.update(targetGraphic);
    }, [editingBufferId]);

    useEffect(() => {
        const layer = bufferLayerRef.current;
        if (!layer) return;
        layer.graphics.removeAll();

        const currentSelectedId = selectedBufferIdRef.current;
        for (const feature of buffersGeoJson?.features ?? []) {
            const props = feature.properties;
            const rings = (feature.geometry as { coordinates: number[][][] }).coordinates;
            const polygon = new Polygon({ rings, spatialReference: { wkid: 4326 } });
            const isSelected = props.id === currentSelectedId;

            layer.add(new Graphic({
                geometry: polygon,
                symbol: symbolForBuffer(isSelected),
                attributes: { bufferId: props.id, bufferProps: props },
            }));

            const centroid = centroidOperator.execute(polygon);
            if (centroid) {
                layer.add(new Graphic({
                    geometry: centroid,
                    symbol: new TextSymbol({
                        text: props.name,
                        color: HAZARD_STROKE,
                        haloColor: "white",
                        haloSize: "1.5px",
                        font: { size: 11, weight: "bold" },
                        horizontalAlignment: "center",
                        verticalAlignment: "middle",
                    }),
                    attributes: { isLabel: true, bufferId: props.id },
                }));
            }
        }
    }, [buffersGeoJson]);

    useEffect(() => {
        const layer = bufferLayerRef.current;
        if (!layer) return;
        for (const g of layer.graphics.toArray()) {
            if (g.geometry?.type === "polygon" && g.attributes?.bufferId) {
                g.symbol = symbolForBuffer(g.attributes.bufferId === selectedBufferId);
            }
        }
    }, [selectedBufferId]);

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
