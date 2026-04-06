import { useEffect, useState, useCallback, useRef } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Layers, PlusCircle, Upload, Shield, AlertTriangle, Download } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Button } from "@/shared/components/ui/button";
import { Badge } from "@/shared/components/ui/badge";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { PATHS } from "@/shared/consts/paths";
import { downloadGeoJson } from "@/lib/exportGeoJson";
import { useFloorPlanApi } from "../floorplans/useFloorPlanApi";
import { ZoneMapView } from "./ZoneMapView";
import { ZoneAttributePanel } from "./ZoneAttributePanel";
import {
    listZones,
    importZones,
    fetchZonesGeoJson,
    fetchLocationsGeoJson,
    updateZone,
} from "./zonesApi";
import type {
    ZoneRecord,
    GeoJsonFeatureCollection,
    ZoneFeatureProps,
    LocationFeatureProps,
} from "./zonesApi";

const ACTION_ICON = {
    BLOCK: Shield,
    WARN: AlertTriangle,
} as const;

export default function ZoneManagementPage() {
    const { t } = useI18n();
    const navigate = useNavigate();
    const { hasPermission } = useAuth();
    const canManage = hasPermission(TENANT_PERMISSIONS.GIS_ZONES_MANAGE);
    const { tenantSlug } = useParams<{ tenantSlug: string }>();
    const slug = normalizeTenantSlug(tenantSlug ?? "");

    const { config, loading: floorPlanLoading, error: floorPlanError, svgContent } = useFloorPlanApi();

    const [zones, setZones] = useState<ZoneRecord[]>([]);
    const [zonesGeoJson, setZonesGeoJson] = useState<GeoJsonFeatureCollection<ZoneFeatureProps> | null>(null);
    const [locationsGeoJson, setLocationsGeoJson] = useState<GeoJsonFeatureCollection<LocationFeatureProps> | null>(null);
    const [loadError, setLoadError] = useState<string | null>(null);

    const [selectedZoneId, setSelectedZoneId] = useState<string | null>(null);
    const [drawPending, setDrawPending] = useState(false);
    const [pendingGeometry, setPendingGeometry] = useState<number[][][] | null>(null);

    const [editingZoneId, setEditingZoneId] = useState<string | null>(null);

    const [importing, setImporting] = useState(false);
    const importInputRef = useRef<HTMLInputElement>(null);

    const selectedZone = zones.find((z) => z.id === selectedZoneId) ?? null;

    const loadAll = useCallback(async () => {
        try {
            const [zoneList, zonesGeo, locsGeo] = await Promise.all([
                listZones(slug),
                fetchZonesGeoJson(slug),
                fetchLocationsGeoJson(slug),
            ]);
            setZones(zoneList);
            setZonesGeoJson(zonesGeo);
            setLocationsGeoJson(locsGeo);
        } catch {
            setLoadError(t("gis.zones.loadError"));
        }
    }, [slug, t]);

    // Initial load
    useEffect(() => {
        let cancelled = false;
        void loadAll().then(() => { if (cancelled) return; });
        return () => { cancelled = true; };
    }, [loadAll]);

    const handleZoneSelect = useCallback((zoneId: string) => {
        setSelectedZoneId(zoneId);
        setDrawPending(false);
        setPendingGeometry(null);
    }, []);

    const handleDeselect = useCallback(() => {
        if (!drawPending) setSelectedZoneId(null);
    }, [drawPending]);

    const handleDrawComplete = useCallback((coordinates: number[][][]) => {
        setPendingGeometry(coordinates);
        setDrawPending(false);
        setSelectedZoneId(null);
    }, []);

    const handleDrawCancel = useCallback(() => {
        setDrawPending(false);
        setPendingGeometry(null);
    }, []);

    const handleCancelCreate = useCallback(() => {
        setPendingGeometry(null);
    }, []);

    const handleSaveSuccess = useCallback(async (saved: ZoneRecord) => {
        // Re-fetch to keep list + GeoJSON in sync
        await loadAll();
        setSelectedZoneId(saved.id);
        setPendingGeometry(null);
    }, [loadAll]);

    const handleDeleteSuccess = useCallback(async () => {
        await loadAll();
        setSelectedZoneId(null);
    }, [loadAll]);

    const handleMoveComplete = useCallback(async (zoneId: string, rings: number[][][]) => {
        const zone = zones.find((z) => z.id === zoneId);
        if (!zone) return;
        try {
            await updateZone(slug, zoneId, {
                name: zone.name,
                description: zone.description,
                violationAction: zone.violationAction,
                categoryRules: zone.categoryRules,
                coordinates: rings,
            });
            await loadAll();
        } finally {
            setEditingZoneId(null);
        }
    }, [slug, zones, loadAll]);

    const handleMoveCanceled = useCallback(() => {
        setEditingZoneId(null);
    }, []);

    // ArcGIS Pro GeoJSON import
    const handleImportFile = useCallback(async (file: File) => {
        setImporting(true);
        try {
            const text = await file.text();
            const parsed = JSON.parse(text) as { features?: unknown[] };
            const features = parsed.features ?? [];
            await importZones(slug, features);
            await loadAll();
        } catch {
            /* silently ignore – could add a toast here */
        } finally {
            setImporting(false);
        }
    }, [slug, loadAll]);

    const isLoading = floorPlanLoading;
    const hasFloorPlan = config?.hasFloorPlan && svgContent;

    return (
        <div className="flex flex-col gap-6">
            <div className="flex items-start justify-between">
                <div>
                    <h1 className="text-xl font-semibold">{t("gis.zones.pageTitle")}</h1>
                    <p className="mt-1 text-sm text-muted-foreground">{t("gis.zones.pageDescription")}</p>
                </div>
                {canManage && (
                    <div className="flex gap-2">
                        <Button
                            variant="outline"
                            size="sm"
                            disabled={!zonesGeoJson || zonesGeoJson.features.length === 0}
                            onClick={() => downloadGeoJson(zonesGeoJson!, "zones.geojson")}
                        >
                            <Download className="mr-1.5 h-3.5 w-3.5" />
                            {t("gis.zones.exportGeoJson")}
                        </Button>
                        <Button
                            variant="outline"
                            size="sm"
                            disabled={importing || !hasFloorPlan}
                            onClick={() => importInputRef.current?.click()}
                        >
                            <Upload className="mr-1.5 h-3.5 w-3.5" />
                            {importing ? t("gis.zones.importing") : t("gis.zones.importZones")}
                        </Button>
                        <input
                            ref={importInputRef}
                            type="file"
                            accept=".json,.geojson"
                            className="hidden"
                            onChange={(e) => {
                                const f = e.target.files?.[0];
                                if (f) void handleImportFile(f);
                                e.target.value = "";
                            }}
                        />
                        <Button
                            size="sm"
                            disabled={drawPending || !hasFloorPlan}
                            onClick={() => {
                                setDrawPending(true);
                                setSelectedZoneId(null);
                                setPendingGeometry(null);
                            }}
                        >
                            <PlusCircle className="mr-1.5 h-3.5 w-3.5" />
                            {t("gis.zones.newZone")}
                        </Button>
                    </div>
                )}
            </div>

            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-base">
                        <Layers className="h-4 w-4" />
                        {t("gis.zones.pageTitle")}
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
                            {/* Zone list sidebar */}
                            <div className="w-56 shrink-0 overflow-y-auto border-e">
                                {drawPending ? (
                                    <div className="p-3 text-xs text-muted-foreground">
                                        {t("gis.zones.drawInstructions")}
                                    </div>
                                ) : zones.length === 0 ? (
                                    <div className="p-3 text-xs text-muted-foreground">
                                        {t("gis.zones.noZones")}
                                    </div>
                                ) : (
                                    <ul>
                                        {zones.map((z) => {
                                            const ActionIcon = ACTION_ICON[z.violationAction];
                                            return (
                                                <li key={z.id}>
                                                    <button
                                                        type="button"
                                                        onClick={() => handleZoneSelect(z.id)}
                                                        className={`flex w-full items-start gap-2 px-3 py-2.5 text-left text-xs hover:bg-muted ${z.id === selectedZoneId ? "bg-muted font-medium" : ""}`}
                                                    >
                                                        <ActionIcon className="mt-0.5 h-3 w-3 shrink-0 text-muted-foreground" />
                                                        <div className="min-w-0">
                                                            <p className="truncate">{z.name}</p>
                                                            {z.categoryRules.length > 0 && (
                                                                <Badge variant="secondary" className="mt-0.5 text-[10px]">
                                                                    {z.categoryRules.length} {t("gis.zones.rules")}
                                                                </Badge>
                                                            )}
                                                        </div>
                                                    </button>
                                                </li>
                                            );
                                        })}
                                    </ul>
                                )}
                            </div>

                            {/* Map */}
                            <div className="flex-1 overflow-hidden">
                                <ZoneMapView
                                    svgContent={svgContent}
                                    anchorLon={config!.anchorLon}
                                    anchorLat={config!.anchorLat}
                                    widthMeters={config!.widthMeters}
                                    lengthMeters={config!.lengthMeters}
                                    zonesGeoJson={zonesGeoJson}
                                    locationsGeoJson={locationsGeoJson}
                                    selectedZoneId={selectedZoneId}
                                    drawPending={drawPending}
                                    onZoneSelect={handleZoneSelect}
                                    onDeselect={handleDeselect}
                                    onDrawComplete={handleDrawComplete}
                                    onDrawCancel={handleDrawCancel}
                                    editingZoneId={editingZoneId}
                                    onMoveComplete={handleMoveComplete}
                                    onMoveCanceled={handleMoveCanceled}
                                />
                            </div>

                            {/* Detail panel */}
                            <div className="w-72 shrink-0 overflow-hidden border-s">
                                <ZoneAttributePanel
                                    slug={slug}
                                    zone={selectedZone}
                                    pendingGeometry={pendingGeometry}
                                    canManage={canManage}
                                    onSaveSuccess={handleSaveSuccess}
                                    onDeleteSuccess={handleDeleteSuccess}
                                    onCancelCreate={handleCancelCreate}
                                    onEditShape={selectedZoneId ? () => { setEditingZoneId(selectedZoneId); } : undefined}
                                />
                            </div>
                        </div>
                    ) : null}
                </CardContent>
            </Card>
        </div>
    );
}

