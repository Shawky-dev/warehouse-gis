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
import MapView from "@arcgis/core/views/MapView.js";
import Sketch from "@arcgis/core/widgets/Sketch.js";
import type { ExistingPolygon, EditorTemplate } from "./useEditorState";

esriConfig.assetsPath = `${import.meta.env.BASE_URL}assets`;

// ── Color palette keyed by template name ──────────────────────────────────────

const TEMPLATE_COLORS: Record<string, { fill: string; stroke: string }> = {
  Zone:  { fill: "rgba(59, 130, 246, 0.15)",  stroke: "#3b82f6" },
  Aisle: { fill: "rgba(16, 185, 129, 0.15)",  stroke: "#10b981" },
  Bay:   { fill: "rgba(245, 158, 11, 0.15)",  stroke: "#f59e0b" },
  Shelf: { fill: "rgba(239, 68, 68, 0.15)",   stroke: "#ef4444" },
};
const DEFAULT_COLOR = { fill: "rgba(139, 92, 246, 0.15)", stroke: "#8b5cf6" };

function getTemplateColor(name: string) {
  return TEMPLATE_COLORS[name] ?? DEFAULT_COLOR;
}

// ── Props & handle types ──────────────────────────────────────────────────────

export interface WarehouseMapViewHandle {
  cancelPendingPolygon: () => void;
}

interface WarehouseMapViewProps {
  svgContent: string;
  anchorLon: number;
  anchorLat: number;
  widthMeters: number;
  lengthMeters: number;
  // Editor mode — all optional; absent = read-only
  editorMode?: boolean;
  templates?: EditorTemplate[];
  activeTemplateName?: string | null;
  onPolygonComplete?: (rings: number[][][], templateName: string) => void;
  onPolygonDelete?: (gisBlockId: string, templateName: string) => void;
  existingPolygons?: ExistingPolygon[];
}

// ── Component ─────────────────────────────────────────────────────────────────

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
      onPolygonDelete,
      existingPolygons,
    },
    ref
  ) {
    const containerRef = useRef<HTMLDivElement>(null);
    const viewRef = useRef<MapView | null>(null);
    const sketchRef = useRef<Sketch | null>(null);
    const layersRef = useRef<Map<string, GraphicsLayer>>(new Map());
    const lastCreatedGraphicRef = useRef<Graphic | null>(null);
    const activeTemplateNameRef = useRef<string | null>(activeTemplateName ?? null);
    // Stable callback refs so event listeners always call the latest version
    const onPolygonCompleteRef = useRef(onPolygonComplete);
    const onPolygonDeleteRef = useRef(onPolygonDelete);
    const [renderError, setRenderError] = useState<string | null>(null);

    // Keep callback and active-template refs in sync with latest props
    useEffect(() => { onPolygonCompleteRef.current = onPolygonComplete; }, [onPolygonComplete]);
    useEffect(() => { onPolygonDeleteRef.current = onPolygonDelete; }, [onPolygonDelete]);
    useEffect(() => { activeTemplateNameRef.current = activeTemplateName ?? null; }, [activeTemplateName]);

    // ── Imperative handle: remove the unassigned drawn polygon ───────────────
    useImperativeHandle(ref, () => ({
      cancelPendingPolygon() {
        const graphic = lastCreatedGraphicRef.current;
        const layerName = activeTemplateNameRef.current;
        if (graphic && layerName) {
          layersRef.current.get(layerName)?.graphics.remove(graphic);
        }
        lastCreatedGraphicRef.current = null;
      },
    }));

    // ── Main effect: build map, layers, view, sketch ─────────────────────────
    // Only the stable spatial properties are in the dep list — template switches
    // and polygon list updates are handled by the two separate effects below.
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

      // Create one GraphicsLayer per template synchronously before view.when()
      // so the existingPolygons effect can add graphics to them immediately
      const layers = new Map<string, GraphicsLayer>();
      if (editorMode && templates && templates.length > 0) {
        for (const tpl of templates) {
          layers.set(tpl.name, new GraphicsLayer({ id: `gis-layer-${tpl.name}`, title: tpl.name }));
        }
      }
      layersRef.current = layers;

      const esriMap = new EsriMap({
        layers: [mediaLayer, ...Array.from(layers.values())],
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

      let cancelled = false;

      void (async () => {
        try {
          setRenderError(null);
          await view.when();
          if (cancelled) return;
          await view.goTo(warehouseExtent.expand(1.02), { animate: false });

          // Sketch widget — wired to the currently active template's layer
          if (editorMode && layers.size > 0) {
            const initialLayerName =
              activeTemplateNameRef.current ?? templates?.[0]?.name;
            const initialLayer = initialLayerName
              ? layers.get(initialLayerName)
              : undefined;

            if (initialLayer) {
              const sketch = new Sketch({
                layer: initialLayer,
                view,
                availableCreateTools: ["polygon", "rectangle"],
              });

              sketch.on("create", (event) => {
                if (event.state === "complete") {
                  lastCreatedGraphicRef.current = event.graphic;
                  const tplName = activeTemplateNameRef.current ?? "";
                  const color = getTemplateColor(tplName);
                  // Stamp the layer color onto the completed graphic
                  event.graphic.symbol = new SimpleFillSymbol({
                    color: color.fill,
                    outline: { color: color.stroke, width: 2 },
                  });
                  const geomJson = event.graphic.geometry.toJSON() as {
                    rings?: number[][][];
                  };
                  onPolygonCompleteRef.current?.(geomJson.rings ?? [], tplName);
                }
              });

              view.ui.add(sketch, "top-right");
              sketchRef.current = sketch;
            }
          }

          // Click-to-delete existing polygons
          if (editorMode) {
            view.on("click", async (clickEvent) => {
              const result = await view.hitTest(clickEvent);
              const hit = result.results
                .filter((r) => r.type === "graphic")
                .map((r) => (r as { graphic: Graphic }).graphic)
                .find((g) => g.attributes?.gisBlockId);

              if (hit) {
                const confirmed = window.confirm("Remove this polygon mapping?");
                if (confirmed) {
                  onPolygonDeleteRef.current?.(
                    hit.attributes.gisBlockId as string,
                    hit.attributes.templateName as string
                  );
                }
              }
            });
          }
        } catch (err) {
          if (!cancelled) {
            const message =
              err instanceof Error ? err.message : "Failed to render floor plan.";
            setRenderError(message);
            console.error("Failed to render floor plan", err);
          }
        }
      })();

      return () => {
        cancelled = true;
        sketchRef.current = null;
        view.destroy();
        viewRef.current = null;
      };
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [svgContent, anchorLon, anchorLat, widthMeters, lengthMeters]);

    // ── Effect: switch Sketch's target layer when the active template changes ─
    useEffect(() => {
      if (!sketchRef.current || !activeTemplateName) return;
      const layer = layersRef.current.get(activeTemplateName);
      if (layer) sketchRef.current.layer = layer;
    }, [activeTemplateName]);

    // ── Effect: sync existing polygons into their graphics layers ────────────
    // Runs on mount (initial load) and after every save / delete.
    useEffect(() => {
      if (!editorMode || layersRef.current.size === 0) return;

      for (const layer of layersRef.current.values()) {
        layer.graphics.removeAll();
      }

      for (const ep of existingPolygons ?? []) {
        const layer = layersRef.current.get(ep.templateName);
        if (!layer) continue;
        const color = getTemplateColor(ep.templateName);
        const polygon = new Polygon({
          rings: ep.rings,
          spatialReference: { wkid: 4326 },
        });
        layer.graphics.add(
          new Graphic({
            geometry: polygon,
            symbol: new SimpleFillSymbol({
              color: color.fill,
              outline: { color: color.stroke, width: 2 },
            }),
            attributes: {
              gisBlockId: ep.gisBlockId,
              templateName: ep.templateName,
              label: ep.label,
            },
          })
        );
      }
    }, [existingPolygons, editorMode]);

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
