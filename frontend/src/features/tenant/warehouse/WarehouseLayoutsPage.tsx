import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
  createLayout,
  extractF1ErrorMessage,
  hardDeleteLayout,
  listLayouts,
  restoreLayout,
  softDeleteLayout,
  updateLayout,
} from "@/features/tenant/api/f1Api";
import type { LayoutResult } from "@/features/tenant/types/f1";
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
  WarehousePageShell,
  WarehousePagination,
  WarehouseStatusBadge,
  toActiveParam,
} from "@/features/tenant/warehouse/shared";
import type { FilterActive } from "@/features/tenant/warehouse/shared";

export default function WarehouseLayoutsPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");

  const canEdit = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_EDIT);
  const canSoftDelete = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_SOFT_DELETE);
  const canRestore = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_RESTORE);
  const canHardDelete = hasPermission(TENANT_PERMISSIONS.WAREHOUSE_HARD_DELETE);

  const [items, setItems] = useState<LayoutResult[]>([]);
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
  const [editingItem, setEditingItem] = useState<LayoutResult | null>(null);
  const [formCode, setFormCode] = useState("");
  const [formName, setFormName] = useState("");
  const [formDescription, setFormDescription] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [hardDeleteTarget, setHardDeleteTarget] = useState<LayoutResult | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const loadData = useCallback(
    async (pg: number, srch: string, act: FilterActive) => {
      setIsLoading(true);
      setPageError(null);
      try {
        const result = await listLayouts(slug, {
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
    [slug, t]
  );

  useEffect(() => {
    void loadData(0, search, activeFilter);
  }, [activeFilter, loadData, search]);

  const applyFilters = () => {
    setSearch(pendingSearch);
    setActiveFilter(pendingActive);
  };

  const openCreate = () => {
    setEditingItem(null);
    setFormCode("");
    setFormName("");
    setFormDescription("");
    setFormError(null);
    setIsFormOpen(true);
  };

  const openEdit = (item: LayoutResult) => {
    setEditingItem(item);
    setFormCode(item.code);
    setFormName(item.name);
    setFormDescription(item.description ?? "");
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleSave = async () => {
    if (!formCode.trim() || !formName.trim()) {
      setFormError(t("warehouse.validationLayoutRequired"));
      return;
    }

    setIsSaving(true);
    setFormError(null);
    try {
      const payload = {
        code: formCode.trim(),
        name: formName.trim(),
        description: formDescription.trim() || null,
      };
      if (editingItem) {
        await updateLayout(slug, editingItem.id, payload);
      } else {
        await createLayout(slug, payload);
      }
      setIsFormOpen(false);
      void loadData(0, search, activeFilter);
    } catch (error) {
      setFormError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  const handleSoftDelete = async (item: LayoutResult) => {
    try {
      await softDeleteLayout(slug, item.id);
      void loadData(page, search, activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    }
  };

  const handleRestore = async (item: LayoutResult) => {
    try {
      await restoreLayout(slug, item.id);
      void loadData(page, search, activeFilter);
    } catch (error) {
      setPageError(extractF1ErrorMessage(error) ?? t("warehouse.actionFailed"));
    }
  };

  const handleHardDelete = async () => {
    if (!hardDeleteTarget) return;
    setIsDeleting(true);
    try {
      await hardDeleteLayout(slug, hardDeleteTarget.id);
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
      title={t("warehouse.layouts.pageTitle")}
      description={t("warehouse.layouts.pageDescription")}
      filters={
        <div className="flex flex-wrap gap-3">
          <Input
            className="max-w-xs"
            placeholder={t("warehouse.layouts.searchPlaceholder")}
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
            <Button className="ms-auto" onClick={openCreate}>
              {t("warehouse.layouts.createAction")}
            </Button>
          ) : null}
        </div>
      }
      listTitle={t("warehouse.layouts.listTitle")}
      listDescription={t("warehouse.layouts.listCount", { count: String(totalElements) })}
      filterTitle={t("warehouse.filtersTitle")}
    >
      {pageError ? <p className="mb-2 text-xs text-destructive">{pageError}</p> : null}
      {isLoading ? (
        <p className="text-sm text-muted-foreground">{t("warehouse.layouts.loading")}</p>
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t("warehouse.layouts.empty")}</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-start">
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.layouts.tableCode")}</th>
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.layouts.tableName")}</th>
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.layouts.tableDescription")}</th>
                <th className="py-2 pe-4 text-start font-medium">{t("warehouse.tableStatus")}</th>
                <th className="py-2 text-start font-medium">{t("warehouse.tableActions")}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id} className="border-b last:border-0">
                  <td className="py-2 pe-4 font-medium">{item.code}</td>
                  <td className="py-2 pe-4">{item.name}</td>
                  <td className="py-2 pe-4 text-muted-foreground">{item.description ?? "—"}</td>
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
                          to={PATHS.TENANT.warehouseAisles(slug, item.id)}
                          state={{ layout: { id: item.id, code: item.code } }}
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
              {editingItem ? t("warehouse.layouts.editDialogTitle") : t("warehouse.layouts.createDialogTitle")}
            </DialogTitle>
            <DialogDescription>
              {editingItem
                ? t("warehouse.layouts.editDialogDescription")
                : t("warehouse.layouts.createDialogDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="layout-code">{t("warehouse.layouts.codeLabel")}</Label>
              <Input id="layout-code" value={formCode} onChange={(e) => setFormCode(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="layout-name">{t("warehouse.layouts.nameLabel")}</Label>
              <Input id="layout-name" value={formName} onChange={(e) => setFormName(e.target.value)} />
            </div>
            <div className="space-y-1">
              <Label htmlFor="layout-description">{t("warehouse.layouts.descriptionLabel")}</Label>
              <Textarea
                id="layout-description"
                value={formDescription}
                onChange={(e) => setFormDescription(e.target.value)}
              />
            </div>
            {formError ? <p className="text-xs text-destructive">{formError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" disabled={isSaving} onClick={() => setIsFormOpen(false)}>
              {t("warehouse.cancelAction")}
            </Button>
            <Button disabled={isSaving} onClick={() => void handleSave()}>
              {isSaving ? t("warehouse.saving") : editingItem ? t("warehouse.editAction") : t("warehouse.layouts.createAction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={hardDeleteTarget !== null} onOpenChange={(open) => !open && setHardDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("warehouse.layouts.hardDeleteDialogTitle")}</AlertDialogTitle>
            <AlertDialogDescription>{t("warehouse.layouts.hardDeleteDialogDescription")}</AlertDialogDescription>
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
