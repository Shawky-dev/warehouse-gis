import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useI18n } from "@/i18n";
import {
    deleteStaticHeatmap,
    extractHeatmapErrorMessage,
    listStaticHeatmaps,
    setDefaultStaticHeatmap,
    uploadStaticHeatmap,
} from "@/features/gis/heatmaps/heatmapsApi";
import type { StaticHeatmapRecord } from "@/features/tenant/types/gis";
import { Badge } from "@/shared/components/ui/badge";
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

export default function StaticHeatmapsPage() {
    const { t } = useI18n();
    const { tenantSlug } = useParams<{ tenantSlug: string }>();
    const slug = normalizeTenantSlug(tenantSlug ?? "");

    const [items, setItems] = useState<StaticHeatmapRecord[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [pageError, setPageError] = useState<string | null>(null);

    // Upload form state
    const [uploadName, setUploadName] = useState("");
    const [uploadNameError, setUploadNameError] = useState<string | null>(null);
    const [isUploading, setIsUploading] = useState(false);
    const [uploadError, setUploadError] = useState<string | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [selectedFile, setSelectedFile] = useState<File | null>(null);

    // Delete dialog state
    const [deleteTarget, setDeleteTarget] = useState<StaticHeatmapRecord | null>(null);
    const [isDeleting, setIsDeleting] = useState(false);

    // Set-default state
    const [settingDefaultId, setSettingDefaultId] = useState<string | null>(null);

    const load = useCallback(async () => {
        setIsLoading(true);
        setPageError(null);
        try {
            const result = await listStaticHeatmaps(slug);
            setItems(result);
        } catch (error) {
            setPageError(extractHeatmapErrorMessage(error) ?? t("gis.heatmaps.loadFailed"));
        } finally {
            setIsLoading(false);
        }
    }, [slug, t]);

    useEffect(() => {
        void load();
    }, [load]);

    const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0] ?? null;
        setSelectedFile(file);
        e.target.value = "";
    };

    const handleUpload = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!uploadName.trim()) {
            setUploadNameError(t("gis.heatmaps.uploadNameRequired"));
            return;
        }
        if (!selectedFile) return;
        setUploadNameError(null);
        setUploadError(null);
        setIsUploading(true);
        try {
            await uploadStaticHeatmap(slug, uploadName.trim(), selectedFile);
            setUploadName("");
            setSelectedFile(null);
            void load();
        } catch (error) {
            setUploadError(extractHeatmapErrorMessage(error) ?? t("gis.heatmaps.uploadFailed"));
        } finally {
            setIsUploading(false);
        }
    };

    const handleSetDefault = async (id: string) => {
        setSettingDefaultId(id);
        setPageError(null);
        try {
            await setDefaultStaticHeatmap(slug, id);
            void load();
        } catch (error) {
            setPageError(extractHeatmapErrorMessage(error) ?? t("gis.heatmaps.setDefaultFailed"));
        } finally {
            setSettingDefaultId(null);
        }
    };

    const handleDelete = async () => {
        if (!deleteTarget) return;
        setIsDeleting(true);
        try {
            await deleteStaticHeatmap(slug, deleteTarget.id);
            setDeleteTarget(null);
            void load();
        } catch (error) {
            setPageError(extractHeatmapErrorMessage(error) ?? t("gis.heatmaps.deleteFailed"));
        } finally {
            setIsDeleting(false);
        }
    };

    return (
        <div className="space-y-4">
            <div className="space-y-1">
                <h1 className="text-xl font-semibold">{t("gis.heatmaps.pageTitle")}</h1>
                <p className="text-sm text-muted-foreground">{t("gis.heatmaps.pageDescription")}</p>
            </div>

            {/* Upload card */}
            <Card>
                <CardHeader>
                    <CardTitle>{t("gis.heatmaps.uploadCardTitle")}</CardTitle>
                </CardHeader>
                <CardContent>
                    <form onSubmit={(e) => void handleUpload(e)} className="flex flex-wrap items-end gap-3">
                        <div className="flex flex-col gap-1">
                            <label className="text-sm font-medium" htmlFor="heatmap-name">
                                {t("gis.heatmaps.uploadNameLabel")}
                            </label>
                            <input
                                id="heatmap-name"
                                type="text"
                                value={uploadName}
                                onChange={(e) => {
                                    setUploadName(e.target.value);
                                    setUploadNameError(null);
                                }}
                                placeholder={t("gis.heatmaps.uploadNamePlaceholder")}
                                className="h-9 rounded-md border bg-background px-3 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                            />
                            {uploadNameError && (
                                <p className="text-xs text-destructive">{uploadNameError}</p>
                            )}
                        </div>
                        <div className="flex flex-col gap-1">
                            <label className="text-sm font-medium" htmlFor="heatmap-file">
                                {t("gis.heatmaps.uploadFileLabel")}
                            </label>
                            <div className="flex items-center gap-2">
                                <Button
                                    type="button"
                                    variant="outline"
                                    size="sm"
                                    onClick={() => fileInputRef.current?.click()}
                                >
                                    {selectedFile ? selectedFile.name : "Choose .tif / .tiff"}
                                </Button>
                                <input
                                    ref={fileInputRef}
                                    id="heatmap-file"
                                    type="file"
                                    accept=".tif,.tiff"
                                    className="hidden"
                                    onChange={handleFileSelect}
                                />
                            </div>
                        </div>
                        <Button
                            type="submit"
                            disabled={isUploading || !selectedFile || !uploadName.trim()}
                        >
                            {isUploading ? t("gis.heatmaps.uploading") : t("gis.heatmaps.uploadButton")}
                        </Button>
                    </form>
                    {uploadError && (
                        <p className="mt-2 text-xs text-destructive">{uploadError}</p>
                    )}
                </CardContent>
            </Card>

            {/* Table card */}
            <Card>
                <CardHeader>
                    <CardTitle>{t("gis.heatmaps.tableCardTitle")}</CardTitle>
                    <CardDescription>{items.length} total</CardDescription>
                </CardHeader>
                <CardContent>
                    {pageError && <p className="mb-2 text-xs text-destructive">{pageError}</p>}
                    {isLoading ? (
                        <p className="text-sm text-muted-foreground">{t("gis.heatmaps.loading")}</p>
                    ) : items.length === 0 ? (
                        <p className="text-sm text-muted-foreground">{t("gis.heatmaps.empty")}</p>
                    ) : (
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b">
                                        <th className="py-2 pe-4 text-start font-medium">{t("gis.heatmaps.tableName")}</th>
                                        <th className="py-2 pe-4 text-start font-medium">{t("gis.heatmaps.tableFilename")}</th>
                                        <th className="py-2 pe-4 text-start font-medium">{t("gis.heatmaps.tableDefault")}</th>
                                        <th className="py-2 pe-4 text-start font-medium">{t("gis.heatmaps.tableCreatedAt")}</th>
                                        <th className="py-2 text-start font-medium">{t("gis.heatmaps.tableActions")}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {items.map((item) => (
                                        <tr key={item.id} className="border-b last:border-0">
                                            <td className="py-2 pe-4 font-medium">{item.name}</td>
                                            <td className="py-2 pe-4 font-mono text-xs text-muted-foreground">
                                                {item.sourceFilename}
                                            </td>
                                            <td className="py-2 pe-4">
                                                {item.isDefault && (
                                                    <Badge variant="secondary">{t("gis.heatmaps.defaultBadge")}</Badge>
                                                )}
                                            </td>
                                            <td className="py-2 pe-4 text-muted-foreground">
                                                {new Date(item.createdAt).toLocaleDateString()}
                                            </td>
                                            <td className="py-2">
                                                <div className="flex items-center gap-2">
                                                    {!item.isDefault && (
                                                        <Button
                                                            size="sm"
                                                            variant="outline"
                                                            disabled={settingDefaultId === item.id}
                                                            onClick={() => void handleSetDefault(item.id)}
                                                        >
                                                            {settingDefaultId === item.id
                                                                ? t("gis.heatmaps.settingDefault")
                                                                : t("gis.heatmaps.setDefaultAction")}
                                                        </Button>
                                                    )}
                                                    <Button
                                                        size="sm"
                                                        variant="destructive"
                                                        onClick={() => setDeleteTarget(item)}
                                                    >
                                                        {t("gis.heatmaps.deleteAction")}
                                                    </Button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* Delete confirm dialog */}
            <AlertDialog
                open={!!deleteTarget}
                onOpenChange={(open) => { if (!open) setDeleteTarget(null); }}
            >
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>{t("gis.heatmaps.deleteDialogTitle")}</AlertDialogTitle>
                        <AlertDialogDescription>
                            {t("gis.heatmaps.deleteDialogDescription")}
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isDeleting}>
                            {t("gis.heatmaps.cancelAction")}
                        </AlertDialogCancel>
                        <AlertDialogAction
                            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                            onClick={() => void handleDelete()}
                            disabled={isDeleting}
                        >
                            {t("gis.heatmaps.deleteAction")}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </div>
    );
}
