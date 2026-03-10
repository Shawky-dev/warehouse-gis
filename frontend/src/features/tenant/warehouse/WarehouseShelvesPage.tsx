import { useCallback, useEffect, useMemo, useState } from "react";
import { useLocation, useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
  createShelf,
  extractF1ErrorMessage,
  hardDeleteShelf,
  listShelves,
  restoreShelf,
  softDeleteShelf,
} from "@/features/tenant/api/f1Api";
import type { ShelfResult, WarehouseAncestorState } from "@/features/tenant/types/f1";
import { PATHS } from "@/shared/consts/paths";
import { Button } from "@/shared/components/ui/button";
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
import {
  levelLabel,
  sideLabel,
  WarehousePageShell,
  WarehouseStatusBadge,
  toActiveParam,
} from "@/features/tenant/warehouse/shared";
import type { FilterActive } from "@/features/tenant/warehouse/shared";

export default function WarehouseShelvesPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const location = useLocation();
  const state = location.state as WarehouseAncestorState | undefined;
  const { tenantSlug, levelId } = useParams<{ tenantSlug: string; levelId: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");
  const currentLevelId = levelId ?? "";

  const canEdit = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_EDIT);
  const canSoftDelete = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_SOFT_DELETE);
  const canRestore = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_RESTORE);
  const canHardDelete = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_HARD_DELETE);

  const [items, setItems] = useState<ShelfResult[]>([]);
  const [activeFilter, setActiveFilter] = useState<FilterActive>("all");
  const [pendingActive, setPendingActive] = useState<FilterActive>("all");
  const [isLoading, setIsLoading] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [formShelfNum, setFormShelfNum] = useState("1");
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [hardDeleteTarget, setHardDeleteTarget] = useState<ShelfResult | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const loadData = useCallback(
    async (act: FilterActive) => {
      setIsLoading(true);
      setPageError(null);
      try {
        const result = await listShelves(slug, currentLevelId, toActiveParam(act));
        setItems(result.content);
      } catch (error) {
        setPageError(extractF1ErrorMessage(error) ?? t("warehouse.loadFailed"));
      } finally {
        setIsLoading(false);
      }
    },
    [currentLevelId, slug, t]
  );

  useEffect(() => {
    if (currentLevelId) {
      void loadData(activeFilter);
    }
  }, [activeFilter, currentLevelId, loadData]);

  const breadcrumbs = useMemo(
    () => [
      { label: t("warehouse.breadcrumb.layouts"), to: PATHS.TENANT.warehouseLayouts(slug) },
      {
        label: state?.layout?.code || t("warehouse.breadcrumb.layout"),
        to: state?.layout?.id ? PATHS.TENANT.warehouseAisles(slug, state.layout.id) : undefined,
      },
      {
        label: state?.aisle?.code || t("warehouse.breadcrumb.aisle"),
        to: state?.aisle?.id ? PATHS.TENANT.warehouseSides(slug, state.aisle.id) : undefined,
      },
      {
        label: state?.side?.side ? sideLabel(state.side.side) : t("warehouse.breadcrumb.side"),
        to: state?.side?.id ? PATHS.TENANT.warehouseBays(slug, state.side.id) : undefined,
      },
      {
        label: state?.bay?.code || t("warehouse.breadcrumb.bay"),
        to: state?.bay?.id ? PATHS.TENANT.warehouseLevels(slug, state.bay.id) : undefined,
      },
      {
        label: state?.level?.levelNum ? levelLabel(state.level.levelNum) : t("warehouse.breadcrumb.level"),
      },
      {
        label: t("warehouse.breadcrumb.shelves"),
      },
    ],
    [slug, state, t]
  );

  const handleSave = async () => {
    const value = Number(formShelfNum);
    if (value < 1) {
      setFormError(t("warehouse.validationShelfRequired"));
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      await createShelf(slug, currentLevelId, { shelfNum: value });
      setFormOpen(false);
      setFormShelfNum("1");
      void loadData(activeFilter);
    } catch (error) {
      setFormError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  const handleSoftDelete = async (item: ShelfResult) => {
    try {
      await softDeleteShelf(slug, currentLevelId, item.id);
      void loadData(activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    }
  };

  const handleRestore = async (item: ShelfResult) => {
    try {
      await restoreShelf(slug, currentLevelId, item.id);
      void loadData(activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    }
  };

  const handleHardDelete = async () => {
    if (!hardDeleteTarget) return;
    setIsDeleting(true);
    try {
      await hardDeleteShelf(slug, currentLevelId, hardDeleteTarget.id);
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
      title={t("warehouse.shelves.pageTitle")}
      description={t("warehouse.shelves.pageDescription")}
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
            <Button className="ms-auto" onClick={() => setFormOpen(true)}>
              {t("warehouse.shelves.createAction")}
            </Button>
          ) : null}
        </div>
      }
      listTitle={t("warehouse.shelves.listTitle")}
      listDescription={t("warehouse.shelves.listCount", { count: String(items.length) })}
    >
      {pageError ? <p className="mb-2 text-xs text-destructive">{pageError}</p> : null}
      {isLoading ? (
        <p className="text-sm text-muted-foreground">{t("warehouse.shelves.loading")}</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("warehouse.shelves.empty")}</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-start">
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.shelves.tableShelf")}</th>
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.shelves.tableLocationCode")}</th>
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.tableStatus")}</th>
                <th className="py-2 text-start font-medium">{t("warehouse.tableActions")}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} className="border-b last:border-0">
                  <td className="py-2 pe-4 font-medium">{item.shelfNum}</td>
                  <td className="py-2 pe-4">{item.locationCode}</td>
                  <td className="py-2 pe-4">
                    <WarehouseStatusBadge
                      active={item.active}
                      activeLabel={t("warehouse.statusActive")}
                      inactiveLabel={t("warehouse.statusInactive")}
                    />
                  </td>
                  <td className="py-2">
                    <div className="flex flex-wrap gap-1">
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

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("warehouse.shelves.createDialogTitle")}</DialogTitle>
            <DialogDescription>{t("warehouse.shelves.createDialogDescription")}</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="shelf-num">{t("warehouse.shelves.shelfNumLabel")}</Label>
              <Input
                id="shelf-num"
                type="number"
                min={1}
                value={formShelfNum}
                onChange={(e) => setFormShelfNum(e.target.value)}
              />
            </div>
            {formError ? <p className="text-xs text-destructive">{formError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={isSaving} onClick={() => setFormOpen(false)}>
              {t("warehouse.cancelAction")}
            </Button>
            <Button disabled={isSaving} onClick={() => void handleSave()}>
              {isSaving ? t("warehouse.saving") : t("warehouse.shelves.createAction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={hardDeleteTarget !== null} onOpenChange={(open) => !open && setHardDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("warehouse.shelves.hardDeleteDialogTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{t("warehouse.shelves.hardDeleteDialogDescription")}</AlertDialogDescription>
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
