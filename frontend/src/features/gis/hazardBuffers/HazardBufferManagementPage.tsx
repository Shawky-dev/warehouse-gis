import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AlertTriangle, Download, Layers, PlusCircle, Upload } from "lucide-react";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useI18n } from "@/i18n";
import { downloadGeoJson } from "@/lib/exportGeoJson";
import { PATHS } from "@/shared/consts/paths";
import { Badge } from "@/shared/components/ui/badge";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { useFloorPlanApi } from "../floorplans/useFloorPlanApi";
import type { GeoJsonFeatureCollection } from "../zones/zonesApi";
import { HazardBufferAttributePanel } from "./HazardBufferAttributePanel";
import { HazardBufferMapView } from "./HazardBufferMapView";
import {
    fetchHazardBuffersGeoJson,
    importHazardBuffers,
    listHazardBuffers,
    updateHazardBuffer,
} from "./hazardBuffersApi";
import type { HazardBufferFeatureProps } from "./hazardBuffersApi";
import type { HazardBufferResult } from "@/features/tenant/types/gis";

export default function HazardBufferManagementPage() {
    const { t } = useI18n();
    const navigate = useNavigate();
    const { hasPermission } = useAuth();
    const canManage = hasPermission(TENANT_PERMISSIONS.GIS_HAZARD_BUFFERS_MANAGE);
    const { tenantSlug } = useParams<{ tenantSlug: string }>();
    const slug = normalizeTenantSlug(tenantSlug ?? "");

    const { config, loading: floorPlanLoading, error: floorPlanError, svgContent } = useFloorPlanApi();

    const [buffers, setBuffers] = useState<HazardBufferResult[]>([]);
    const [buffersGeoJson, setBuffersGeoJson] = useState<GeoJsonFeatureCollection<HazardBufferFeatureProps> | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [selectedBufferId, setSelectedBufferId] = useState<string | null>(null);
    const [drawPending, setDrawPending] = useState(false);
    const [pendingGeometry, setPendingGeometry] = useState<number[][][] | null>(null);
    const [editingBufferId, setEditingBufferId] = useState<string | null>(null);
    const [importing, setImporting] = useState(false);

    const importInputRef = useRef<HTMLInputElement>(null);
    const selectedBuffer = buffers.find((b) => b.id === selectedBufferId) ?? null;

    const loadAll = useCallback(async () => {
        try {
            setLoadError(null);
            const [bufferList, bufferGeo] = await Promise.all([
                listHazardBuffers(slug),
                fetchHazardBuffersGeoJson(slug),
            ]);
            setBuffers(bufferList);
            setBuffersGeoJson(bufferGeo);
        } catch {
            setLoadError(t("gis.hazardBuffers.loadFailed"));
        }
    }, [slug, t]);

    useEffect(() => {
        void loadAll();
    }, [loadAll]);

    const handleBufferSelect = useCallback((bufferId: string) => {
        setSelectedBufferId(bufferId);
        setDrawPending(false);
        setPendingGeometry(null);
    }, []);

    const handleDeselect = useCallback(() => {
        if (!drawPending) setSelectedBufferId(null);
    }, [drawPending]);

    const handleDrawComplete = useCallback((coordinates: number[][][]) => {
        setPendingGeometry(coordinates);
        setDrawPending(false);
        setSelectedBufferId(null);
    }, []);

    const handleDrawCancel = useCallback(() => {
        setDrawPending(false);
        setPendingGeometry(null);
    }, []);

    const handleCancelCreate = useCallback(() => {
        setPendingGeometry(null);
    }, []);

    const handleSaveSuccess = useCallback(async (saved: HazardBufferResult) => {
        await loadAll();
        setSelectedBufferId(saved.id);
        setPendingGeometry(null);
    }, [loadAll]);

    const handleDeleteSuccess = useCallback(async () => {
        await loadAll();
        setSelectedBufferId(null);
    }, [loadAll]);

    const handleMoveComplete = useCallback(async (bufferId: string, rings: number[][][]) => {
        const buffer = buffers.find((b) => b.id === bufferId);
        if (!buffer) return;
        try {
            await updateHazardBuffer(slug, bufferId, {
                name: buffer.name,
                notes: buffer.notes,
                coordinates: rings,
                restrictedHazardTypeIds: buffer.restrictedHazardTypes.map((ht) => ht.id),
            });
            await loadAll();
        } finally {
            setEditingBufferId(null);
        }
    }, [slug, buffers, loadAll]);

    const handleMoveCanceled = useCallback(() => {
        setEditingBufferId(null);
    }, []);

    const handleImportFile = useCallback(async (file: File) => {
        setImporting(true);
        try {
            await importHazardBuffers(slug, file);
            await loadAll();
        } catch {
            setLoadError(t("gis.hazardBuffers.importFailed"));
        } finally {
            setImporting(false);
        }
    }, [slug, loadAll, t]);

    const isLoading = floorPlanLoading;
    const hasFloorPlan = config?.hasFloorPlan && svgContent;

    return (
        <div className="flex flex-col gap-6">
            <div className="flex items-start justify-between">
                <div>
                    <h1 className="text-xl font-semibold">{t("gis.hazardBuffers.management.pageTitle")}</h1>
                    <p className="mt-1 text-sm text-muted-foreground">{t("gis.hazardBuffers.management.pageDescription")}</p>
                </div>
                {canManage && (
                    <div className="flex gap-2">
                        <Button
                            variant="outline"
                            size="sm"
                            disabled={!buffersGeoJson || buffersGeoJson.features.length === 0}
                            onClick={() => downloadGeoJson(buffersGeoJson!, "hazard-buffers.geojson")}
                        >
                            <Download className="mr-1.5 h-3.5 w-3.5" />
                            {t("gis.hazardBuffers.exportGeoJson")}
                        </Button>
                        <Button
                            variant="outline"
                            size="sm"
                            disabled={importing || !hasFloorPlan}
                            onClick={() => importInputRef.current?.click()}
                        >
                            <Upload className="mr-1.5 h-3.5 w-3.5" />
                            {importing ? t("gis.hazardBuffers.importing") : t("gis.hazardBuffers.importAction")}
                        </Button>
                        <input
                            ref={importInputRef}
                            type="file"
                            accept=".json,.geojson"
                            className="hidden"
                            onChange={(e) => {
                                const file = e.target.files?.[0];
                                if (file) void handleImportFile(file);
                                e.target.value = "";
                            }}
                        />
                        <Button
                            size="sm"
                            disabled={drawPending || !hasFloorPlan}
                            onClick={() => {
                                setDrawPending(true);
                                setSelectedBufferId(null);
                                setPendingGeometry(null);
                            }}
                        >
                            <PlusCircle className="mr-1.5 h-3.5 w-3.5" />
                            {t("gis.hazardBuffers.management.newBuffer")}
                        </Button>
                    </div>
                )}
            </div>

            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-base">
                        <Layers className="h-4 w-4" />
                        {t("gis.hazardBuffers.management.pageTitle")}
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    {isLoading ? (
                        <div className="flex h-96 items-center justify-center text-sm text-muted-foreground">
                            {t("common.loading")}
                        </div>
                    ) : null}

                    {!isLoading && (floorPlanError || loadError) ? (
                        <div className="flex h-96 items-center justify-center text-sm text-destructive">
                            {floorPlanError ?? loadError}
                        </div>
                    ) : null}

                    {!isLoading && !floorPlanError && !loadError && !hasFloorPlan ? (
                        <div className="flex h-96 flex-col items-center justify-center gap-3 text-sm text-muted-foreground">
                            <p>{t("gis.zones.noFloorPlan")}</p>
                            <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={() => navigate(PATHS.TENANT.gisFloorPlans(slug))}
                            >
                                {t("gis.zones.goToFloorPlans")} →
                            </Button>
                        </div>
                    ) : null}

                    {!isLoading && !floorPlanError && !loadError && hasFloorPlan ? (
                        <div className="flex gap-0" style={{ height: "640px" }}>
                            <div className="w-56 shrink-0 overflow-y-auto border-e">
                                {drawPending ? (
                                    <div className="p-3 text-xs text-muted-foreground">
                                        {t("gis.hazardBuffers.management.drawInstructions")}
                                    </div>
                                ) : buffers.length === 0 ? (
                                    <div className="p-3 text-xs text-muted-foreground">
                                        {t("gis.hazardBuffers.management.noBuffers")}
                                    </div>
                                ) : (
                                    <ul>
                                        {buffers.map((buffer) => (
                                            <li key={buffer.id}>
                                                <button
                                                    type="button"
                                                    onClick={() => handleBufferSelect(buffer.id)}
                                                    className={`flex w-full items-start gap-2 px-3 py-2.5 text-left text-xs hover:bg-muted ${buffer.id === selectedBufferId ? "bg-muted font-medium" : ""}`}
                                                >
                                                    <AlertTriangle className="mt-0.5 h-3 w-3 shrink-0 text-red-600" />
                                                    <div className="min-w-0">
                                                        <p className="truncate">{buffer.name}</p>
                                                        {buffer.restrictedHazardTypes.length > 0 && (
                                                            <Badge variant="secondary" className="mt-0.5 text-[10px]">
                                                                {buffer.restrictedHazardTypes.length} {t("gis.hazardBuffers.tableRestrictedTypes")}
                                                            </Badge>
                                                        )}
                                                    </div>
                                                </button>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </div>

                            <div className="flex-1 overflow-hidden">
                                <HazardBufferMapView
                                    svgContent={svgContent}
                                    anchorLon={config!.anchorLon}
                                    anchorLat={config!.anchorLat}
                                    widthMeters={config!.widthMeters}
                                    lengthMeters={config!.lengthMeters}
                                    buffersGeoJson={buffersGeoJson}
                                    selectedBufferId={selectedBufferId}
                                    drawPending={drawPending}
                                    onBufferSelect={handleBufferSelect}
                                    onDeselect={handleDeselect}
                                    onDrawComplete={handleDrawComplete}
                                    onDrawCancel={handleDrawCancel}
                                    editingBufferId={editingBufferId}
                                    onMoveComplete={handleMoveComplete}
                                    onMoveCanceled={handleMoveCanceled}
                                />
                            </div>

                            <div className="w-72 shrink-0 overflow-hidden border-s">
                                <HazardBufferAttributePanel
                                    slug={slug}
                                    buffer={selectedBuffer}
                                    pendingGeometry={pendingGeometry}
                                    canManage={canManage}
                                    onSaveSuccess={handleSaveSuccess}
                                    onDeleteSuccess={handleDeleteSuccess}
                                    onCancelCreate={handleCancelCreate}
                                    onEditShape={selectedBufferId ? () => { setEditingBufferId(selectedBufferId); } : undefined}
                                />
                            </div>
                        </div>
                    ) : null}
                </CardContent>
            </Card>
        </div>
    );
}
