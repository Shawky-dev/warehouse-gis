import { useEffect, useRef } from "react";
import type { ArcgisSketch, ArcgisSketchCustomEvent } from "@arcgis/map-components";
import type Graphic from "@arcgis/core/Graphic.js";
import type GraphicsLayer from "@arcgis/core/layers/GraphicsLayer.js";
import type { CreateEvent } from "@arcgis/core/widgets/Sketch/types.js";
import type MapView from "@arcgis/core/views/MapView.js";
import "@arcgis/map-components/components/arcgis-sketch";

interface ArcgisSketchOverlayProps {
  active: boolean;
  className?: string;
  layer: GraphicsLayer | null;
  onCancel?: () => void;
  onCreateComplete?: (graphic: Graphic) => void;
  view: MapView | null;
}

export function ArcgisSketchOverlay({
  active,
  className,
  layer,
  onCancel,
  onCreateComplete,
  view,
}: ArcgisSketchOverlayProps) {
  const sketchRef = useRef<ArcgisSketch | null>(null);
  const activeRef = useRef(active);

  useEffect(() => {
    activeRef.current = active;
  }, [active]);

  useEffect(() => {
    const sketch = sketchRef.current;
    if (!sketch) return;

    let cancelled = false;

    void sketch.componentOnReady().then(() => {
      if (cancelled) return;
      sketch.availableCreateTools = ["polygon"];
      sketch.creationMode = "single";
      sketch.defaultGraphicsLayerDisabled = true;
      sketch.hideCreateToolsCircle = true;
      sketch.hideCreateToolsPoint = true;
      sketch.hideCreateToolsPolyline = true;
      sketch.hideCreateToolsRectangle = true;
      sketch.hideSelectionToolsLassoSelection = true;
      sketch.hideSelectionToolsRectangleSelection = true;
      sketch.hideSettingsMenu = true;
      sketch.hideUndoRedoMenu = true;
      sketch.layout = "vertical";
      sketch.toolbarKind = "docked";
      sketch.view = view;
      sketch.layer = layer;
    });

    return () => {
      cancelled = true;
    };
  }, [layer, view]);

  useEffect(() => {
    const sketch = sketchRef.current;
    if (!sketch || !view || !layer) return;

    let cancelled = false;

    void sketch.componentOnReady().then(async () => {
      if (cancelled) return;

      sketch.view = view;
      sketch.layer = layer;

      if (active) {
        await sketch.cancel().catch(() => undefined);
        if (!cancelled) {
          await sketch.create("polygon");
        }
        return;
      }

      await sketch.cancel().catch(() => undefined);
    });

    return () => {
      cancelled = true;
    };
  }, [active, layer, view]);

  useEffect(() => {
    const sketch = sketchRef.current;
    if (!sketch) return;

    const handleCreate = (event: Event) => {
      const createEvent = event as ArcgisSketchCustomEvent<CreateEvent>;
      const { detail } = createEvent;

      if (detail.state === "complete" && detail.graphic) {
        onCreateComplete?.(detail.graphic);
        return;
      }

      if (detail.state === "cancel" && activeRef.current) {
        onCancel?.();
      }
    };

    sketch.addEventListener("arcgisCreate", handleCreate as EventListener);

    return () => {
      sketch.removeEventListener("arcgisCreate", handleCreate as EventListener);
    };
  }, [onCancel, onCreateComplete]);

  return (
    <arcgis-sketch
      ref={sketchRef}
      className={className}
      style={{ display: active ? "block" : "none" }}
    />
  );
}
