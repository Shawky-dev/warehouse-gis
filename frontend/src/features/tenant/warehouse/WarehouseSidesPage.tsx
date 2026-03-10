import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
  createSide,
  extractF1ErrorMessage,
  hardDeleteSide,
  listSides,
  restoreSide,
  softDeleteSide,
} from "@/features/tenant/api/f1Api";
import type { SideResult, WarehouseAncestorState } from "@/features/tenant/types/f1";
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
  buildAncestorState,
  sideLabel,
  WarehousePageShell,
  WarehouseStatusBadge,
  toActiveParam,
} from "@/features/tenant/warehouse/shared";
import type { FilterActive } from "@/features/tenant/warehouse/shared";

export default function WarehouseSidesPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const location = useLocation();
  const state = location.state as WarehouseAncestorState | undefined;
  const { tenantSlug, aisleId } = useParams<{ tenantSlug: string; aisleId: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");
  const currentAisleId = aisleId ?? "";

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
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [hardDeleteTarget, setHardDeleteTarget] = useState<SideResult | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const [layoutCode, setLayoutCode] = useState(state?.layout?.code ?? "");
  const [aisleCode, setAisleCode] = useState(state?.aisle?.code ?? "");

  const loadData = useCallback(
    async (act: FilterActive) => {
      setIsLoading(true);
      setPageError(null);
      try {
        const result = await listSides(slug, currentAisleId, toActiveParam(act));
        setItems(result.content);
        if (result.content[0]) {
          setLayoutCode((prev) => prev || result.content[0].layoutCode);
          setAisleCode((prev) => prev || result.content[0].aisleCode);
        }
      } catch (error) {
        setPageError(extractF1ErrorMessage(error) ?? t("warehouse.loadFailed"));
      } finally {
        setIsLoading(false);
      }
    },
    [currentAisleId, slug, t]
  );

  useEffect(() => {
    if (currentAisleId) {
      void loadData(activeFilter);
    }
  }, [activeFilter, currentAisleId, loadData]);

  const breadcrumbs = useMemo(
    () => [
      {
        label: t("warehouse.breadcrumb.layouts"),
        to: PATHS.TENANT.warehouseLayouts(slug),
      },
      {
        label: layoutCode || t("warehouse.breadcrumb.layout"),
        to: state?.layout?.id ? PATHS.TENANT.warehouseAisles(slug, state.layout.id) : undefined,
      },
      {
        label: aisleCode || t("warehouse.breadcrumb.aisle"),
        to: state?.layout?.id ? PATHS.TENANT.warehouseAisles(slug, state.layout.id) : undefined,
      },
      {
        label: t("warehouse.breadcrumb.sides"),
      },
    ],
    [aisleCode, layoutCode, slug, state, t]
  );

  const applyFilters = () => {
    setActiveFilter(pendingActive);
  };

  const handleSave = async () => {
    if (!formSide) {
      setFormError(t("warehouse.validationSideRequired"));
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      await createSide(slug, currentAisleId, { side: formSide });
      setIsFormOpen(false);
      setFormSide("L");
      void loadData(activeFilter);
    } catch (error) {
      setFormError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  const handleSoftDelete = async (item: SideResult) => {
    try {
      await softDeleteSide(slug, currentAisleId, item.id);
      void loadData(activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    }
  };

  const handleRestore = async (item: SideResult) => {
    try {
      await restoreSide(slug, currentAisleId, item.id);
      void loadData(activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    }
  };

  const handleHardDelete = async () => {
    if (!hardDeleteTarget) return;
    setIsDeleting(true);
    try {
      await hardDeleteSide(slug, currentAisleId, hardDeleteTarget.id);
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
          <Button variant="outline" onClick={applyFilters}>
            {t("warehouse.applyFilters")}
          </Button>
          {canEdit ? (
            <Button className="ms-auto" onClick={() => setIsFormOpen(true)}>
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
                          to={PATHS.TENANT.warehouseBays(slug, item.id)}
                          state={buildAncestorState(state, {
                            layout: state?.layout,
                            aisle: { id: currentAisleId, code: aisleCode || item.aisleCode },
                            side: { id: item.id, side: item.side },
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
