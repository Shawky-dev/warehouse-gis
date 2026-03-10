import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
  createBay,
  createBaysBulk,
  extractF1ErrorMessage,
  hardDeleteBay,
  listBays,
  restoreBay,
  softDeleteBay,
  updateBay,
} from "@/features/tenant/api/f1Api";
import type { BayResult, WarehouseAncestorState } from "@/features/tenant/types/f1";
import { PATHS } from "@/shared/consts/paths";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";
import { Textarea } from "@/shared/components/ui/textarea";
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
  WarehousePagination,
  WarehouseStatusBadge,
  toActiveParam,
} from "@/features/tenant/warehouse/shared";
import type { FilterActive } from "@/features/tenant/warehouse/shared";

export default function WarehouseBaysPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const location = useLocation();
  const state = location.state as WarehouseAncestorState | undefined;
  const { tenantSlug, sideId } = useParams<{ tenantSlug: string; sideId: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");
  const currentSideId = sideId ?? "";

  const canEdit = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_EDIT);
  const canSoftDelete = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_SOFT_DELETE);
  const canRestore = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_RESTORE);
  const canHardDelete = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_HARD_DELETE);

  const [items, setItems] = useState<BayResult[]>([]);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);

  const [search, setSearch] = useState("");
  const [pendingSearch, setPendingSearch] = useState("");
  const [activeFilter, setActiveFilter] = useState<FilterActive>("all");
  const [pendingActive, setPendingActive] = useState<FilterActive>("all");

  const [formOpen, setFormOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<BayResult | null>(null);
  const [formCode, setFormCode] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [bulkOpen, setBulkOpen] = useState(false);
  const [bulkCodes, setBulkCodes] = useState("");
  const [levelsPerBay, setLevelsPerBay] = useState("3");
  const [shelvesPerLevel, setShelvesPerLevel] = useState("4");
  const [bulkError, setBulkError] = useState<string | null>(null);
  const [bulkResult, setBulkResult] = useState<string[]>([]);
  const [isBulkSaving, setIsBulkSaving] = useState(false);

  const [hardDeleteTarget, setHardDeleteTarget] = useState<BayResult | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const [sideCode, setSideCode] = useState(state?.side?.side ?? "");

  const loadData = useCallback(
    async (pg: number, srch: string, act: FilterActive) => {
      setIsLoading(true);
      setPageError(null);
      try {
        const result = await listBays(slug, currentSideId, {
          page: pg,
          size: 20,
          search: srch || undefined,
          active: toActiveParam(act),
        });
        setItems(result.content);
        setPage(result.page);
        setTotalElements(result.totalElements);
        setTotalPages(result.totalPages);
        if (!sideCode && result.content[0]?.side) {
          setSideCode(result.content[0].side);
        }
      } catch (error) {
        setPageError(extractF1ErrorMessage(error) ?? t("warehouse.loadFailed"));
      } finally {
        setIsLoading(false);
      }
    },
    [currentSideId, sideCode, slug, t]
  );

  useEffect(() => {
    if (currentSideId) {
      void loadData(0, search, activeFilter);
    }
  }, [activeFilter, currentSideId, loadData, search]);

  const breadcrumbs = useMemo(
    () => [
      { label: t("warehouse.breadcrumb.layouts"), to: PATHS.TENANT.warehouseLayouts(slug) },
      {
        label: state?.layout?.code || t("warehouse.breadcrumb.layout"),
        to: state?.layout?.id ? PATHS.TENANT.warehouseAisles(slug, state.layout.id) : undefined,
      },
      {
        label: state?.aisle?.code || t("warehouse.breadcrumb.aisle"),
        to: state?.layout?.id ? PATHS.TENANT.warehouseSides(slug, state.aisle?.id ?? "") : undefined,
      },
      {
        label: sideCode ? sideLabel(sideCode) : t("warehouse.breadcrumb.side"),
      },
      {
        label: t("warehouse.breadcrumb.bays"),
      },
    ],
    [sideCode, slug, state, t]
  );

  const applyFilters = () => {
    setSearch(pendingSearch);
    setActiveFilter(pendingActive);
  };

  const openCreate = () => {
    setEditingItem(null);
    setFormCode("");
    setFormError(null);
    setFormOpen(true);
  };

  const openEdit = (item: BayResult) => {
    setEditingItem(item);
    setFormCode(item.bayCode);
    setFormError(null);
    setFormOpen(true);
  };

  const handleSave = async () => {
    if (!formCode.trim()) {
      setFormError(t("warehouse.validationBayRequired"));
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      const payload = { code: formCode.trim() };
      if (editingItem) {
        await updateBay(slug, currentSideId, editingItem.id, payload);
      } else {
        await createBay(slug, currentSideId, payload);
      }
      setFormOpen(false);
      void loadData(0, search, activeFilter);
    } catch (error) {
      setFormError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  const handleBulkSave = async () => {
    const codes = bulkCodes
      .split(/[\n,]+/)
      .map((value) => value.trim())
      .filter(Boolean);
    const parsedLevels = Number(levelsPerBay);
    const parsedShelves = Number(shelvesPerLevel);
    if (codes.length === 0 || parsedLevels < 1 || parsedShelves < 1) {
      setBulkError(t("warehouse.validationBulkRequired"));
      return;
    }
    setIsBulkSaving(true);
    setBulkError(null);
    try {
      const result = await createBaysBulk(slug, currentSideId, {
        codes,
        levelsPerBay: parsedLevels,
        shelvesPerLevel: parsedShelves,
      });
      setBulkResult(result.locationCodes);
      void loadData(0, search, activeFilter);
    } catch (error) {
      setBulkError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    } finally {
      setIsBulkSaving(false);
    }
  };

  const handleSoftDelete = async (item: BayResult) => {
    try {
      await softDeleteBay(slug, currentSideId, item.id);
      void loadData(page, search, activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    }
  };

  const handleRestore = async (item: BayResult) => {
    try {
      await restoreBay(slug, currentSideId, item.id);
      void loadData(page, search, activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    }
  };

  const handleHardDelete = async () => {
    if (!hardDeleteTarget) return;
    setIsDeleting(true);
    try {
      await hardDeleteBay(slug, currentSideId, hardDeleteTarget.id);
      setHardDeleteTarget(null);
      void loadData(0, search, activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <WarehousePageShell
      title={t("warehouse.bays.pageTitle")}
      description={t("warehouse.bays.pageDescription")}
      breadcrumbs={breadcrumbs}
      filterTitle={t("warehouse.filtersTitle")}
      filters={
        <div className="flex flex-wrap gap-3">
          <Input
            className="max-w-xs"
            placeholder={t("warehouse.bays.searchPlaceholder")}
            value={pendingSearch}
            onChange={(e) => setPendingSearch(e.target.value)}
          />
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
            <>
              <Button variant="outline" onClick={() => { setBulkOpen(true); setBulkResult([]); setBulkError(null); }}>
                {t("warehouse.bays.bulkCreateAction")}
              </Button>
              <Button className="ms-auto" onClick={openCreate}>
                {t("warehouse.bays.createAction")}
              </Button>
            </>
          ) : null}
        </div>
      }
      listTitle={t("warehouse.bays.listTitle")}
      listDescription={t("warehouse.bays.listCount", { count: String(totalElements) })}
    >
      {pageError ? <p className="mb-2 text-xs text-destructive">{pageError}</p> : null}
      {isLoading ? (
        <p className="text-sm text-muted-foreground">{t("warehouse.bays.loading")}</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("warehouse.bays.empty")}</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-start">
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.bays.tableCode")}</th>
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.tableStatus")}</th>
                <th className="py-2 text-start font-medium">{t("warehouse.tableActions")}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} className="border-b last:border-0">
                  <td className="py-2 pe-4 font-medium">{item.bayCode}</td>
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
                          to={PATHS.TENANT.warehouseLevels(slug, item.id)}
                          state={buildAncestorState(state, {
                            side: { id: currentSideId, side: sideCode || item.side },
                            bay: { id: item.id, code: item.bayCode },
                          })}
                        >
                          {t("warehouse.enterAction")}
                        </Link>
                      </Button>
                      {canEdit ? (
                        <Button size="sm" variant="outline" onClick={() => openEdit(item)}>
                          {t("warehouse.editAction")}
                        </Button>
                      ) : null}
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

      <WarehousePagination
        page={page}
        totalPages={totalPages}
        previousLabel={t("warehouse.paginationPrevious")}
        nextLabel={t("warehouse.paginationNext")}
        infoLabel={t("warehouse.paginationInfo", {
          page: String(page + 1),
          totalPages: String(totalPages),
        })}
        onPrevious={() => void loadData(page - 1, search, activeFilter)}
        onNext={() => void loadData(page + 1, search, activeFilter)}
      />

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editingItem ? t("warehouse.bays.editDialogTitle") : t("warehouse.bays.createDialogTitle")}
            </DialogTitle>
            <DialogDescription>
              {editingItem ? t("warehouse.bays.editDialogDescription") : t("warehouse.bays.createDialogDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="bay-code">{t("warehouse.bays.codeLabel")}</Label>
              <Input id="bay-code" value={formCode} onChange={(e) => setFormCode(e.target.value)} />
            </div>
            {formError ? <p className="text-xs text-destructive">{formError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={isSaving} onClick={() => setFormOpen(false)}>
              {t("warehouse.cancelAction")}
            </Button>
            <Button disabled={isSaving} onClick={() => void handleSave()}>
              {isSaving ? t("warehouse.saving") : editingItem ? t("warehouse.editAction") : t("warehouse.bays.createAction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={bulkOpen} onOpenChange={setBulkOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("warehouse.bays.bulkDialogTitle")}</DialogTitle>
            <DialogDescription>{t("warehouse.bays.bulkDialogDescription")}</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="bulk-codes">{t("warehouse.bays.bulkCodesLabel")}</Label>
              <Textarea
                id="bulk-codes"
                value={bulkCodes}
                onChange={(e) => setBulkCodes(e.target.value)}
                placeholder={t("warehouse.bays.bulkCodesPlaceholder")}
              />
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1">
                <Label htmlFor="levels-per-bay">{t("warehouse.bays.levelsPerBayLabel")}</Label>
                <Input
                  id="levels-per-bay"
                  type="number"
                  min={1}
                  value={levelsPerBay}
                  onChange={(e) => setLevelsPerBay(e.target.value)}
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="shelves-per-level">{t("warehouse.bays.shelvesPerLevelLabel")}</Label>
                <Input
                  id="shelves-per-level"
                  type="number"
                  min={1}
                  value={shelvesPerLevel}
                  onChange={(e) => setShelvesPerLevel(e.target.value)}
                />
              </div>
            </div>
            {bulkError ? <p className="text-xs text-destructive">{bulkError}</p> : null}
            {bulkResult.length > 0 ? (
              <div className="space-y-1">
                <p className="text-xs font-medium">{t("warehouse.bays.bulkResultTitle")}</p>
                <div className="max-h-48 overflow-y-auto border border-border p-2 text-xs">
                  {bulkResult.map((code) => (
                    <p key={code}>{code}</p>
                  ))}
                </div>
              </div>
            ) : null}
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={isBulkSaving} onClick={() => setBulkOpen(false)}>
              {t("warehouse.cancelAction")}
            </Button>
            <Button disabled={isBulkSaving} onClick={() => void handleBulkSave()}>
              {isBulkSaving ? t("warehouse.saving") : t("warehouse.bays.bulkCreateAction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={hardDeleteTarget !== null} onOpenChange={(open) => !open && setHardDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("warehouse.bays.hardDeleteDialogTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{t("warehouse.bays.hardDeleteDialogDescription")}</AlertDialogDescription>
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
