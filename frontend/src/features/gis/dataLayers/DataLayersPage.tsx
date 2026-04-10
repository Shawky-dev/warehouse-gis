import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { Pencil, Trash2, Upload } from "lucide-react";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
    deleteDataLayer,
    extractDataLayerErrorMessage,
    listDataLayers,
    renameDataLayer,
    uploadDataLayer,
} from "@/features/gis/dataLayers/dataLayersApi";
import type { DataLayerResult } from "@/features/gis/dataLayers/dataLayersApi";
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
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";

export default function DataLayersPage() {
    const { t } = useI18n();
    const { hasPermission } = useAuth();
    const { tenantSlug } = useParams<{ tenantSlug: string }>();
    const slug = normalizeTenantSlug(tenantSlug ?? "");

    const canManage = hasPermission(TENANT_PERMISSIONS.GIS_DATA_LAYERS_MANAGE);

    const [items, setItems] = useState<DataLayerResult[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [pageError, setPageError] = useState<string | null>(null);

    // Upload dialog
    const [uploadOpen, setUploadOpen] = useState(false);
    const [uploadName, setUploadName] = useState("");
    const [uploadFile, setUploadFile] = useState<File | null>(null);
    const [isUploading, setIsUploading] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);

    // Rename dialog
    const [renameTarget, setRenameTarget] = useState<DataLayerResult | null>(null);
    const [renameName, setRenameName] = useState("");
    const [isRenaming, setIsRenaming] = useState(false);

    // Delete dialog
    const [deleteTarget, setDeleteTarget] = useState<DataLayerResult | null>(null);
    const [isDeleting, setIsDeleting] = useState(false);

    const load = useCallback(async () => {
        setIsLoading(true);
        setPageError(null);
        try {
            const result = await listDataLayers(slug);
            setItems(result);
        } catch (error) {
            setPageError(extractDataLayerErrorMessage(error) ?? t("gis.dataLayers.loadFailed"));
        } finally {
            setIsLoading(false);
        }
    }, [slug, t]);

    useEffect(() => {
        void load();
    }, [load]);

    const handleUpload = async () => {
        if (!uploadFile || !uploadName.trim()) return;
        setIsUploading(true);
        setPageError(null);
        try {
            await uploadDataLayer(slug, uploadName.trim(), uploadFile);
            setUploadOpen(false);
            setUploadName("");
            setUploadFile(null);
            void load();
        } catch (error) {
            setPageError(extractDataLayerErrorMessage(error) ?? t("gis.dataLayers.uploadFailed"));
        } finally {
            setIsUploading(false);
        }
    };

    const handleRename = async () => {
        if (!renameTarget || !renameName.trim()) return;
        setIsRenaming(true);
        try {
            await renameDataLayer(slug, renameTarget.id, renameName.trim());
            setRenameTarget(null);
            setRenameName("");
            void load();
        } catch (error) {
            setPageError(extractDataLayerErrorMessage(error) ?? t("gis.dataLayers.renameFailed"));
        } finally {
            setIsRenaming(false);
        }
    };

    const handleDelete = async () => {
        if (!deleteTarget) return;
        setIsDeleting(true);
        try {
            await deleteDataLayer(slug, deleteTarget.id);
            setDeleteTarget(null);
            void load();
        } catch (error) {
            setPageError(extractDataLayerErrorMessage(error) ?? t("gis.dataLayers.deleteFailed"));
        } finally {
            setIsDeleting(false);
        }
    };

    return (
        <div className="space-y-4">
            <div className="space-y-1">
                <h1 className="text-xl font-semibold">{t("gis.dataLayers.pageTitle")}</h1>
                <p className="text-sm text-muted-foreground">{t("gis.dataLayers.pageDescription")}</p>
            </div>

            {canManage && (
                <div className="flex items-center gap-2">
                    <Button onClick={() => setUploadOpen(true)}>
                        <Upload className="mr-1.5 h-4 w-4" />
                        {t("gis.dataLayers.uploadAction")}
                    </Button>
                </div>
            )}

            <Card>
                <CardHeader>
                    <CardTitle>{t("gis.dataLayers.pageTitle")}</CardTitle>
                    <CardDescription>{items.length} total</CardDescription>
                </CardHeader>
                <CardContent>
                    {pageError ? <p className="mb-2 text-xs text-destructive">{pageError}</p> : null}
                    {isLoading ? (
                        <p className="text-sm text-muted-foreground">{t("gis.dataLayers.loading")}</p>
                    ) : items.length === 0 ? (
                        <p className="text-sm text-muted-foreground">{t("gis.dataLayers.empty")}</p>
                    ) : (
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b">
                                        <th className="py-2 pe-4 text-start font-medium">{t("gis.dataLayers.tableName")}</th>
                                        <th className="py-2 pe-4 text-start font-medium">{t("gis.dataLayers.tableFileName")}</th>
                                        <th className="py-2 pe-4 text-start font-medium">{t("gis.dataLayers.tableCreatedAt")}</th>
                                        {canManage && (
                                            <th className="py-2 text-start font-medium">{t("gis.dataLayers.tableActions")}</th>
                                        )}
                                    </tr>
                                </thead>
                                <tbody>
                                    {items.map((item) => (
                                        <tr key={item.id} className="border-b last:border-0">
                                            <td className="py-2 pe-4 font-medium">{item.name}</td>
                                            <td className="py-2 pe-4 font-mono text-xs text-muted-foreground">
                                                {item.fileName}
                                            </td>
                                            <td className="py-2 pe-4 text-muted-foreground">
                                                {item.createdAt
                                                    ? new Date(item.createdAt).toLocaleDateString()
                                                    : "—"}
                                            </td>
                                            {canManage && (
                                                <td className="py-2">
                                                    <div className="flex items-center gap-1">
                                                        <Button
                                                            size="sm"
                                                            variant="outline"
                                                            onClick={() => {
                                                                setRenameTarget(item);
                                                                setRenameName(item.name);
                                                            }}
                                                        >
                                                            <Pencil className="h-3.5 w-3.5" />
                                                        </Button>
                                                        <Button
                                                            size="sm"
                                                            variant="destructive"
                                                            onClick={() => setDeleteTarget(item)}
                                                        >
                                                            <Trash2 className="h-3.5 w-3.5" />
                                                        </Button>
                                                    </div>
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

            {/* Upload dialog */}
            <Dialog open={uploadOpen} onOpenChange={(open) => { if (!open) { setUploadOpen(false); setUploadName(""); setUploadFile(null); } }}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>{t("gis.dataLayers.uploadDialogTitle")}</DialogTitle>
                        <DialogDescription>{t("gis.dataLayers.uploadDialogDescription")}</DialogDescription>
                    </DialogHeader>
                    <div className="space-y-4 py-2">
                        <div className="space-y-1.5">
                            <Label htmlFor="layer-name">{t("gis.dataLayers.nameLabel")}</Label>
                            <Input
                                id="layer-name"
                                placeholder={t("gis.dataLayers.namePlaceholder")}
                                value={uploadName}
                                onChange={(e) => setUploadName(e.target.value)}
                            />
                        </div>
                        <div className="space-y-1.5">
                            <Label htmlFor="layer-file">{t("gis.dataLayers.fileLabel")}</Label>
                            <Input
                                id="layer-file"
                                ref={fileInputRef}
                                type="file"
                                accept=".png,.jpg,.jpeg"
                                onChange={(e) => setUploadFile(e.target.files?.[0] ?? null)}
                            />
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setUploadOpen(false)} disabled={isUploading}>
                            {t("gis.dataLayers.cancelAction")}
                        </Button>
                        <Button
                            onClick={() => void handleUpload()}
                            disabled={isUploading || !uploadFile || !uploadName.trim()}
                        >
                            {isUploading ? t("gis.dataLayers.uploading") : t("gis.dataLayers.uploadAction")}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Rename dialog */}
            <Dialog open={!!renameTarget} onOpenChange={(open) => { if (!open) setRenameTarget(null); }}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>{t("gis.dataLayers.renameDialogTitle")}</DialogTitle>
                        <DialogDescription>{t("gis.dataLayers.renameDialogDescription")}</DialogDescription>
                    </DialogHeader>
                    <div className="space-y-1.5 py-2">
                        <Label htmlFor="rename-input">{t("gis.dataLayers.newNameLabel")}</Label>
                        <Input
                            id="rename-input"
                            value={renameName}
                            onChange={(e) => setRenameName(e.target.value)}
                        />
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setRenameTarget(null)} disabled={isRenaming}>
                            {t("gis.dataLayers.cancelAction")}
                        </Button>
                        <Button
                            onClick={() => void handleRename()}
                            disabled={isRenaming || !renameName.trim()}
                        >
                            {isRenaming ? "..." : t("gis.dataLayers.saveAction")}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Delete confirm */}
            <AlertDialog
                open={!!deleteTarget}
                onOpenChange={(open) => { if (!open) setDeleteTarget(null); }}
            >
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>{t("gis.dataLayers.deleteDialogTitle")}</AlertDialogTitle>
                        <AlertDialogDescription>
                            {t("gis.dataLayers.deleteDialogDescription")}
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isDeleting}>
                            {t("gis.dataLayers.cancelAction")}
                        </AlertDialogCancel>
                        <AlertDialogAction
                            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                            onClick={() => void handleDelete()}
                            disabled={isDeleting}
                        >
                            {t("gis.dataLayers.deleteAction")}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </div>
    );
}
