import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";
import { Globe, Download } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Button } from "@/shared/components/ui/button";
import { useI18n } from "@/i18n";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { PATHS } from "@/shared/consts/paths";
import { downloadGeoJson } from "@/lib/exportGeoJson";
import { useFloorPlanApi } from "../floorplans/useFloorPlanApi";
import { useEditorState } from "../floorplans/useEditorState";
import { WarehouseMapView } from "../floorplans/WarehouseMapView";
import { ViewerLayerPanel } from "./ViewerLayerPanel";
import { LocationInspectPanel } from "./LocationInspectPanel";
import { fetchZonesGeoJson } from "../zones/zonesApi";
import { fetchHazardBuffersGeoJson } from "../hazardBuffers/hazardBuffersApi";
import { listDataLayers } from "@/features/gis/dataLayers/dataLayersApi";
import type { GeoJsonFeatureCollection, ZoneFeatureProps } from "../zones/zonesApi";
import type { HazardBufferFeatureProps } from "@/features/tenant/types/gis";
import type { DataLayerResult } from "@/features/gis/dataLayers/dataLayersApi";

export default function WarehouseMapPage() {
    const { t } = useI18n();
    const navigate = useNavigate();
    const location = useLocation();
    const { tenantSlug } = useParams<{ tenantSlug: string }>();
    const slug = normalizeTenantSlug(tenantSlug ?? "");

    const { config, loading, error, svgContent } = useFloorPlanApi();
    const { templates, existingPolygons, polygonCountByTemplate } = useEditorState();

    const locationState = location.state as {
        highlightAreaId?: string;
        highlightSuggestedZoneIds?: string[];
    } | null;
    const highlightAreaIds = useMemo(() => {
        const ids: string[] = [];
        if (locationState?.highlightAreaId) ids.push(locationState.highlightAreaId);
        if (locationState?.highlightSuggestedZoneIds) ids.push(...locationState.highlightSuggestedZoneIds);
        return ids;
    }, [locationState]);

    const [zonesGeoJson, setZonesGeoJson] = useState<GeoJsonFeatureCollection<ZoneFeatureProps> | null>(null);
    const [hazardBuffersGeoJson, setHazardBuffersGeoJson] = useState<GeoJsonFeatureCollection<HazardBufferFeatureProps> | null>(null);
    const [dataLayers, setDataLayers] = useState<DataLayerResult[]>([]);
    const [visibleDataLayerIds, setVisibleDataLayerIds] = useState<Set<string>>(new Set());
    const [dataLayerOpacity, setDataLayerOpacity] = useState<Record<string, number>>({});
    const [dataLayerOffset, setDataLayerOffset] = useState<Record<string, { dx: number; dy: number }>>({});
    const [activeMoveLayerId, setActiveMoveLayerId] = useState<string | null>(null);
    const [zonesVisible, setZonesVisible] = useState(true);
    const [hazardBuffersVisible, setHazardBuffersVisible] = useState(true);

    useEffect(() => {
        let cancelled = false;
        void (async () => {
            try {
                const [zones, buffers, layers] = await Promise.all([
                    fetchZonesGeoJson(slug),
                    fetchHazardBuffersGeoJson(slug),
                    listDataLayers(slug),
                ]);
                if (!cancelled) {
                    setZonesGeoJson(zones);
                    setHazardBuffersGeoJson(buffers);
                    setDataLayers(layers);
                }
            } catch {
                // overlay data is best-effort; don't block the map
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [slug]);

    const [selectedPolygon, setSelectedPolygon] = useState<{
        gisBlockId: string;
        templateName: string;
        label: string;
        layoutBlockId: string;
        positionPath: string;
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

    function handleDataLayerToggle(id: string) {
        setVisibleDataLayerIds((prev) => {
            const next = new Set(prev);
            if (next.has(id)) {
                next.delete(id);
                // clear move mode if hiding the moving layer
                setActiveMoveLayerId((cur) => cur === id ? null : cur);
            } else {
                next.add(id);
            }
            return next;
        });
    }

    function handleDataLayerOpacityChange(id: string, value: number) {
        setDataLayerOpacity((prev) => ({ ...prev, [id]: value }));
    }

    function handleMoveLayerToggle(id: string) {
        setActiveMoveLayerId((cur) => cur === id ? null : id);
    }

    function handleLayerOffsetChange(id: string, offset: { dx: number; dy: number }) {
        setDataLayerOffset((prev) => ({ ...prev, [id]: offset }));
    }

    function handleExport() {
        type Feature = { type: string; id?: unknown; geometry: unknown; properties: unknown };
        const features: Feature[] = [];
        if (zonesVisible && zonesGeoJson) {
            features.push(...(zonesGeoJson.features as Feature[]));
        }
        if (hazardBuffersVisible && hazardBuffersGeoJson) {
            features.push(...(hazardBuffersGeoJson.features as Feature[]));
        }
        for (const polygon of existingPolygons) {
            if (visibilityByTemplate[polygon.templateName] !== false) {
                features.push({
                    type: "Feature",
                    geometry: { type: "Polygon", coordinates: polygon.rings },
                    properties: {
                        gisBlockId: polygon.gisBlockId,
                        templateName: polygon.templateName,
                        label: polygon.label,
                        positionPath: polygon.positionPath,
                    },
                });
            }
        }
        downloadGeoJson({ type: "FeatureCollection", features }, "warehouse-map.geojson");
    }

    return (
        <div className="flex flex-col gap-6">
            <div className="flex items-start justify-between">
                <div>
                    <h1 className="text-xl font-semibold">{t("gis.viewer.pageTitle")}</h1>
                    <p className="mt-1 text-sm text-muted-foreground">
                        {t("gis.viewer.pageDescription")}
                    </p>
                </div>
                <Button
                    variant="outline"
                    size="sm"
                    onClick={handleExport}
                    disabled={
                        (!zonesGeoJson || zonesGeoJson.features.length === 0) &&
                        (!hazardBuffersGeoJson || hazardBuffersGeoJson.features.length === 0) &&
                        existingPolygons.length === 0
                    }
                >
                    <Download className="mr-1.5 h-3.5 w-3.5" />
                    {t("gis.viewer.exportGeoJson")}
                </Button>
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
                                    zonesVisible={zonesVisible}
                                    onZonesVisibilityToggle={() => setZonesVisible((v) => !v)}
                                    hazardBuffersVisible={hazardBuffersVisible}
                                    onHazardBuffersVisibilityToggle={() => setHazardBuffersVisible((v) => !v)}
                                    dataLayers={dataLayers}
                                    visibleDataLayerIds={visibleDataLayerIds}
                                    onDataLayerToggle={handleDataLayerToggle}
                                    dataLayerOpacity={dataLayerOpacity}
                                    onDataLayerOpacityChange={handleDataLayerOpacityChange}
                                    activeMoveLayerId={activeMoveLayerId}
                                    onMoveLayerToggle={handleMoveLayerToggle}
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
                                    zonesGeoJson={zonesGeoJson}
                                    hazardBuffersGeoJson={hazardBuffersGeoJson}
                                    highlightAreaIds={highlightAreaIds}
                                    zonesLayerVisible={zonesVisible}
                                    hazardBuffersLayerVisible={hazardBuffersVisible}
                                    dataLayers={dataLayers}
                                    visibleDataLayerIds={visibleDataLayerIds}
                                    tenantSlug={slug}
                                    dataLayerOpacity={dataLayerOpacity}
                                    dataLayerOffset={dataLayerOffset}
                                    activeMoveLayerId={activeMoveLayerId}
                                    onLayerOffsetChange={handleLayerOffsetChange}
                                    onPolygonSelect={(gisBlockId, templateName, label) => {
                                        if (gisBlockId && templateName && label) {
                                            const ep = existingPolygons.find((p) => p.gisBlockId === gisBlockId);
                                            setSelectedPolygon({
                                                gisBlockId,
                                                templateName,
                                                label,
                                                layoutBlockId: ep?.layoutBlockId ?? "",
                                                positionPath: ep?.positionPath ?? "",
                                            });
                                        } else {
                                            setSelectedPolygon(null);
                                        }
                                    }}
                                />
                            </div>
                            {selectedPolygon && (
                                <div className="w-72 shrink-0">
                                    <LocationInspectPanel
                                        key={selectedPolygon.gisBlockId}
                                        slug={slug}
                                        layoutBlockId={selectedPolygon.layoutBlockId}
                                        templateName={selectedPolygon.templateName}
                                        label={selectedPolygon.label}
                                        positionPath={selectedPolygon.positionPath}
                                        onClose={() => setSelectedPolygon(null)}
                                    />
                                </div>
                            )}
                        </div>
                    ) : null}
                </CardContent>
            </Card>
        </div>
    );
}
