import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
  createLevel,
  extractF1ErrorMessage,
  hardDeleteLevel,
  listBaysGlobal,
  listLevelsGlobal,
  restoreLevel,
  softDeleteLevel,
} from "@/features/tenant/api/f1Api";
import type { BayResult, LevelResult } from "@/features/tenant/types/f1";
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
  WarehousePageShell,
  WarehouseStatusBadge,
  toActiveParam,
} from "@/features/tenant/warehouse/shared";
import type { FilterActive, WarehouseBreadcrumbItem } from "@/features/tenant/warehouse/shared";

export default function WarehouseLevelsGlobalPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const [searchParams] = useSearchParams();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");
  const bayId = searchParams.get("bayId") ?? "";
  const sideId = searchParams.get("sideId") ?? "";
  const aisleId = searchParams.get("aisleId") ?? "";
  const layoutId = searchParams.get("layoutId") ?? "";

  const canEdit = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_EDIT);
  const canSoftDelete = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_SOFT_DELETE);
  const canRestore = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_RESTORE);
  const canHardDelete = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_HARD_DELETE);

  const [items, setItems] = useState<LevelResult[]>([]);
  const [activeFilter, setActiveFilter] = useState<FilterActive>("all");
  const [pendingActive, setPendingActive] = useState<FilterActive>("all");
  const [isLoading, setIsLoading] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [formLevelNum, setFormLevelNum] = useState("1");
  const [formBayId, setFormBayId] = useState(bayId);
  const [formBays, setFormBays] = useState<BayResult[]>([]);
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [hardDeleteTarget, setHardDeleteTarget] = useState<LevelResult | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const loadData = useCallback(
    async (act: FilterActive) => {
      setIsLoading(true);
      setPageError(null);
      try {
        const result = await listLevelsGlobal(slug, {
          bayId: bayId || undefined,
          active: toActiveParam(act),
        });
        setItems(result.content);
      } catch (error) {
        setPageError(extractF1ErrorMessage(error) ?? t("warehouse.loadFailed"));
      } finally {
        setIsLoading(false);
      }
    },
    [bayId, slug, t]
  );

  useEffect(() => {
    void loadData(activeFilter);
  }, [activeFilter, loadData]);

  const breadcrumbs = useMemo(() => {
    const crumbs: WarehouseBreadcrumbItem[] = [
      {
        label: t("warehouse.breadcrumb.bays"),
        to: PATHS.TENANT.warehouseBaysGlobal(slug, {
          layoutId: layoutId || undefined,
          aisleId: aisleId || undefined,
          sideId: sideId || undefined,
        }),
      },
    ];
    crumbs.push({ label: t("warehouse.breadcrumb.levels") });
    return crumbs;
  }, [aisleId, layoutId, sideId, slug, t]);

  const openCreate = async () => {
    setFormLevelNum("1");
    setFormBayId(bayId);
    setFormError(null);
    try {
      const result = await listBaysGlobal(slug, { sideId: sideId || undefined, size: 100 });
      setFormBays(result.content);
      if (!bayId && result.content[0]) {
        setFormBayId(result.content[0].id);
      }
    } catch {
      setFormBays([]);
    }
    setFormOpen(true);
  };

  const handleSave = async () => {
    const value = Number(formLevelNum);
    if (value < 1) {
      setFormError(t("warehouse.validationLevelRequired"));
      return;
    }
    if (!formBayId) {
      setFormError(t("warehouse.formParentRequired"));
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      await createLevel(slug, formBayId, { levelNum: value });
      setFormOpen(false);
      setFormLevelNum("1");
      void loadData(activeFilter);
    } catch (error) {
      setFormError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  const handleSoftDelete = async (item: LevelResult) => {
    try {
      await softDeleteLevel(slug, item.bayId, item.id);
      void loadData(activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    }
  };

  const handleRestore = async (item: LevelResult) => {
    try {
      await restoreLevel(slug, item.bayId, item.id);
      void loadData(activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    }
  };

  const handleHardDelete = async () => {
    if (!hardDeleteTarget) return;
    setIsDeleting(true);
    try {
      await hardDeleteLevel(slug, hardDeleteTarget.bayId, hardDeleteTarget.id);
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
      title={t("warehouse.levels.pageTitle")}
      description={t("warehouse.levels.pageDescription")}
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
              {t("warehouse.levels.createAction")}
            </Button>
          ) : null}
        </div>
      }
      listTitle={t("warehouse.levels.listTitle")}
      listDescription={t("warehouse.levels.listCount", { count: String(items.length) })}
    >
      {pageError ? <p className="mb-2 text-xs text-destructive">{pageError}</p> : null}
      {isLoading ? (
        <p className="text-sm text-muted-foreground">{t("warehouse.levels.loading")}</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("warehouse.levels.empty")}</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-start">
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.levels.tableLevel")}</th>
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.tableStatus")}</th>
                <th className="py-2 text-start font-medium">{t("warehouse.tableActions")}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} className="border-b last:border-0">
                  <td className="py-2 pe-4 font-medium">{levelLabel(item.levelNum)}</td>
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
                          to={PATHS.TENANT.warehouseShelvesGlobal(slug, {
                            layoutId: layoutId || undefined,
                            aisleId: aisleId || undefined,
                            sideId: sideId || undefined,
                            bayId: bayId || item.bayId,
                            levelId: item.id,
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

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("warehouse.levels.createDialogTitle")}</DialogTitle>
            <DialogDescription>{t("warehouse.levels.createDialogDescription")}</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="level-bay">{t("warehouse.levels.formBayLabel")}</Label>
              <select
                id="level-bay"
                className="w-full rounded-none border border-input bg-background px-3 py-2 text-sm"
                value={formBayId}
                onChange={(e) => setFormBayId(e.target.value)}
              >
                {formBays.map((b) => (
                  <option key={b.id} value={b.id}>{b.bayCode}</option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="level-num">{t("warehouse.levels.levelNumLabel")}</Label>
              <Input
                id="level-num"
                type="number"
                min={1}
                value={formLevelNum}
                onChange={(e) => setFormLevelNum(e.target.value)}
              />
            </div>
            {formError ? <p className="text-xs text-destructive">{formError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={isSaving} onClick={() => setFormOpen(false)}>
              {t("warehouse.cancelAction")}
            </Button>
            <Button disabled={isSaving} onClick={() => void handleSave()}>
              {isSaving ? t("warehouse.saving") : t("warehouse.levels.createAction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={hardDeleteTarget !== null} onOpenChange={(open) => !open && setHardDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("warehouse.levels.hardDeleteDialogTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{t("warehouse.levels.hardDeleteDialogDescription")}</AlertDialogDescription>
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
