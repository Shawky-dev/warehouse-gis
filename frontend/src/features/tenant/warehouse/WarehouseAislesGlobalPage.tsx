import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
  createAisle,
  extractF1ErrorMessage,
  hardDeleteAisle,
  listAislesGlobal,
  listLayouts,
  restoreAisle,
  softDeleteAisle,
  updateAisle,
} from "@/features/tenant/api/f1Api";
import type { AisleResult, LayoutResult } from "@/features/tenant/types/f1";
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
  emptyValue,
  WarehousePageShell,
  WarehousePagination,
  WarehouseStatusBadge,
  toActiveParam,
} from "@/features/tenant/warehouse/shared";
import type { FilterActive, WarehouseBreadcrumbItem } from "@/features/tenant/warehouse/shared";

export default function WarehouseAislesGlobalPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const [searchParams] = useSearchParams();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");
  const layoutId = searchParams.get("layoutId") ?? "";

  const canEdit = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_EDIT);
  const canSoftDelete = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_SOFT_DELETE);
  const canRestore = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_RESTORE);
  const canHardDelete = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_HARD_DELETE);

  const [items, setItems] = useState<AisleResult[]>([]);
  const [page, setPage] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);

  const [search, setSearch] = useState("");
  const [pendingSearch, setPendingSearch] = useState("");
  const [activeFilter, setActiveFilter] = useState<FilterActive>("all");
  const [pendingActive, setPendingActive] = useState<FilterActive>("all");

  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<AisleResult | null>(null);
  const [formCode, setFormCode] = useState("");
  const [formName, setFormName] = useState("");
  const [formLayoutId, setFormLayoutId] = useState(layoutId);
  const [formLayouts, setFormLayouts] = useState<LayoutResult[]>([]);
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [hardDeleteTarget, setHardDeleteTarget] = useState<AisleResult | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const loadData = useCallback(
    async (pg: number, srch: string, act: FilterActive) => {
      setIsLoading(true);
      setPageError(null);
      try {
        const result = await listAislesGlobal(slug, {
          layoutId: layoutId || undefined,
          page: pg,
          size: 20,
          search: srch || undefined,
          active: toActiveParam(act),
        });
        setItems(result.content);
        setPage(result.page);
        setTotalElements(result.totalElements);
        setTotalPages(result.totalPages);
      } catch (error) {
        setPageError(extractF1ErrorMessage(error) ?? t("warehouse.loadFailed"));
      } finally {
        setIsLoading(false);
      }
    },
    [layoutId, slug, t]
  );

  useEffect(() => {
    void loadData(0, search, activeFilter);
  }, [activeFilter, loadData, search]);

  const breadcrumbs = useMemo(() => {
    const crumbs: WarehouseBreadcrumbItem[] = [
      { label: t("warehouse.breadcrumb.layouts"), to: PATHS.TENANT.warehouseLayouts(slug) },
    ];
    crumbs.push({ label: t("warehouse.breadcrumb.aisles") });
    return crumbs;
  }, [slug, t]);

  const applyFilters = () => {
    setSearch(pendingSearch);
    setActiveFilter(pendingActive);
  };

  const openCreate = async () => {
    setEditingItem(null);
    setFormCode("");
    setFormName("");
    setFormLayoutId(layoutId);
    setFormError(null);
    try {
      const result = await listLayouts(slug, { size: 100 });
      setFormLayouts(result.content);
      if (!layoutId && result.content[0]) {
        setFormLayoutId(result.content[0].id);
      }
    } catch {
      setFormLayouts([]);
    }
    setIsFormOpen(true);
  };

  const openEdit = (item: AisleResult) => {
    setEditingItem(item);
    setFormCode(item.code);
    setFormName(item.name ?? "");
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleSave = async () => {
    if (!formCode.trim()) {
      setFormError(t("warehouse.validationAisleRequired"));
      return;
    }
    if (!editingItem && !formLayoutId) {
      setFormError(t("warehouse.formParentRequired"));
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      const payload = { code: formCode.trim(), name: formName.trim() || null };
      if (editingItem) {
        await updateAisle(slug, editingItem.layoutId, editingItem.id, payload);
      } else {
        await createAisle(slug, formLayoutId, payload);
      }
      setIsFormOpen(false);
      void loadData(0, search, activeFilter);
    } catch (error) {
      setFormError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  const handleSoftDelete = async (item: AisleResult) => {
    try {
      await softDeleteAisle(slug, item.layoutId, item.id);
      void loadData(page, search, activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    }
  };

  const handleRestore = async (item: AisleResult) => {
    try {
      await restoreAisle(slug, item.layoutId, item.id);
      void loadData(page, search, activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    }
  };

  const handleHardDelete = async () => {
    if (!hardDeleteTarget) return;
    setIsDeleting(true);
    try {
      await hardDeleteAisle(slug, hardDeleteTarget.layoutId, hardDeleteTarget.id);
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
      title={t("warehouse.aisles.pageTitle")}
      description={t("warehouse.aisles.pageDescription")}
      breadcrumbs={breadcrumbs}
      filterTitle={t("warehouse.filtersTitle")}
      filters={
        <div className="flex flex-wrap gap-3">
          <Input
            className="max-w-xs"
            placeholder={t("warehouse.aisles.searchPlaceholder")}
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
            <Button className="ms-auto" onClick={() => void openCreate()}>
              {t("warehouse.aisles.createAction")}
            </Button>
          ) : null}
        </div>
      }
      listTitle={t("warehouse.aisles.listTitle")}
      listDescription={t("warehouse.aisles.listCount", { count: String(totalElements) })}
    >
      {pageError ? <p className="mb-2 text-xs text-destructive">{pageError}</p> : null}
      {isLoading ? (
        <p className="text-sm text-muted-foreground">{t("warehouse.aisles.loading")}</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("warehouse.aisles.empty")}</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-start">
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.aisles.tableCode")}</th>
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.aisles.tableName")}</th>
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.tableStatus")}</th>
                <th className="py-2 text-start font-medium">{t("warehouse.tableActions")}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} className="border-b last:border-0">
                  <td className="py-2 pe-4 font-medium">{item.code}</td>
                  <td className="py-2 pe-4 text-muted-foreground">{emptyValue(item.name)}</td>
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
                          to={PATHS.TENANT.warehouseSidesGlobal(slug, {
                            layoutId: item.layoutId,
                            aisleId: item.id,
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

      <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editingItem ? t("warehouse.aisles.editDialogTitle") : t("warehouse.aisles.createDialogTitle")}
            </DialogTitle>
            <DialogDescription>
              {editingItem
                ? t("warehouse.aisles.editDialogDescription")
                : t("warehouse.aisles.createDialogDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            {!editingItem ? (
              <div className="space-y-1">
                <Label htmlFor="aisle-layout">{t("warehouse.aisles.formLayoutLabel")}</Label>
                <select
                  id="aisle-layout"
                  className="w-full rounded-none border border-input bg-background px-3 py-2 text-sm"
                  value={formLayoutId}
                  onChange={(e) => setFormLayoutId(e.target.value)}
                >
                  {formLayouts.map((l) => (
                    <option key={l.id} value={l.id}>{l.code}{l.name ? ` — ${l.name}` : ""}</option>
                  ))}
                </select>
              </div>
            ) : null}
            <div className="space-y-1">
              <Label htmlFor="aisle-code">{t("warehouse.aisles.codeLabel")}</Label>
              <Input id="aisle-code" value={formCode} onChange={(e) => setFormCode(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="aisle-name">{t("warehouse.aisles.nameLabel")}</Label>
              <Input id="aisle-name" value={formName} onChange={(e) => setFormName(e.target.value)} />
            </div>
            {formError ? <p className="text-xs text-destructive">{formError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={isSaving} onClick={() => setIsFormOpen(false)}>
              {t("warehouse.cancelAction")}
            </Button>
            <Button disabled={isSaving} onClick={() => void handleSave()}>
              {isSaving ? t("warehouse.saving") : editingItem ? t("warehouse.editAction") : t("warehouse.aisles.createAction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={hardDeleteTarget !== null} onOpenChange={(open) => !open && setHardDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("warehouse.aisles.hardDeleteDialogTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{t("warehouse.aisles.hardDeleteDialogDescription")}</AlertDialogDescription>
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
