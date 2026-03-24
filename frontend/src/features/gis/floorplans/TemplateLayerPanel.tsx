import { useI18n } from "@/i18n";
import { Badge } from "@/shared/components/ui/badge";
import type { EditorTemplate } from "./useEditorState";

/** Hex stroke colors by well-known template names; falls back to purple */
const TEMPLATE_STROKE: Record<string, string> = {
  Zone: "#3b82f6",
  Aisle: "#10b981",
  Bay: "#f59e0b",
  Shelf: "#ef4444",
};

function getStrokeColor(name: string): string {
  return TEMPLATE_STROKE[name] ?? "#8b5cf6";
}

interface TemplateLayerPanelProps {
  templates: EditorTemplate[];
  activeTemplateName: string | null;
  onSelect: (templateName: string) => void;
  polygonCountByTemplate: Record<string, number>;
  hasActiveLayout: boolean;
}

export function TemplateLayerPanel({
  templates,
  activeTemplateName,
  onSelect,
  polygonCountByTemplate,
  hasActiveLayout,
}: TemplateLayerPanelProps) {
  const { t } = useI18n();

  return (
    <div className="flex h-full flex-col gap-2 rounded-md border bg-card p-3">
      <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">
        {t("gis.editor.layerPanelTitle")}
      </p>

      <div className="flex flex-col gap-1">
        {templates.map((tpl) => {
          const isActive = tpl.name === activeTemplateName;
          const count = polygonCountByTemplate[tpl.name] ?? 0;
          const color = getStrokeColor(tpl.name);

          return (
            <button
              key={tpl.id}
              type="button"
              onClick={() => onSelect(tpl.name)}
              className={`flex w-full items-center gap-2 rounded-sm px-2 py-1.5 text-left text-sm transition-colors ${
                isActive
                  ? "bg-accent text-accent-foreground font-medium"
                  : "hover:bg-accent/50"
              }`}
            >
              <span
                className="h-2.5 w-2.5 shrink-0 rounded-full"
                style={{ backgroundColor: color }}
              />
              <span className="flex-1 truncate">{tpl.name}</span>
              {count > 0 && (
                <Badge variant="secondary" className="shrink-0 text-xs">
                  {t("gis.editor.polygonCount").replace("{count}", String(count))}
                </Badge>
              )}
            </button>
          );
        })}
      </div>

      <div className="mt-auto flex flex-col gap-2">
        {!hasActiveLayout && (
          <p className="rounded-sm bg-destructive/10 px-2 py-1.5 text-xs text-destructive">
            {t("gis.editor.noActiveLayout")}
          </p>
        )}
        <p className="text-xs text-muted-foreground leading-snug">
          {t("gis.editor.instructions")}
        </p>
      </div>
    </div>
  );
}
