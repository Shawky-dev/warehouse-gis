import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
  createSide,
  extractF1ErrorMessage,
  hardDeleteSide,
  listAislesGlobal,
  listSidesGlobal,
  restoreSide,
  softDeleteSide,
} from "@/features/tenant/api/f1Api";
import type { AisleResult, SideResult } from "@/features/tenant/types/f1";
import { PATHS } from "@/shared/consts/paths";
import { Button } from "@/shared/components/ui/button";
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
import {
  sideLabel,
  WarehousePageShell,
  WarehouseStatusBadge,
  toActiveParam,
} from "@/features/tenant/warehouse/shared";
import type { FilterActive, WarehouseBreadcrumbItem } from "@/features/tenant/warehouse/shared";

export default function WarehouseSidesGlobalPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const [searchParams] = useSearchParams();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");
  const aisleId = searchParams.get("aisleId") ?? "";
  const layoutId = searchParams.get("layoutId") ?? "";

  const canEdit = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_EDIT);
  const canSoftDelete = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_SOFT_DELETE);
  const canRestore = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_RESTORE);
  const canHardDelete = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_HARD_DELETE);

  const [items, setItems] = useState<SideResult[]>([]);
  const [activeFilter, setActiveFilter] = useState<FilterActive>("all");
  const [pendingActive, setPendingActive] = useState<FilterActive>("all");
  const [isLoading, setIsLoading] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);

  const [isFormOpen, setIsFormOpen] = useState(false);
  const [formSide, setFormSide] = useState("L");
  const [formAisleId, setFormAisleId] = useState(aisleId);
  const [formAisles, setFormAisles] = useState<AisleResult[]>([]);
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [hardDeleteTarget, setHardDeleteTarget] = useState<SideResult | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const loadData = useCallback(
    async (act: FilterActive) => {
      setIsLoading(true);
      setPageError(null);
      try {
        const result = await listSidesGlobal(slug, {
          aisleId: aisleId || undefined,
          active: toActiveParam(act),
        });
        setItems(result.content);
      } catch (error) {
        setPageError(extractF1ErrorMessage(error) ?? t("warehouse.loadFailed"));
      } finally {
        setIsLoading(false);
      }
    },
    [aisleId, slug, t]
  );

  useEffect(() => {
    void loadData(activeFilter);
  }, [activeFilter, loadData]);

  const breadcrumbs = useMemo(() => {
    const crumbs: WarehouseBreadcrumbItem[] = [
      { label: t("warehouse.breadcrumb.aisles"), to: PATHS.TENANT.warehouseAislesGlobal(slug, layoutId ? { layoutId } : undefined) },
    ];
    crumbs.push({ label: t("warehouse.breadcrumb.sides") });
    return crumbs;
  }, [layoutId, slug, t]);

  const openCreate = async () => {
    setFormSide("L");
    setFormAisleId(aisleId);
    setFormError(null);
    try {
      const result = await listAislesGlobal(slug, { layoutId: layoutId || undefined, size: 100 });
      setFormAisles(result.content);
      if (!aisleId && result.content[0]) {
        setFormAisleId(result.content[0].id);
      }
    } catch {
      setFormAisles([]);
    }
    setIsFormOpen(true);
  };

  const handleSave = async () => {
    if (!formAisleId) {
      setFormError(t("warehouse.formParentRequired"));
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      await createSide(slug, formAisleId, { side: formSide });
      setIsFormOpen(false);
      void loadData(activeFilter);
    } catch (error) {
      setFormError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  const handleSoftDelete = async (item: SideResult) => {
    try {
      await softDeleteSide(slug, item.aisleId, item.id);
      void loadData(activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    }
  };

  const handleRestore = async (item: SideResult) => {
    try {
      await restoreSide(slug, item.aisleId, item.id);
      void loadData(activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    }
  };

  const handleHardDelete = async () => {
    if (!hardDeleteTarget) return;
    setIsDeleting(true);
    try {
      await hardDeleteSide(slug, hardDeleteTarget.aisleId, hardDeleteTarget.id);
      setHardDeleteTarget(null);
      void loadData(activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <WarehousePageShell
      title={t("warehouse.sides.pageTitle")}
      description={t("warehouse.sides.pageDescription")}
      breadcrumbs={breadcrumbs}
      filterTitle={t("warehouse.filtersTitle")}
      filters={
        <div className="flex flex-wrap gap-3">
          <select
            className="rounded-none border border-input bg-background px-3 py-2 text-sm"
            aria-label={t("warehouse.activeFilterLabel")}
            value={pendingActive}
            onChange={(e) => setPendingActive(e.target.value as FilterActive)}
          >
            <option value="all">{t("warehouse.activeFilterAll")}</option>
            <option value="active">{t("warehouse.activeFilterActive")}</option>
            <option value="inactive">{t("warehouse.activeFilterInactive")}</option>
          </select>
          <Button variant="outline" onClick={() => setActiveFilter(pendingActive)}>
            {t("warehouse.applyFilters")}
          </Button>
          {canEdit ? (
            <Button className="ms-auto" onClick={() => void openCreate()}>
              {t("warehouse.sides.createAction")}
            </Button>
          ) : null}
        </div>
      }
      listTitle={t("warehouse.sides.listTitle")}
      listDescription={t("warehouse.sides.listCount", { count: String(items.length) })}
    >
      {pageError ? <p className="mb-2 text-xs text-destructive">{pageError}</p> : null}
      {isLoading ? (
        <p className="text-sm text-muted-foreground">{t("warehouse.sides.loading")}</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("warehouse.sides.empty")}</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-start">
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.sides.tableSide")}</th>
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.tableStatus")}</th>
                <th className="py-2 text-start font-medium">{t("warehouse.tableActions")}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} className="border-b last:border-0">
                  <td className="py-2 pe-4 font-medium">{sideLabel(item.side)}</td>
                  <td className="py-2 pe-4">
                    <WarehouseStatusBadge
                      active={item.active}
                      activeLabel={t("warehouse.statusActive")}
                      inactiveLabel={t("warehouse.statusInactive")}
                    />
                  </td>
                  <td className="py-2">
                    <div className="flex flex-wrap gap-1">
                      <Button asChild size="sm" variant="outline">
                        <Link
                          to={PATHS.TENANT.warehouseBaysGlobal(slug, {
                            layoutId: layoutId || undefined,
                            aisleId: aisleId || item.aisleId,
                            sideId: item.id,
                          })}
                        >
                          {t("warehouse.enterAction")}
                        </Link>
                      </Button>
                      {canSoftDelete && item.active ? (
                        <Button size="sm" variant="outline" onClick={() => void handleSoftDelete(item)}>
                          {t("warehouse.softDeleteAction")}
                        </Button>
                      ) : null}
                      {canRestore && !item.active ? (
                        <Button size="sm" variant="outline" onClick={() => void handleRestore(item)}>
                          {t("warehouse.restoreAction")}
                        </Button>
                      ) : null}
                      {canHardDelete ? (
                        <Button size="sm" variant="destructive" onClick={() => setHardDeleteTarget(item)}>
                          {t("warehouse.hardDeleteAction")}
                        </Button>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("warehouse.sides.createDialogTitle")}</DialogTitle>
            <DialogDescription>{t("warehouse.sides.createDialogDescription")}</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="side-aisle">{t("warehouse.sides.formAisleLabel")}</Label>
              <select
                id="side-aisle"
                className="w-full rounded-none border border-input bg-background px-3 py-2 text-sm"
                value={formAisleId}
                onChange={(e) => setFormAisleId(e.target.value)}
              >
                {formAisles.map((a) => (
                  <option key={a.id} value={a.id}>{a.code}{a.name ? ` — ${a.name}` : ""}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="side-select">{t("warehouse.sides.sideLabel")}</Label>
              <select
                id="side-select"
                className="w-full rounded-none border border-input bg-background px-3 py-2 text-sm"
                value={formSide}
                onChange={(e) => setFormSide(e.target.value)}
              >
                <option value="L">{t("warehouse.sides.leftOption")}</option>
                <option value="R">{t("warehouse.sides.rightOption")}</option>
              </select>
            </div>
            {formError ? <p className="text-xs text-destructive">{formError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={isSaving} onClick={() => setIsFormOpen(false)}>
              {t("warehouse.cancelAction")}
            </Button>
            <Button disabled={isSaving} onClick={() => void handleSave()}>
              {isSaving ? t("warehouse.saving") : t("warehouse.sides.createAction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={hardDeleteTarget !== null} onOpenChange={(open) => !open && setHardDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("warehouse.sides.hardDeleteDialogTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{t("warehouse.sides.hardDeleteDialogDescription")}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDeleting}>{t("warehouse.cancelAction")}</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={isDeleting}
              onClick={() => void handleHardDelete()}
            >
              {isDeleting ? t("warehouse.deleting") : t("warehouse.hardDeleteAction")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </WarehousePageShell>
  );
}
