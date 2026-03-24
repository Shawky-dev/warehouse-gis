import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { Map, Upload, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import { useFloorPlanApi } from "./useFloorPlanApi";
import { useEditorState } from "./useEditorState";
import { WarehouseMapView, type WarehouseMapViewHandle } from "./WarehouseMapView";
import { BlockAssignmentDialog } from "./BlockAssignmentDialog";
import { TemplateLayerPanel } from "./TemplateLayerPanel";

export default function FloorPlansPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const canManage = hasPermission(TENANT_PERMISSIONS.GIS_FLOOR_PLAN_MANAGE);

  const { config, loading, error, svgContent, upload, remove } = useFloorPlanApi();
  const {
    templates,
    activeTemplateName,
    setActiveTemplateName,
    existingPolygons,
    polygonCountByTemplate,
    totalBlocksByTemplate,
    pendingPolygon,
    pendingReassign,
    availableBlocks,
    loadingBlocks,
    hasActiveLayout,
    openAssignmentDialog,
    openReassignDialog,
    savePolygon,
    reassignPolygon,
    deletePolygon,
    clearPendingPolygon,
    clearPendingReassign,
  } = useEditorState();

  const mapViewRef = useRef<WarehouseMapViewHandle>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [uploading, setUploading] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // Editor UI state
  const [selectedPolygon, setSelectedPolygon] = useState<{
    gisBlockId: string;
    templateName: string;
    label: string;
  } | null>(null);
  const [isDrawMode, setIsDrawMode] = useState(false);
  const [visibilityByTemplate, setVisibilityByTemplate] = useState<Record<string, boolean>>({});
  const [canUndo, setCanUndo] = useState(false);

  // Initialise visibility when templates first load (don't overwrite user toggles)
  useEffect(() => {
    if (templates.length === 0) return;
    setVisibilityByTemplate((prev) => {
      if (Object.keys(prev).length > 0) return prev;
      return Object.fromEntries(templates.map((tpl) => [tpl.name, true]));
    });
  }, [templates]);

  // Keyboard shortcuts
  useEffect(() => {
    const dialogOpen = pendingPolygon !== null || pendingReassign !== null;

    function handleKeyDown(e: KeyboardEvent) {
      if (dialogOpen) return;
      // D — toggle draw mode on
      if ((e.key === "d" || e.key === "D") && !e.metaKey && !e.ctrlKey) {
        setIsDrawMode(true);
      }
      // Escape — exit draw mode
      if (e.key === "Escape") {
        setIsDrawMode(false);
      }
      // Delete / Backspace — delete the selected polygon
      if ((e.key === "Delete" || e.key === "Backspace") && selectedPolygon) {
        void deletePolygon(selectedPolygon.gisBlockId, selectedPolygon.templateName);
        setSelectedPolygon(null);
        toast.success(t("gis.editor.deleteSuccess"));
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [pendingPolygon, pendingReassign, selectedPolygon, deletePolygon, t]);

  // ── Stats ──────────────────────────────────────────────────────────────────
  const totalMapped = Object.values(polygonCountByTemplate).reduce((a, b) => a + b, 0);
  const totalBlocksAll = Object.values(totalBlocksByTemplate).reduce((a, b) => a + b, 0);
  const totalUnmapped = Math.max(0, totalBlocksAll - totalMapped);

  // ── Handlers ───────────────────────────────────────────────────────────────

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    setUploading(true);
    try {
      await upload(file);
      toast.success(t("gis.floorPlans.uploadSuccess"));
    } catch {
      toast.error(t("gis.floorPlans.uploadError"));
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  async function handleDelete() {
    setDeleting(true);
    try {
      await remove();
      toast.success(t("gis.floorPlans.deleteSuccess"));
    } catch {
      toast.error(t("gis.floorPlans.uploadError"));
    } finally {
      setDeleting(false);
    }
  }

  function handleDeleteSelected() {
    if (!selectedPolygon) return;
    void deletePolygon(selectedPolygon.gisBlockId, selectedPolygon.templateName);
    setSelectedPolygon(null);
    toast.success(t("gis.editor.deleteSuccess"));
  }

  function handleReassignSelected() {
    if (!selectedPolygon) return;
    void openReassignDialog(selectedPolygon.gisBlockId, selectedPolygon.templateName);
  }

  function handleUndo() {
    mapViewRef.current?.cancelPendingPolygon();
    clearPendingPolygon();
    setCanUndo(false);
  }

  function handleVisibilityToggle(templateName: string) {
    setVisibilityByTemplate((prev) => ({ ...prev, [templateName]: !(prev[templateName] ?? true) }));
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">{t("gis.floorPlans.pageTitle")}</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          {t("gis.floorPlans.pageDescription")}
        </p>
      </div>

      {canManage ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t("gis.floorPlans.uploadLabel")}</CardTitle>
            <CardDescription>{t("gis.floorPlans.uploadHint")}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <input
              ref={fileInputRef}
              type="file"
              accept=".svg,image/svg+xml"
              className="hidden"
              onChange={(e) => void handleFileChange(e)}
            />
            <div className="flex flex-wrap items-center gap-3">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={uploading || deleting}
                onClick={() => fileInputRef.current?.click()}
              >
                <Upload className="mr-2 h-4 w-4" />
                {uploading ? "Uploading..." : t("gis.floorPlans.uploadButton")}
              </Button>
              {config?.hasFloorPlan ? (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="text-destructive hover:text-destructive"
                  disabled={uploading || deleting}
                  onClick={() => void handleDelete()}
                >
                  <Trash2 className="mr-2 h-4 w-4" />
                  {deleting ? "Removing..." : t("gis.floorPlans.deleteButton")}
                </Button>
              ) : null}
            </div>
          </CardContent>
        </Card>
      ) : null}

      {/* Stats summary card */}
      {config?.hasFloorPlan && templates.length > 0 ? (
        <Card>
          <CardContent className="pt-4">
            <div className="grid grid-cols-3 divide-x">
              <div className="flex flex-col items-center gap-0.5 px-4 py-2">
                <span className="text-2xl font-semibold">{totalMapped}</span>
                <span className="text-xs text-muted-foreground">{t("gis.editor.statsMapped")}</span>
              </div>
              <div className="flex flex-col items-center gap-0.5 px-4 py-2">
                <span className="text-2xl font-semibold">{totalUnmapped}</span>
                <span className="text-xs text-muted-foreground">{t("gis.editor.statsUnmapped")}</span>
              </div>
              <div className="flex flex-col items-center gap-0.5 px-4 py-2">
                <span className="text-2xl font-semibold">{templates.length}</span>
                <span className="text-xs text-muted-foreground">{t("gis.editor.statsLayers")}</span>
              </div>
            </div>
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Map className="h-4 w-4" />
            {t("gis.floorPlans.mapTitle")}
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
            <div className="flex h-96 items-center justify-center text-sm text-muted-foreground">
              {t("gis.floorPlans.noFloorPlan")}
            </div>
          ) : null}

          {!loading && !error && config?.hasFloorPlan && svgContent ? (
            <div className="flex gap-4" style={{ height: "640px" }}>
              {canManage && templates.length > 0 ? (
                <div className="w-52 shrink-0">
                  <TemplateLayerPanel
                    templates={templates}
                    activeTemplateName={activeTemplateName}
                    onSelect={setActiveTemplateName}
                    polygonCountByTemplate={polygonCountByTemplate}
                    totalBlocksByTemplate={totalBlocksByTemplate}
                    hasActiveLayout={hasActiveLayout}
                    isDrawMode={isDrawMode}
                    onDrawModeChange={setIsDrawMode}
                    visibilityByTemplate={visibilityByTemplate}
                    onVisibilityToggle={handleVisibilityToggle}
                    selectedPolygon={selectedPolygon}
                    onDeleteSelected={handleDeleteSelected}
                    onReassignSelected={handleReassignSelected}
                    canUndo={canUndo}
                    onUndo={handleUndo}
                  />
                </div>
              ) : null}
              <div className="flex-1">
                <WarehouseMapView
                  ref={mapViewRef}
                  svgContent={svgContent}
                  anchorLon={config.anchorLon}
                  anchorLat={config.anchorLat}
                  widthMeters={config.widthMeters}
                  lengthMeters={config.lengthMeters}
                  editorMode={canManage}
                  templates={templates}
                  activeTemplateName={activeTemplateName}
                  existingPolygons={existingPolygons}
                  isDrawMode={isDrawMode}
                  onDrawModeChange={setIsDrawMode}
                  visibilityByTemplate={visibilityByTemplate}
                  selectedGisBlockId={selectedPolygon?.gisBlockId}
                  onPolygonSelect={(gisBlockId, templateName, label) => {
                    if (gisBlockId && templateName && label) {
                      setSelectedPolygon({ gisBlockId, templateName, label });
                    } else {
                      setSelectedPolygon(null);
                    }
                  }}
                  onPolygonComplete={(rings, templateName) => {
                    setCanUndo(true);
                    void openAssignmentDialog({ rings, templateName });
                  }}
                />
              </div>
            </div>
          ) : null}
        </CardContent>
      </Card>

      <BlockAssignmentDialog
        open={pendingPolygon !== null || pendingReassign !== null}
        templateName={pendingReassign?.templateName ?? pendingPolygon?.templateName ?? ""}
        availableBlocks={availableBlocks}
        loadingBlocks={loadingBlocks}
        currentLabel={pendingReassign !== null ? (selectedPolygon?.label ?? "") : undefined}
        onAssign={async (layoutBlockId, fullCode) => {
          if (pendingReassign !== null) {
            await reassignPolygon(
              pendingReassign.gisBlockId,
              layoutBlockId,
              fullCode,
              pendingReassign.templateName
            );
            setSelectedPolygon(null);
            toast.success(t("gis.editor.reassignSuccess").replace("{label}", fullCode));
          } else {
            await savePolygon(layoutBlockId, fullCode);
            setCanUndo(false);
            toast.success(t("gis.editor.saveSuccess").replace("{label}", fullCode));
          }
        }}
        onCancel={() => {
          if (pendingReassign !== null) {
            clearPendingReassign();
          } else {
            mapViewRef.current?.cancelPendingPolygon();
            clearPendingPolygon();
            setCanUndo(false);
          }
        }}
      />
    </div>
  );
}
