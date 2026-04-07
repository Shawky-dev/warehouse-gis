import type { ComponentType } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Box, Eye, EyeOff, ImageIcon, Layers, MapPin, ShieldAlert, X } from "lucide-react";
import * as LucideIcons from "lucide-react";
import { Badge } from "@/shared/components/ui/badge";
import { Button } from "@/shared/components/ui/button";
import { useI18n } from "@/i18n";
import { PATHS } from "@/shared/consts/paths";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { getTemplateStroke } from "../floorplans/templateColors";
import type { EditorTemplate } from "../floorplans/useEditorState";

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
}: ViewerLayerPanelProps) {
    const { t } = useI18n();
    const navigate = useNavigate();
    const { tenantSlug } = useParams<{ tenantSlug: string }>();
    const slug = normalizeTenantSlug(tenantSlug ?? "");

    return (
        <div className="flex h-full flex-col gap-2 rounded-md border bg-card p-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                {t("gis.viewer.layerPanelTitle")}
            </p>

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
                            LucideIcons as unknown as Record<string, ComponentType<{ className?: string }>>
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
