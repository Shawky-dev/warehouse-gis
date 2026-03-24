import { useEffect, useRef, useState } from "react";
import esriConfig from "@arcgis/core/config.js";
import Extent from "@arcgis/core/geometry/Extent.js";
import MediaLayer from "@arcgis/core/layers/MediaLayer.js";
import ImageElement from "@arcgis/core/layers/support/ImageElement.js";
import ExtentAndRotationGeoreference from "@arcgis/core/layers/support/ExtentAndRotationGeoreference.js";
import Map from "@arcgis/core/Map.js";
import MapView from "@arcgis/core/views/MapView.js";

esriConfig.assetsPath = `${import.meta.env.BASE_URL}assets`;

interface WarehouseMapViewProps {
  svgContent: string;
  anchorLon: number;
  anchorLat: number;
  widthMeters: number;
  lengthMeters: number;
}

export function WarehouseMapView({
  svgContent,
  anchorLon,
  anchorLat,
  widthMeters,
  lengthMeters,
}: WarehouseMapViewProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<MapView | null>(null);
  const [renderError, setRenderError] = useState<string | null>(null);

  useEffect(() => {
    if (!containerRef.current) {
      return;
    }

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
          georeference: new ExtentAndRotationGeoreference({
            extent: warehouseExtent,
          }),
        }),
      ],
      title: "Warehouse floor plan",
    });

    const map = new Map({ layers: [mediaLayer] });

    const view = new MapView({
      container: containerRef.current,
      map,
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
      } catch (error) {
        if (!cancelled) {
          const message =
            error instanceof Error ? error.message : "Failed to render floor plan.";
          setRenderError(message);
          console.error("Failed to render floor plan", error);
        }
      }
    })();

    return () => {
      cancelled = true;
      view.destroy();
      viewRef.current = null;
    };
  }, [svgContent, anchorLon, anchorLat, widthMeters, lengthMeters]);

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
