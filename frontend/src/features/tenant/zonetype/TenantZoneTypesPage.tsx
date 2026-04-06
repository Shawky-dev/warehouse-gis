import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
    createZoneType,
    deactivateZoneType,
    extractZoneTypeErrorMessage,
    hardDeleteZoneType,
    listZoneTypes,
    reactivateZoneType,
    updateZoneType,
} from "@/features/tenant/api/zoneTypeApi";
import type { ZoneTypeResult } from "@/features/tenant/types/f0";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
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

type FilterActive = "all" | "active" | "inactive";

function toActiveParam(filter: FilterActive): boolean | undefined {
    if (filter === "active") return true;
    if (filter === "inactive") return false;
    return undefined;
}

export default function TenantZoneTypesPage() {
    const { t } = useI18n();
    const { hasPermission } = useAuth();
    const { tenantSlug } = useParams<{ tenantSlug: string }>();
    const slug = normalizeTenantSlug(tenantSlug ?? "");

    const canCreate = hasPermission(TENANT_PERMISSIONS.ZONE_TYPES_CREATE);
    const canEdit = hasPermission(TENANT_PERMISSIONS.ZONE_TYPES_EDIT);
    const canDeactivate = hasPermission(TENANT_PERMISSIONS.ZONE_TYPES_DEACTIVATE);
    const canReactivate = hasPermission(TENANT_PERMISSIONS.ZONE_TYPES_REACTIVATE);
    const canHardDelete = hasPermission(TENANT_PERMISSIONS.ZONE_TYPES_HARD_DELETE);

    const [items, setItems] = useState<ZoneTypeResult[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [pageError, setPageError] = useState<string | null>(null);

    const [search, setSearch] = useState("");
    const [activeFilter, setActiveFilter] = useState<FilterActive>("all");
    const [pendingSearch, setPendingSearch] = useState("");
    const [pendingActive, setPendingActive] = useState<FilterActive>("all");

    const [isFormOpen, setIsFormOpen] = useState(false);
    const [editingItem, setEditingItem] = useState<ZoneTypeResult | null>(null);
    const [formCode, setFormCode] = useState("");
    const [formDisplayName, setFormDisplayName] = useState("");
    const [formError, setFormError] = useState<string | null>(null);
    const [isSaving, setIsSaving] = useState(false);

    const [deactivateTarget, setDeactivateTarget] = useState<ZoneTypeResult | null>(null);
    const [deleteTarget, setDeleteTarget] = useState<ZoneTypeResult | null>(null);
    const [isActioning, setIsActioning] = useState(false);

    const load = useCallback(
        async (srch: string, act: FilterActive) => {
            setIsLoading(true);
            setPageError(null);
            try {
                const result = await listZoneTypes(slug, {
                    search: srch || undefined,
                    active: toActiveParam(act),
                });
                setItems(result);
            } catch (error) {
                setPageError(extractZoneTypeErrorMessage(error) ?? t("zoneTypes.loadFailed"));
            } finally {
                setIsLoading(false);
            }
        },
        [slug, t]
    );

    useEffect(() => {
        void load(search, activeFilter);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [load]);

    const applyFilters = () => {
        setSearch(pendingSearch);
        setActiveFilter(pendingActive);
        void load(pendingSearch, pendingActive);
    };

    const openCreate = () => {
        setEditingItem(null);
        setFormCode("");
        setFormDisplayName("");
        setFormError(null);
        setIsFormOpen(true);
    };

    const openEdit = (item: ZoneTypeResult) => {
        setEditingItem(item);
        setFormCode(item.code);
        setFormDisplayName(item.displayName);
        setFormError(null);
        setIsFormOpen(true);
    };

    const handleFormSave = async () => {
        if (!formCode.trim() || !formDisplayName.trim()) {
            setFormError(t("zoneTypes.validationRequired"));
            return;
        }
        setIsSaving(true);
        setFormError(null);
        try {
            const payload = { code: formCode.trim(), displayName: formDisplayName.trim() };
            if (editingItem) {
                await updateZoneType(slug, editingItem.id, payload);
            } else {
                await createZoneType(slug, payload);
            }
            setIsFormOpen(false);
            void load(search, activeFilter);
        } catch (error) {
            setFormError(extractZoneTypeErrorMessage(error) ?? t("zoneTypes.actionFailed"));
        } finally {
            setIsSaving(false);
        }
    };

    const handleDeactivate = async () => {
        if (!deactivateTarget) return;
        setIsActioning(true);
        try {
            await deactivateZoneType(slug, deactivateTarget.id);
            setDeactivateTarget(null);
            void load(search, activeFilter);
        } catch (error) {
            setPageError(extractZoneTypeErrorMessage(error) ?? t("zoneTypes.actionFailed"));
        } finally {
            setIsActioning(false);
        }
    };

    const handleReactivate = async (item: ZoneTypeResult) => {
        try {
            await reactivateZoneType(slug, item.id);
            void load(search, activeFilter);
        } catch (error) {
            setPageError(extractZoneTypeErrorMessage(error) ?? t("zoneTypes.actionFailed"));
        }
    };

    const handleHardDelete = async () => {
        if (!deleteTarget) return;
        setIsActioning(true);
        try {
            await hardDeleteZoneType(slug, deleteTarget.id);
            setDeleteTarget(null);
            void load(search, activeFilter);
        } catch (error) {
            setPageError(extractZoneTypeErrorMessage(error) ?? t("zoneTypes.actionFailed"));
        } finally {
            setIsActioning(false);
        }
    };

    return (
        <div className="space-y-4">
            <div className="space-y-1">
                <h1 className="text-xl font-semibold">{t("zoneTypes.pageTitle")}</h1>
                <p className="text-sm text-muted-foreground">{t("zoneTypes.pageDescription")}</p>
            </div>

            <Card>
                <CardContent className="pt-4">
                    <div className="flex flex-wrap gap-3">
                        <Input
                            className="max-w-xs"
                            placeholder={t("zoneTypes.searchPlaceholder")}
                            value={pendingSearch}
                            onChange={(e) => setPendingSearch(e.target.value)}
                        />
                        <select
                            className="rounded-md border border-input bg-background px-3 py-2 text-sm"
                            value={pendingActive}
                            onChange={(e) => setPendingActive(e.target.value as FilterActive)}
                        >
                            <option value="all">{t("zoneTypes.activeFilterAll")}</option>
                            <option value="active">{t("zoneTypes.activeFilterActive")}</option>
                            <option value="inactive">{t("zoneTypes.activeFilterInactive")}</option>
                        </select>
                        <Button variant="outline" onClick={applyFilters}>
                            Apply
                        </Button>
                        {canCreate && (
                            <Button className="ms-auto" onClick={openCreate}>
                                {t("zoneTypes.createAction")}
                            </Button>
                        )}
                    </div>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle>{t("zoneTypes.pageTitle")}</CardTitle>
                    <CardDescription>{items.length} total</CardDescription>
                </CardHeader>
                <CardContent>
                    {pageError ? <p className="mb-2 text-xs text-destructive">{pageError}</p> : null}
                    {isLoading ? (
                        <p className="text-sm text-muted-foreground">{t("zoneTypes.loading")}</p>
                    ) : items.length === 0 ? (
                        <p className="text-sm text-muted-foreground">{t("zoneTypes.empty")}</p>
                    ) : (
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm">
                                <thead>
                                    <tr className="border-b">
                                        <th className="py-2 pe-4 text-start font-medium">{t("zoneTypes.tableCode")}</th>
                                        <th className="py-2 pe-4 text-start font-medium">{t("zoneTypes.tableDisplayName")}</th>
                                        <th className="py-2 pe-4 text-start font-medium">{t("zoneTypes.tableStatus")}</th>
                                        <th className="py-2 text-start font-medium">{t("zoneTypes.tableActions")}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {items.map((item) => {
                                        const isProtected = item.code === "NONE";
                                        return (
                                            <tr key={item.id} className="border-b last:border-0">
                                                <td className="py-2 pe-4 font-mono font-medium">{item.code}</td>
                                                <td className="py-2 pe-4">{item.displayName}</td>
                                                <td className="py-2 pe-4">
                                                    <span className={item.isActive ? "text-emerald-600" : "text-muted-foreground"}>
                                                        {item.isActive ? t("zoneTypes.statusActive") : t("zoneTypes.statusInactive")}
                                                    </span>
                                                </td>
                                                <td className="py-2">
                                                    <div className="flex flex-wrap gap-1">
                                                        {canEdit && !isProtected && (
                                                            <Button size="sm" variant="outline" onClick={() => openEdit(item)}>
                                                                {t("zoneTypes.editAction")}
                                                            </Button>
                                                        )}
                                                        {canDeactivate && item.isActive && !isProtected && (
                                                            <Button
                                                                size="sm"
                                                                variant="outline"
                                                                onClick={() => setDeactivateTarget(item)}
                                                            >
                                                                {t("zoneTypes.deactivateAction")}
                                                            </Button>
                                                        )}
                                                        {canReactivate && !item.isActive && !isProtected && (
                                                            <Button
                                                                size="sm"
                                                                variant="outline"
                                                                onClick={() => void handleReactivate(item)}
                                                            >
                                                                {t("zoneTypes.reactivateAction")}
                                                            </Button>
                                                        )}
                                                        {canHardDelete && !isProtected && (
                                                            <Button
                                                                size="sm"
                                                                variant="destructive"
                                                                onClick={() => setDeleteTarget(item)}
                                                            >
                                                                {t("zoneTypes.deleteAction")}
                                                            </Button>
                                                        )}
                                                        {isProtected && (
                                                            <span className="text-xs text-muted-foreground">
                                                                {t("zoneTypes.protectedHint")}
                                                            </span>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    )}

                </CardContent>
            </Card>

            {/* Create / Edit dialog */}
            <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>
                            {editingItem ? t("zoneTypes.editDialogTitle") : t("zoneTypes.createDialogTitle")}
                        </DialogTitle>
                        <DialogDescription>
                            {editingItem
                                ? t("zoneTypes.editDialogDescription")
                                : t("zoneTypes.createDialogDescription")}
                        </DialogDescription>
                    </DialogHeader>
                    <div className="space-y-3">
                        <div className="space-y-1">
                            <Label htmlFor="zt-code">{t("zoneTypes.codeLabel")}</Label>
                            <Input
                                id="zt-code"
                                value={formCode}
                                onChange={(e) => setFormCode(e.target.value.toUpperCase())}
                                placeholder="e.g. COLD_STORAGE"
                            />
                            <p className="text-xs text-muted-foreground">{t("zoneTypes.codeHint")}</p>
                        </div>
                        <div className="space-y-1">
                            <Label htmlFor="zt-display-name">{t("zoneTypes.displayNameLabel")}</Label>
                            <Input
                                id="zt-display-name"
                                value={formDisplayName}
                                onChange={(e) => setFormDisplayName(e.target.value)}
                            />
                        </div>
                        {formError ? <p className="text-xs text-destructive">{formError}</p> : null}
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setIsFormOpen(false)} disabled={isSaving}>
                            {t("zoneTypes.cancelAction")}
                        </Button>
                        <Button onClick={() => void handleFormSave()} disabled={isSaving}>
                            {isSaving ? t("zoneTypes.saving") : editingItem ? t("zoneTypes.editAction") : t("zoneTypes.createAction")}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            {/* Deactivate confirm */}
            <AlertDialog
                open={!!deactivateTarget}
                onOpenChange={(open) => { if (!open) setDeactivateTarget(null); }}
            >
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>{t("zoneTypes.deactivateDialogTitle")}</AlertDialogTitle>
                        <AlertDialogDescription>
                            {t("zoneTypes.deactivateDialogDescription")}
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isActioning}>{t("zoneTypes.cancelAction")}</AlertDialogCancel>
                        <AlertDialogAction onClick={() => void handleDeactivate()} disabled={isActioning}>
                            {t("zoneTypes.deactivateAction")}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            {/* Hard delete confirm */}
            <AlertDialog
                open={!!deleteTarget}
                onOpenChange={(open) => { if (!open) setDeleteTarget(null); }}
            >
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>{t("zoneTypes.deleteDialogTitle")}</AlertDialogTitle>
                        <AlertDialogDescription>
                            {t("zoneTypes.deleteDialogDescription")}
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isActioning}>{t("zoneTypes.cancelAction")}</AlertDialogCancel>
                        <AlertDialogAction
                            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                            onClick={() => void handleHardDelete()}
                            disabled={isActioning}
                        >
                            {t("zoneTypes.deleteAction")}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </div>
    );
}
