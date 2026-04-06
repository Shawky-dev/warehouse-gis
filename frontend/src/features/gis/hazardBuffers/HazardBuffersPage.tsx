import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { Download } from "lucide-react";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
    deleteHazardBuffer,
    extractHazardBufferErrorMessage,
    fetchHazardBuffersGeoJson,
    importHazardBuffers,
    listHazardBuffers,
} from "@/features/gis/hazardBuffers/hazardBuffersApi";
import type { HazardBufferResult } from "@/features/tenant/types/gis";
import { downloadGeoJson } from "@/lib/exportGeoJson";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/shared/components/ui/alert-dialog";

export default function HazardBuffersPage() {
    const { t } = useI18n();
    const { hasPermission } = useAuth();
    const { tenantSlug } = useParams<{ tenantSlug: string }>();
    const slug = normalizeTenantSlug(tenantSlug ?? "");

    const canManage = hasPermission(TENANT_PERMISSIONS.GIS_HAZARD_BUFFERS_MANAGE);

    const [items, setItems] = useState<HazardBufferResult[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [pageError, setPageError] = useState<string | null>(null);
    const [isImporting, setIsImporting] = useState(false);
    const [isExporting, setIsExporting] = useState(false);
    const [deleteTarget, setDeleteTarget] = useState<HazardBufferResult | null>(null);
    const [isDeleting, setIsDeleting] = useState(false);

    const fileInputRef = useRef<HTMLInputElement>(null);

    const load = useCallback(async () => {
        setIsLoading(true);
        setPageError(null);
        try {
            const result = await listHazardBuffers(slug);
            setItems(result);
        } catch (error) {
            setPageError(extractHazardBufferErrorMessage(error) ?? t("gis.hazardBuffers.loadFailed"));
        } finally {
            setIsLoading(false);
        }
    }, [slug, t]);

    useEffect(() => {
        void load();
    }, [load]);

    const handleImportClick = () => {
        fileInputRef.current?.click();
    };

    const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        e.target.value = "";
        setIsImporting(true);
        setPageError(null);
        try {
            await importHazardBuffers(slug, file);
            void load();
        } catch (error) {
            setPageError(extractHazardBufferErrorMessage(error) ?? t("gis.hazardBuffers.importFailed"));
        } finally {
            setIsImporting(false);
        }
    };

    const handleExport = async () => {
        setIsExporting(true);
        try {
            const geoJson = await fetchHazardBuffersGeoJson(slug);
            downloadGeoJson(geoJson, "hazard-buffers.geojson");
        } catch {
            // export failure is non-critical
        } finally {
            setIsExporting(false);
        }
    };

    const handleDelete = async () => {
        if (!deleteTarget) return;
        setIsDeleting(true);
        try {
            await deleteHazardBuffer(slug, deleteTarget.id);
            setDeleteTarget(null);
            void load();
        } catch (error) {
            setPageError(extractHazardBufferErrorMessage(error) ?? t("gis.hazardBuffers.deleteFailed"));
        } finally {
            setIsDeleting(false);
        }
    };

    return (
        <div className="space-y-4">
            <div className="space-y-1">
                <h1 className="text-xl font-semibold">{t("gis.hazardBuffers.pageTitle")}</h1>
                <p className="text-sm text-muted-foreground">{t("gis.hazardBuffers.pageDescription")}</p>
            </div>

            <div className="flex items-center gap-2">
                <Button
                    variant="outline"
                    disabled={items.length === 0 || isExporting}
                    onClick={() => void handleExport()}
                >
                    <Download className="mr-1.5 h-4 w-4" />
                    {isExporting ? "..." : t("gis.hazardBuffers.exportGeoJson")}
                </Button>
                {canManage && (
                    <>
                        <Button onClick={handleImportClick} disabled={isImporting}>
                            {isImporting ? t("gis.hazardBuffers.importing") : t("gis.hazardBuffers.importAction")}
                        </Button>
                        <input
                            ref={fileInputRef}
                            type="file"
                            accept=".json,.geojson"
                            className="hidden"
                            onChange={(e) => void handleFileChange(e)}
                        />
                    </>
                )}
            </div>

            <Card>
                <CardHeader>
                    <CardTitle>{t("gis.hazardBuffers.pageTitle")}</CardTitle>
                    <CardDescription>{items.length} total</CardDescription>
                </CardHeader>
                <CardContent>
                    {pageError ? <p className="mb-2 text-xs text-destructive">{pageError}</p> : null}
                    {isLoading ? (
                        <p className="text-sm text-muted-foreground">{t("gis.hazardBuffers.loading")}</p>
                    ) : items.length === 0 ? (
                        <p className="text-sm text-muted-foreground">{t("gis.hazardBuffers.empty")}</p>
                    ) : (
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b">
                                        <th className="py-2 pe-4 text-start font-medium">{t("gis.hazardBuffers.tableName")}</th>
                                        <th className="py-2 pe-4 text-start font-medium">{t("gis.hazardBuffers.tableRestrictedTypes")}</th>
                                        <th className="py-2 pe-4 text-start font-medium">{t("gis.hazardBuffers.tableImportedAt")}</th>
                                        <th className="py-2 pe-4 text-start font-medium">{t("gis.hazardBuffers.tableBatchId")}</th>
                                        {canManage && (
                                            <th className="py-2 text-start font-medium">{t("gis.hazardBuffers.tableActions")}</th>
                                        )}
                                    </tr>
                                </thead>
                                <tbody>
                                    {items.map((item) => (
                                        <tr key={item.id} className="border-b last:border-0">
                                            <td className="py-2 pe-4 font-medium">{item.name}</td>
                                            <td className="py-2 pe-4">
                                                <div className="flex flex-wrap gap-1">
                                                    {item.restrictedHazardTypes.length === 0 ? (
                                                        <span className="text-muted-foreground">—</span>
                                                    ) : (
                                                        item.restrictedHazardTypes.map((ht) => (
                                                            <span
                                                                key={ht.id}
                                                                className="rounded bg-red-100 px-1.5 py-0.5 text-xs font-mono text-red-700"
                                                            >
                                                                {ht.code}
                                                            </span>
                                                        ))
                                                    )}
                                                </div>
                                            </td>
                                            <td className="py-2 pe-4 text-muted-foreground">
                                                {item.importedAt
                                                    ? new Date(item.importedAt).toLocaleDateString()
                                                    : "—"}
                                            </td>
                                            <td className="py-2 pe-4 text-muted-foreground font-mono text-xs">
                                                {item.importBatchId
                                                    ? item.importBatchId.slice(0, 8) + "…"
                                                    : "—"}
                                            </td>
                                            {canManage && (
                                                <td className="py-2">
                                                    <Button
                                                        size="sm"
                                                        variant="destructive"
                                                        onClick={() => setDeleteTarget(item)}
                                                    >
                                                        {t("gis.hazardBuffers.deleteAction")}
                                                    </Button>
                                                </td>
                                            )}
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* Delete confirm */}
            <AlertDialog
                open={!!deleteTarget}
                onOpenChange={(open) => { if (!open) setDeleteTarget(null); }}
            >
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>{t("gis.hazardBuffers.deleteDialogTitle")}</AlertDialogTitle>
                        <AlertDialogDescription>
                            {t("gis.hazardBuffers.deleteDialogDescription")}
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isDeleting}>
                            {t("gis.hazardBuffers.cancelAction")}
                        </AlertDialogCancel>
                        <AlertDialogAction
                            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                            onClick={() => void handleDelete()}
                            disabled={isDeleting}
                        >
                            {t("gis.hazardBuffers.deleteAction")}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </div>
    );
}
