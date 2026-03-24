import { useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Globe } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Button } from "@/shared/components/ui/button";
import { useI18n } from "@/i18n";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { PATHS } from "@/shared/consts/paths";
import { useFloorPlanApi } from "../floorplans/useFloorPlanApi";
import { useEditorState } from "../floorplans/useEditorState";
import { WarehouseMapView } from "../floorplans/WarehouseMapView";
import { ViewerLayerPanel } from "./ViewerLayerPanel";

export default function WarehouseMapPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");

  const { config, loading, error, svgContent } = useFloorPlanApi();
  const { templates, existingPolygons, polygonCountByTemplate } = useEditorState();

  const [selectedPolygon, setSelectedPolygon] = useState<{
    gisBlockId: string;
    templateName: string;
    label: string;
  } | null>(null);
  const [visibilityOverrides, setVisibilityOverrides] = useState<Record<string, boolean>>({});
  const [svgVisible, setSvgVisible] = useState(true);
  const visibilityByTemplate = useMemo(
    () =>
      Object.fromEntries(
        templates.map((tpl) => [tpl.name, visibilityOverrides[tpl.name] ?? true])
      ),
    [templates, visibilityOverrides]
  );

  function handleVisibilityToggle(templateName: string) {
    setVisibilityOverrides((prev) => ({ ...prev, [templateName]: !(prev[templateName] ?? true) }));
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">{t("gis.viewer.pageTitle")}</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {t("gis.viewer.pageDescription")}
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Globe className="h-4 w-4" />
            {t("gis.viewer.pageTitle")}
          </CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="flex h-96 items-center justify-center text-sm text-muted-foreground">
              Loading...
            </div>
          ) : null}

          {!loading && error ? (
            <div className="flex h-96 items-center justify-center text-sm text-destructive">
              {error}
            </div>
          ) : null}

          {!loading && !error && !config?.hasFloorPlan ? (
            <div className="flex h-96 flex-col items-center justify-center gap-3 text-sm text-muted-foreground">
              <p>{t("gis.viewer.noFloorPlan")}</p>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => navigate(PATHS.TENANT.gisFloorPlans(slug))}
              >
                {t("gis.viewer.goToFloorPlans")} →
              </Button>
            </div>
          ) : null}

          {!loading && !error && config?.hasFloorPlan && svgContent ? (
            <div className="flex gap-4" style={{ height: "640px" }}>
              <div className="w-52 shrink-0">
                <ViewerLayerPanel
                  templates={templates}
                  polygonCountByTemplate={polygonCountByTemplate}
                  visibilityByTemplate={visibilityByTemplate}
                  onVisibilityToggle={handleVisibilityToggle}
                  svgVisible={svgVisible}
                  onSvgVisibilityToggle={() => setSvgVisible((v) => !v)}
                  selectedPolygon={selectedPolygon}
                  onClearSelection={() => setSelectedPolygon(null)}
                />
              </div>
              <div className="relative flex-1">
                {existingPolygons.length === 0 && templates.length > 0 ? (
                  <div className="pointer-events-none absolute inset-0 z-10 flex flex-col items-center justify-center gap-2 rounded-md bg-background/60">
                    <p className="text-sm text-muted-foreground">{t("gis.viewer.noBlocks")}</p>
                  </div>
                ) : null}
                <WarehouseMapView
                  svgContent={svgContent}
                  anchorLon={config.anchorLon}
                  anchorLat={config.anchorLat}
                  widthMeters={config.widthMeters}
                  lengthMeters={config.lengthMeters}
                  templates={templates}
                  existingPolygons={existingPolygons}
                  visibilityByTemplate={visibilityByTemplate}
                  svgVisible={svgVisible}
                  selectedGisBlockId={selectedPolygon?.gisBlockId}
                  onPolygonSelect={(gisBlockId, templateName, label) => {
                    if (gisBlockId && templateName && label) {
                      setSelectedPolygon({ gisBlockId, templateName, label });
                    } else {
                      setSelectedPolygon(null);
                    }
                  }}
                />
              </div>
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
