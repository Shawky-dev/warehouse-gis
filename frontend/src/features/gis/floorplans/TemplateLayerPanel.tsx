import type { ComponentType } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Box, Eye, EyeOff, Layers, MousePointer, Pencil, RotateCcw, Trash2 } from "lucide-react";
import * as LucideIcons from "lucide-react";
import { Badge } from "@/shared/components/ui/badge";
import { Button } from "@/shared/components/ui/button";
import { useI18n } from "@/i18n";
import { PATHS } from "@/shared/consts/paths";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { getTemplateStroke } from "./templateColors";
import type { EditorTemplate } from "./useEditorState";

interface TemplateLayerPanelProps {
  templates: EditorTemplate[];
  activeTemplateName: string | null;
  onSelect: (templateName: string) => void;
  polygonCountByTemplate: Record<string, number>;
  totalBlocksByTemplate: Record<string, number>;
  hasActiveLayout: boolean;
  // Draw mode toggle
  isDrawMode: boolean;
  onDrawModeChange: (drawing: boolean) => void;
  // Layer visibility
  visibilityByTemplate: Record<string, boolean>;
  onVisibilityToggle: (templateName: string) => void;
  // Selected polygon
  selectedPolygon: { gisBlockId: string; templateName: string; label: string } | null;
  onDeleteSelected: () => void;
  onReassignSelected: () => void;
  // Undo
  canUndo: boolean;
  onUndo: () => void;
}

export function TemplateLayerPanel({
  templates,
  activeTemplateName,
  onSelect,
  polygonCountByTemplate,
  totalBlocksByTemplate,
  hasActiveLayout,
  isDrawMode,
  onDrawModeChange,
  visibilityByTemplate,
  onVisibilityToggle,
  selectedPolygon,
  onDeleteSelected,
  onReassignSelected,
  canUndo,
  onUndo,
}: TemplateLayerPanelProps) {
  const { t } = useI18n();
  const navigate = useNavigate();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");

  const totalMapped = Object.values(polygonCountByTemplate).reduce((a, b) => a + b, 0);
  const totalBlocks = Object.values(totalBlocksByTemplate).reduce((a, b) => a + b, 0);
  const pct = totalBlocks > 0 ? Math.min(100, Math.round((totalMapped / totalBlocks) * 100)) : 0;

  return (
    <div className="flex h-full flex-col gap-2 rounded-md border bg-card p-3">

      {/* Toolbar: Draw / Select toggle + Undo */}
      <div className="flex items-center gap-1">
        <div className="flex flex-1 overflow-hidden rounded-md border">
          <button
            type="button"
            onClick={() => onDrawModeChange(true)}
            className={`flex flex-1 items-center justify-center gap-1 px-2 py-1 text-xs transition-colors ${
              isDrawMode
                ? "bg-accent text-accent-foreground font-medium"
                : "hover:bg-accent/50"
            }`}
          >
            <Pencil className="h-3 w-3" />
            {t("gis.editor.drawMode")}
          </button>
          <button
            type="button"
            onClick={() => onDrawModeChange(false)}
            className={`flex flex-1 items-center justify-center gap-1 border-l px-2 py-1 text-xs transition-colors ${
              !isDrawMode
                ? "bg-accent text-accent-foreground font-medium"
                : "hover:bg-accent/50"
            }`}
          >
            <MousePointer className="h-3 w-3" />
            {t("gis.editor.selectMode")}
          </button>
        </div>
        {canUndo && (
          <button
            type="button"
            title={t("gis.editor.undoAction")}
            onClick={onUndo}
            className="flex shrink-0 items-center rounded-md border p-1 transition-colors hover:bg-accent/50"
          >
            <RotateCcw className="h-3.5 w-3.5" />
          </button>
        )}
      </div>

      {/* Keyboard hint pills */}
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-[10px] text-muted-foreground">
        <span className="flex items-center gap-0.5">
          <kbd className="rounded border border-border bg-muted px-1 font-mono text-[10px]">D</kbd>
          {t("gis.editor.kbdDraw")}
        </span>
        <span className="flex items-center gap-0.5">
          <kbd className="rounded border border-border bg-muted px-1 font-mono text-[10px]">Esc</kbd>
          {t("gis.editor.kbdCancel")}
        </span>
        <span className="flex items-center gap-0.5">
          <kbd className="rounded border border-border bg-muted px-1 font-mono text-[10px]">Del</kbd>
          {t("gis.editor.kbdDelete")}
        </span>
      </div>

      {/* Progress indicator */}
      {totalBlocks > 0 && (
        <div className="flex flex-col gap-1">
          <p className="text-[11px] text-muted-foreground">
            {t("gis.editor.progressLabel")
              .replace("{mapped}", String(totalMapped))
              .replace("{total}", String(totalBlocks))}
          </p>
          <div className="h-1.5 overflow-hidden rounded-full bg-border">
            <div
              className="h-full rounded-full bg-primary transition-all duration-300"
              style={{ width: `${pct}%` }}
            />
          </div>
        </div>
      )}

      {/* Layer panel title */}
      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        {t("gis.editor.layerPanelTitle")}
      </p>

      {/* Template list or no-active-layout empty state */}
      {!hasActiveLayout ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 text-center">
          <Layers className="h-8 w-8 text-muted-foreground/40" />
          <p className="text-xs text-muted-foreground">{t("gis.editor.noActiveLayout")}</p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="text-xs"
            onClick={() => navigate(PATHS.TENANT.warehouseLayouts(slug))}
          >
            {t("gis.editor.goToLayouts")} →
          </Button>
        </div>
      ) : (
        <div className="flex flex-1 flex-col gap-0.5 overflow-y-auto">
          {templates.map((tpl) => {
            const isActive = tpl.name === activeTemplateName;
            const count = polygonCountByTemplate[tpl.name] ?? 0;
            const isVisible = visibilityByTemplate[tpl.name] ?? true;
            const strokeColor = getTemplateStroke(tpl.name);
            const IconComp = (
              (LucideIcons as unknown) as Record<string, ComponentType<{ className?: string }>>
            )[tpl.iconName] ?? Box;

            return (
              <div
                key={tpl.id}
                className={`flex w-full items-center rounded-sm transition-colors ${
                  isActive ? "bg-accent text-accent-foreground" : "hover:bg-accent/50"
                } ${!isVisible ? "opacity-40" : ""}`}
              >
                <button
                  type="button"
                  onClick={() => onSelect(tpl.name)}
                  className="flex min-w-0 flex-1 items-center gap-1.5 px-2 py-1.5 text-left"
                >
                  <span
                    className="h-2.5 w-2.5 shrink-0 rounded-full"
                    style={{ backgroundColor: strokeColor }}
                  />
                  <IconComp className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                  <span className="flex-1 truncate text-sm font-medium">{tpl.name}</span>
                </button>
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

      {/* Selected polygon detail card */}
      {selectedPolygon && (
        <div className="rounded-md border bg-muted/30 p-2">
          <p className="text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
            {t("gis.editor.selectedPolygon")}
          </p>
          <div className="mt-1 flex items-center gap-1.5">
            <span
              className="h-2.5 w-2.5 shrink-0 rounded-full"
              style={{ backgroundColor: getTemplateStroke(selectedPolygon.templateName) }}
            />
            <span className="min-w-0 flex-1 truncate text-sm font-medium">
              {selectedPolygon.label}
            </span>
            <button
              type="button"
              title={t("gis.editor.reassignAction")}
              onClick={onReassignSelected}
              className="shrink-0 rounded p-0.5 transition-colors hover:bg-accent/50"
            >
              <Pencil className="h-3.5 w-3.5" />
            </button>
            <button
              type="button"
              title={t("gis.editor.deleteAction")}
              onClick={onDeleteSelected}
              className="shrink-0 rounded p-0.5 text-destructive transition-colors hover:bg-destructive/10"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </div>
          <p className="mt-0.5 text-[11px] text-muted-foreground">{selectedPolygon.templateName}</p>
        </div>
      )}

      {/* Instructions */}
      <p className="text-xs leading-snug text-muted-foreground">
        {t("gis.editor.instructions")}
      </p>
    </div>
  );
}
