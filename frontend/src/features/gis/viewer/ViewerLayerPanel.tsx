import type { ComponentType } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Box, Eye, EyeOff, ImageIcon, Layers, MapPin, ShieldAlert, X, Flame, RefreshCw } from "lucide-react";
import * as LucideIcons from "lucide-react";
import { Badge } from "@/shared/components/ui/badge";
import { Button } from "@/shared/components/ui/button";
import { useI18n } from "@/i18n";
import { PATHS } from "@/shared/consts/paths";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { getTemplateStroke } from "../floorplans/templateColors";
import type { EditorTemplate } from "../floorplans/useEditorState";
import type { StaticHeatmapRecord, DynamicHeatmapMetric } from "@/features/tenant/types/gis";

interface ViewerLayerPanelProps {
  templates: EditorTemplate[];
  polygonCountByTemplate: Record<string, number>;
  visibilityByTemplate: Record<string, boolean>;
  onVisibilityToggle: (templateName: string) => void;
  svgVisible: boolean;
  onSvgVisibilityToggle: () => void;
  selectedPolygon: { gisBlockId: string; templateName: string; label: string; positionPath?: string } | null;
  onClearSelection: () => void;
  zonesVisible?: boolean;
  onZonesVisibilityToggle?: () => void;
  hazardBuffersVisible?: boolean;
  onHazardBuffersVisibilityToggle?: () => void;
  // Static heatmap props
  staticHeatmaps?: StaticHeatmapRecord[];
  selectedStaticHeatmapId?: string | null;
  onStaticHeatmapSelect?: (id: string) => void;
  staticHeatmapVisible?: boolean;
  onStaticHeatmapVisibilityToggle?: () => void;
  // Dynamic heatmap props
  dynamicMetrics?: DynamicHeatmapMetric[];
  selectedDynamicMetricKey?: string | null;
  onDynamicMetricSelect?: (key: string) => void;
  dynamicHeatmapVisible?: boolean;
  onDynamicHeatmapVisibilityToggle?: () => void;
  isDynamicHeatmapRefreshing?: boolean;
  onDynamicHeatmapRefresh?: () => void;
}

export function ViewerLayerPanel({
  templates,
  polygonCountByTemplate,
  visibilityByTemplate,
  onVisibilityToggle,
  svgVisible,
  onSvgVisibilityToggle,
  selectedPolygon,
  onClearSelection,
  zonesVisible,
  onZonesVisibilityToggle,
  hazardBuffersVisible,
  onHazardBuffersVisibilityToggle,
  staticHeatmaps,
  selectedStaticHeatmapId,
  onStaticHeatmapSelect,
  staticHeatmapVisible,
  onStaticHeatmapVisibilityToggle,
  dynamicMetrics,
  selectedDynamicMetricKey,
  onDynamicMetricSelect,
  dynamicHeatmapVisible,
  onDynamicHeatmapVisibilityToggle,
  isDynamicHeatmapRefreshing,
  onDynamicHeatmapRefresh,
}: ViewerLayerPanelProps) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");

  return (
    <div className="flex h-full flex-col gap-2 rounded-md border bg-card p-3">

      {/* Layer panel title */}
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        {t("gis.viewer.layerPanelTitle")}
      </p>

      {/* SVG floor plan layer row */}
      <div
        className={`flex w-full items-center rounded-sm transition-colors hover:bg-accent/50 ${!svgVisible ? "opacity-40" : ""
          }`}
      >
        <div className="flex min-w-0 flex-1 items-center gap-1.5 px-2 py-1.5">
          <ImageIcon className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
          <span className="flex-1 truncate text-sm font-medium">{t("gis.editor.svgLayer")}</span>
        </div>
        <button
          type="button"
          title={svgVisible ? t("gis.editor.layerVisible") : t("gis.editor.layerHidden")}
          onClick={onSvgVisibilityToggle}
          className="mr-1 shrink-0 rounded p-0.5 transition-colors hover:bg-accent/50"
        >
          {svgVisible ? (
            <Eye className="h-3.5 w-3.5 text-muted-foreground" />
          ) : (
            <EyeOff className="h-3.5 w-3.5 text-muted-foreground" />
          )}
        </button>
      </div>

      {/* Zones overlay layer row */}
      {onZonesVisibilityToggle !== undefined && (
        <div
          className={`flex w-full items-center rounded-sm transition-colors hover:bg-accent/50 ${!zonesVisible ? "opacity-40" : ""
            }`}
        >
          <div className="flex min-w-0 flex-1 items-center gap-1.5 px-2 py-1.5">
            <MapPin className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
            <span className="flex-1 truncate text-sm font-medium">{t("gis.viewer.zonesLayer")}</span>
          </div>
          <button
            type="button"
            title={zonesVisible ? t("gis.editor.layerVisible") : t("gis.editor.layerHidden")}
            onClick={onZonesVisibilityToggle}
            className="mr-1 shrink-0 rounded p-0.5 transition-colors hover:bg-accent/50"
          >
            {zonesVisible ? (
              <Eye className="h-3.5 w-3.5 text-muted-foreground" />
            ) : (
              <EyeOff className="h-3.5 w-3.5 text-muted-foreground" />
            )}
          </button>
        </div>
      )}

      {/* Hazard buffers overlay layer row */}
      {onHazardBuffersVisibilityToggle !== undefined && (
        <div
          className={`flex w-full items-center rounded-sm transition-colors hover:bg-accent/50 ${!hazardBuffersVisible ? "opacity-40" : ""
            }`}
        >
          <div className="flex min-w-0 flex-1 items-center gap-1.5 px-2 py-1.5">
            <ShieldAlert className="h-3.5 w-3.5 shrink-0 text-red-500" />
            <span className="flex-1 truncate text-sm font-medium">{t("gis.viewer.hazardBuffersLayer")}</span>
          </div>
          <button
            type="button"
            title={hazardBuffersVisible ? t("gis.editor.layerVisible") : t("gis.editor.layerHidden")}
            onClick={onHazardBuffersVisibilityToggle}
            className="mr-1 shrink-0 rounded p-0.5 transition-colors hover:bg-accent/50"
          >
            {hazardBuffersVisible ? (
              <Eye className="h-3.5 w-3.5 text-muted-foreground" />
            ) : (
              <EyeOff className="h-3.5 w-3.5 text-muted-foreground" />
            )}
          </button>
        </div>
      )}

      {/* Static heatmap layer section */}
      {onStaticHeatmapVisibilityToggle !== undefined && (
        <div className="flex flex-col gap-1">
          <div
            className={`flex w-full items-center rounded-sm transition-colors hover:bg-accent/50 ${!staticHeatmapVisible ? "opacity-40" : ""
              }`}
          >
            <div className="flex min-w-0 flex-1 items-center gap-1.5 px-2 py-1.5">
              <Flame className="h-3.5 w-3.5 shrink-0 text-orange-500" />
              <span className="flex-1 truncate text-sm font-medium">{t("gis.viewer.staticHeatmapLayer")}</span>
            </div>
            <button
              type="button"
              title={staticHeatmapVisible ? t("gis.editor.layerVisible") : t("gis.editor.layerHidden")}
              onClick={onStaticHeatmapVisibilityToggle}
              className="mr-1 shrink-0 rounded p-0.5 transition-colors hover:bg-accent/50"
            >
              {staticHeatmapVisible ? (
                <Eye className="h-3.5 w-3.5 text-muted-foreground" />
              ) : (
                <EyeOff className="h-3.5 w-3.5 text-muted-foreground" />
              )}
            </button>
          </div>
          {onStaticHeatmapSelect && (
            <select
              className="mx-2 h-7 rounded border bg-background px-1.5 text-xs focus:outline-none focus:ring-1 focus:ring-ring disabled:opacity-50"
              value={selectedStaticHeatmapId ?? ""}
              disabled={!staticHeatmaps || staticHeatmaps.length === 0}
              onChange={(e) => onStaticHeatmapSelect(e.target.value)}
              aria-label={t("gis.viewer.staticHeatmapSelect")}
            >
              {(!staticHeatmaps || staticHeatmaps.length === 0) ? (
                <option value="">{t("gis.viewer.staticHeatmapEmpty")}</option>
              ) : (
                staticHeatmaps.map((h) => (
                  <option key={h.id} value={h.id}>{h.name}</option>
                ))
              )}
            </select>
          )}
        </div>
      )}

      {/* Dynamic heatmap layer section */}
      {onDynamicHeatmapVisibilityToggle !== undefined && (
        <div className="flex flex-col gap-1">
          <div
            className={`flex w-full items-center rounded-sm transition-colors hover:bg-accent/50 ${!dynamicHeatmapVisible ? "opacity-40" : ""
              }`}
          >
            <div className="flex min-w-0 flex-1 items-center gap-1.5 px-2 py-1.5">
              <Flame className="h-3.5 w-3.5 shrink-0 text-purple-500" />
              <span className="flex-1 truncate text-sm font-medium">{t("gis.viewer.dynamicHeatmapLayer")}</span>
            </div>
            <button
              type="button"
              title={dynamicHeatmapVisible ? t("gis.editor.layerVisible") : t("gis.editor.layerHidden")}
              onClick={onDynamicHeatmapVisibilityToggle}
              className="mr-1 shrink-0 rounded p-0.5 transition-colors hover:bg-accent/50"
            >
              {dynamicHeatmapVisible ? (
                <Eye className="h-3.5 w-3.5 text-muted-foreground" />
              ) : (
                <EyeOff className="h-3.5 w-3.5 text-muted-foreground" />
              )}
            </button>
          </div>
          <div className="mx-2 flex items-center gap-1">
            {onDynamicMetricSelect && (
              <select
                className="h-7 flex-1 rounded border bg-background px-1.5 text-xs focus:outline-none focus:ring-1 focus:ring-ring disabled:opacity-50"
                value={selectedDynamicMetricKey ?? ""}
                disabled={!dynamicMetrics || dynamicMetrics.length === 0}
                onChange={(e) => onDynamicMetricSelect(e.target.value)}
                aria-label={t("gis.viewer.dynamicMetricSelect")}
              >
                {(!dynamicMetrics || dynamicMetrics.length === 0) ? (
                  <option value="">{t("gis.viewer.dynamicMetricEmpty")}</option>
                ) : (
                  dynamicMetrics.map((m) => (
                    <option key={m.key} value={m.key}>
                      {m.label}{m.unit ? ` (${m.unit})` : ""}
                    </option>
                  ))
                )}
              </select>
            )}
            {onDynamicHeatmapRefresh && (
              <button
                type="button"
                title={t("gis.viewer.dynamicRefresh")}
                onClick={onDynamicHeatmapRefresh}
                disabled={isDynamicHeatmapRefreshing}
                className="shrink-0 rounded p-1 text-xs transition-colors hover:bg-accent/50 disabled:opacity-50"
              >
                <RefreshCw className={`h-3.5 w-3.5 text-muted-foreground ${isDynamicHeatmapRefreshing ? "animate-spin" : ""}`} />
              </button>
            )}
          </div>
        </div>
      )}

      {/* Template list or empty state */}
      {templates.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 text-center">
          <Layers className="h-8 w-8 text-muted-foreground/40" />
          <p className="text-xs text-muted-foreground">{t("gis.viewer.noBlocks")}</p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="text-xs"
            onClick={() => navigate(PATHS.TENANT.gisFloorPlans(slug))}
          >
            {t("gis.viewer.goToFloorPlans")} →
          </Button>
        </div>
      ) : (
        <div className="flex flex-1 flex-col gap-0.5 overflow-y-auto">
          {templates.map((tpl) => {
            const count = polygonCountByTemplate[tpl.name] ?? 0;
            const isVisible = visibilityByTemplate[tpl.name] ?? true;
            const strokeColor = getTemplateStroke(tpl.name);
            const IconComp = (
              (LucideIcons as unknown) as Record<string, ComponentType<{ className?: string }>>
            )[tpl.iconName] ?? Box;

            return (
              <div
                key={tpl.id}
                className={`flex w-full items-center rounded-sm transition-colors hover:bg-accent/50 ${!isVisible ? "opacity-40" : ""
                  }`}
              >
                <div className="flex min-w-0 flex-1 items-center gap-1.5 px-2 py-1.5">
                  <span
                    className="h-2.5 w-2.5 shrink-0 rounded-full"
                    style={{ backgroundColor: strokeColor }}
                  />
                  <IconComp className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                  <span className="flex-1 truncate text-sm font-medium">{tpl.name}</span>
                </div>
                {count > 0 ? (
                  <Badge variant="secondary" className="mr-1 shrink-0 text-[10px]">
                    {t("gis.editor.polygonCount").replace("{count}", String(count))}
                  </Badge>
                ) : (
                  <span className="mr-1 shrink-0 text-xs text-muted-foreground">–</span>
                )}
                <button
                  type="button"
                  title={isVisible ? t("gis.editor.layerVisible") : t("gis.editor.layerHidden")}
                  onClick={() => onVisibilityToggle(tpl.name)}
                  className="mr-1 shrink-0 rounded p-0.5 transition-colors hover:bg-accent/50"
                >
                  {isVisible ? (
                    <Eye className="h-3.5 w-3.5 text-muted-foreground" />
                  ) : (
                    <EyeOff className="h-3.5 w-3.5 text-muted-foreground" />
                  )}
                </button>
              </div>
            );
          })}
        </div>
      )}

      {/* Selected polygon info card */}
      {selectedPolygon && (
        <div className="rounded-md border bg-muted/30 p-2">
          <div className="flex items-center justify-between">
            <p className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
              {t("gis.viewer.selectedTitle")}
            </p>
            <button
              type="button"
              onClick={onClearSelection}
              className="shrink-0 rounded p-0.5 transition-colors hover:bg-accent/50"
            >
              <X className="h-3 w-3 text-muted-foreground" />
            </button>
          </div>
          <div className="mt-1 flex items-center gap-1.5">
            <span
              className="h-2.5 w-2.5 shrink-0 rounded-full"
              style={{ backgroundColor: getTemplateStroke(selectedPolygon.templateName) }}
            />
            <span className="min-w-0 flex-1 truncate text-sm font-medium">
              {selectedPolygon.label}
            </span>
          </div>
          <p className="mt-0.5 text-[11px] text-muted-foreground">{selectedPolygon.templateName}</p>
          {selectedPolygon.positionPath && selectedPolygon.positionPath !== selectedPolygon.label && (
            <p className="mt-0.5 truncate text-[10px] text-muted-foreground">
              {selectedPolygon.positionPath}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
