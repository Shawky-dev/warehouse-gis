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
import type { ExistingPolygon, EditorTemplate } from "./useEditorState";

esriConfig.assetsPath = `${import.meta.env.BASE_URL}assets`;

function getLabelFontSize(depth: number): number {
  if (depth <= 0) return 15;
  if (depth === 1) return 13;
  if (depth === 2) return 11;
  return 9;
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
  onPolygonSelect?: (gisBlockId: string | null, templateName: string | null, label: string | null) => void;
  selectedGisBlockId?: string | null;
  isDrawMode?: boolean;
  onDrawModeChange?: (drawing: boolean) => void;
  visibilityByTemplate?: Record<string, boolean>;
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
      onPolygonSelect,
      selectedGisBlockId,
      isDrawMode,
      onDrawModeChange,
      visibilityByTemplate,
      existingPolygons,
    },
    ref
  ) {
    const containerRef = useRef<HTMLDivElement>(null);
    const viewRef = useRef<MapView | null>(null);
    const sketchRef = useRef<Sketch | null>(null);
    const layersRef = useRef<Map<string, GraphicsLayer>>(new Map());
    const lastCreatedGraphicRef = useRef<Graphic | null>(null);
    const selectedGraphicRef = useRef<Graphic | null>(null);
    const activeTemplateNameRef = useRef<string | null>(activeTemplateName ?? null);
    const selectedGisBlockIdRef = useRef<string | null | undefined>(selectedGisBlockId);
    // Stable callback refs so event listeners always call the latest version
    const onPolygonCompleteRef = useRef(onPolygonComplete);
    const onPolygonSelectRef = useRef(onPolygonSelect);
    const onDrawModeChangeRef = useRef(onDrawModeChange);
    const [renderError, setRenderError] = useState<string | null>(null);

    // Keep callback and active-template refs in sync with latest props
    useEffect(() => { onPolygonCompleteRef.current = onPolygonComplete; }, [onPolygonComplete]);
    useEffect(() => { onPolygonSelectRef.current = onPolygonSelect; }, [onPolygonSelect]);
    useEffect(() => { onDrawModeChangeRef.current = onDrawModeChange; }, [onDrawModeChange]);
    useEffect(() => { activeTemplateNameRef.current = activeTemplateName ?? null; }, [activeTemplateName]);
    useEffect(() => { selectedGisBlockIdRef.current = selectedGisBlockId; }, [selectedGisBlockId]);

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
                  // Auto-switch to select mode after drawing
                  onDrawModeChangeRef.current?.(false);
                }
              });

              view.ui.add(sketch, "top-right");
              sketchRef.current = sketch;
            }
          }

          // Click to select / deselect existing polygons
          if (editorMode) {
            view.on("click", async (clickEvent) => {
              const result = await view.hitTest(clickEvent);
              const hit = result.results
                .filter((r) => r.type === "graphic")
                .map((r) => (r as { graphic: Graphic }).graphic)
                .find((g) => g.attributes?.gisBlockId && !g.attributes?.isLabel);

              if (hit) {
                onPolygonSelectRef.current?.(
                  hit.attributes.gisBlockId as string,
                  hit.attributes.templateName as string,
                  hit.attributes.label as string
                );
                void view.goTo((hit.geometry as Polygon).extent.expand(3), {
                  animate: true,
                  duration: 400,
                });
              } else {
                onPolygonSelectRef.current?.(null, null, null);
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
        selectedGraphicRef.current = null;
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

    // ── Effect: cancel sketch when switching to select mode ───────────────────
    useEffect(() => {
      if (!sketchRef.current) return;
      if (!isDrawMode) {
        sketchRef.current.cancel();
      }
    }, [isDrawMode]);

    // ── Effect: toggle layer visibility ──────────────────────────────────────
    useEffect(() => {
      if (!visibilityByTemplate) return;
      for (const [name, layer] of layersRef.current.entries()) {
        layer.visible = visibilityByTemplate[name] ?? true;
      }
    }, [visibilityByTemplate]);

    // ── Effect: highlight the selected polygon ────────────────────────────────
    useEffect(() => {
      // Reset previous selection to its original style
      if (selectedGraphicRef.current) {
        const prev = selectedGraphicRef.current;
        const tplName = prev.attributes?.templateName as string | undefined;
        const color = getTemplateColor(tplName ?? "");
        prev.symbol = new SimpleFillSymbol({
          color: color.fill,
          outline: { color: color.stroke, width: 2 },
        });
        selectedGraphicRef.current = null;
      }

      if (!selectedGisBlockId) return;

      for (const layer of layersRef.current.values()) {
        const found = layer.graphics
          .toArray()
          .find(
            (g: Graphic) =>
              g.attributes?.gisBlockId === selectedGisBlockId && !g.attributes?.isLabel
          );
        if (found) {
          const tplName = found.attributes?.templateName as string | undefined;
          const color = getTemplateColor(tplName ?? "");
          found.symbol = new SimpleFillSymbol({
            color: color.fill.replace(", 0.15)", ", 0.35)"),
            outline: { color: color.stroke, width: 3 },
          });
          selectedGraphicRef.current = found;
          break;
        }
      }
    }, [selectedGisBlockId]);

    // ── Effect: sync existing polygons into their graphics layers ────────────
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

        // Polygon graphic
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

        // Label graphic — non-interactive
        const centroid = polygon.centroid;
        if (centroid) {
          const fontSize = getLabelFontSize(ep.depth);
          layer.graphics.add(
            new Graphic({
              geometry: centroid,
              symbol: new TextSymbol({
                text: ep.label,
                color: color.stroke,
                haloColor: "white",
                haloSize: "1.5px",
                font: {
                  size: fontSize,
                  weight: "bold",
                },
                horizontalAlignment: "center",
                verticalAlignment: "middle",
                xoffset: 0,
                yoffset: 0,
              }),
              attributes: {
                isLabel: true,
                gisBlockId: ep.gisBlockId,
              },
            })
          );
        }
      }

      // Re-apply selection highlight after re-render (use ref to avoid adding
      // selectedGisBlockId to this effect's deps — the dedicated highlight effect
      // handles it for normal selection changes; this only covers the redraw case)
      const currentSelectedId = selectedGisBlockIdRef.current;
      if (currentSelectedId) {
        for (const layer of layersRef.current.values()) {
          const found = layer.graphics
            .toArray()
            .find(
              (g: Graphic) =>
                g.attributes?.gisBlockId === currentSelectedId && !g.attributes?.isLabel
            );
          if (found) {
            const tplName = found.attributes?.templateName as string | undefined;
            const color = getTemplateColor(tplName ?? "");
            found.symbol = new SimpleFillSymbol({
              color: color.fill.replace(", 0.15)", ", 0.35)"),
              outline: { color: color.stroke, width: 3 },
            });
            selectedGraphicRef.current = found;
            break;
          }
        }
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
