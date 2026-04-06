import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
  createCategory,
  extractF0ErrorMessage,
  hardDeleteCategory,
  listCategories,
  restoreCategory,
  softDeleteCategory,
  updateCategory,
} from "@/features/tenant/api/f0Api";
import { listZoneTypes } from "@/features/tenant/api/zoneTypeApi";
import type { CategoryResult, ZoneTypeResult } from "@/features/tenant/types/f0";
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

export default function TenantCategoriesPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");

  const canCreate = hasPermission(TENANT_PERMISSIONS.CATEGORIES_CREATE);
  const canEdit = hasPermission(TENANT_PERMISSIONS.CATEGORIES_EDIT);
  const canSoftDelete = hasPermission(TENANT_PERMISSIONS.CATEGORIES_SOFT_DELETE);
  const canRestore = hasPermission(TENANT_PERMISSIONS.CATEGORIES_RESTORE);
  const canHardDelete = hasPermission(TENANT_PERMISSIONS.CATEGORIES_HARD_DELETE);

  const [categories, setCategories] = useState<CategoryResult[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);

  const [search, setSearch] = useState("");
  const [activeFilter, setActiveFilter] = useState<FilterActive>("all");
  const [pendingSearch, setPendingSearch] = useState("");
  const [pendingActive, setPendingActive] = useState<FilterActive>("all");

  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<CategoryResult | null>(null);
  const [formName, setFormName] = useState("");
  const [formDescription, setFormDescription] = useState("");
  const [formCode, setFormCode] = useState("");
  const [formDisplayName, setFormDisplayName] = useState("");
  const [formRequiredZoneTypeId, setFormRequiredZoneTypeId] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [zoneTypes, setZoneTypes] = useState<ZoneTypeResult[]>([]);

  const [hardDeleteTarget, setHardDeleteTarget] = useState<CategoryResult | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const loadCategories = useCallback(
    async (pg: number, srch: string, act: FilterActive) => {
      setIsLoading(true);
      setPageError(null);
      try {
        const result = await listCategories(slug, {
          page: pg,
          size: 20,
          search: srch || undefined,
          active: toActiveParam(act),
        });
        setCategories(result.content);
        setTotalElements(result.totalElements);
        setTotalPages(result.totalPages);
        setPage(result.page);
      } catch (error) {
        setPageError(extractF0ErrorMessage(error) ?? t("categories.loadFailed"));
      } finally {
        setIsLoading(false);
      }
    },
    [slug, t]
  );

  useEffect(() => {
    void loadCategories(0, search, activeFilter);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadCategories]);

  useEffect(() => {
    void listZoneTypes(slug, { active: true }).then((r) => setZoneTypes(r));
  }, [slug]);

  const applyFilters = () => {
    setSearch(pendingSearch);
    setActiveFilter(pendingActive);
    void loadCategories(0, pendingSearch, pendingActive);
  };

  const openCreate = () => {
    setEditingCategory(null);
    setFormName("");
    setFormDescription("");
    setFormCode("");
    setFormDisplayName("");
    setFormRequiredZoneTypeId("");
    setFormError(null);
    setIsFormOpen(true);
  };

  const openEdit = (category: CategoryResult) => {
    setEditingCategory(category);
    setFormName(category.name);
    setFormDescription(category.description ?? "");
    setFormCode(category.code ?? "");
    setFormDisplayName(category.displayName ?? "");
    setFormRequiredZoneTypeId(category.requiredZoneTypeId ?? "");
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleFormSave = async () => {
    if (!formName.trim()) {
      setFormError(t("categories.validationRequired"));
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      const payload = {
        name: formName.trim(),
        description: formDescription.trim() || null,
        code: formCode.trim() || null,
        displayName: formDisplayName.trim() || null,
        requiredZoneTypeId: formRequiredZoneTypeId || null,
      };
      if (editingCategory) {
        await updateCategory(slug, editingCategory.id, payload);
      } else {
        await createCategory(slug, payload);
      }
      setIsFormOpen(false);
      void loadCategories(0, search, activeFilter);
    } catch (error) {
      setFormError(extractF0ErrorMessage(error) ?? t("categories.actionFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  const handleSoftDelete = async (category: CategoryResult) => {
    try {
      await softDeleteCategory(slug, category.id);
      void loadCategories(page, search, activeFilter);
    } catch (error) {
      setPageError(extractF0ErrorMessage(error) ?? t("categories.actionFailed"));
    }
  };

  const handleRestore = async (category: CategoryResult) => {
    try {
      await restoreCategory(slug, category.id);
      void loadCategories(page, search, activeFilter);
    } catch (error) {
      setPageError(extractF0ErrorMessage(error) ?? t("categories.actionFailed"));
    }
  };

  const handleHardDelete = async () => {
    if (!hardDeleteTarget) return;
    setIsDeleting(true);
    try {
      await hardDeleteCategory(slug, hardDeleteTarget.id);
      setHardDeleteTarget(null);
      void loadCategories(0, search, activeFilter);
    } catch (error) {
      setPageError(extractF0ErrorMessage(error) ?? t("categories.actionFailed"));
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold">{t("categories.pageTitle")}</h1>
        <p className="text-sm text-muted-foreground">{t("categories.pageDescription")}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("categories.filtersTitle")}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-3">
            <Input
              className="max-w-xs"
              placeholder={t("categories.searchPlaceholder")}
              value={pendingSearch}
              onChange={(e) => setPendingSearch(e.target.value)}
            />
            <select
              className="rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={pendingActive}
              onChange={(e) => setPendingActive(e.target.value as FilterActive)}
              aria-label={t("categories.activeFilterLabel")}
            >
              <option value="all">{t("categories.activeFilterAll")}</option>
              <option value="active">{t("categories.activeFilterActive")}</option>
              <option value="inactive">{t("categories.activeFilterInactive")}</option>
            </select>
            <Button variant="outline" onClick={applyFilters}>
              {t("categories.applyFilters")}
            </Button>
            {canCreate && (
              <Button className="ms-auto" onClick={openCreate}>
                {t("categories.createAction")}
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("categories.listTitle")}</CardTitle>
          <CardDescription>{t("categories.listCount", { count: String(totalElements) })}</CardDescription>
        </CardHeader>
        <CardContent>
          {pageError ? <p className="mb-2 text-xs text-destructive">{pageError}</p> : null}
          {isLoading ? (
            <p className="text-sm text-muted-foreground">{t("categories.loading")}</p>
          ) : categories.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("categories.empty")}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-start">
                    <th className="py-2 pe-4 text-start font-medium">{t("categories.tableName")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("categories.tableDescription")}</th>
                    <th className="py-2 pe-4 text-start font-medium">Zone Type</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("categories.tableStatus")}</th>
                    <th className="py-2 text-start font-medium">{t("categories.tableActions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {categories.map((category) => (
                    <tr key={category.id} className="border-b last:border-0">
                      <td className="py-2 pe-4 font-medium">{category.name}</td>
                      <td className="py-2 pe-4 text-muted-foreground">{category.description ?? "—"}</td>
                      <td className="py-2 pe-4">
                        {category.requiredZoneTypeCode ? (
                          <span className="rounded bg-blue-100 px-1.5 py-0.5 text-xs font-mono text-blue-700">
                            {category.requiredZoneTypeCode}
                          </span>
                        ) : "—"}
                      </td>
                      <td className="py-2 pe-4">
                        <span
                          className={
                            category.active ? "text-emerald-600" : "text-muted-foreground"
                          }
                        >
                          {category.active ? t("categories.statusActive") : t("categories.statusInactive")}
                        </span>
                      </td>
                      <td className="py-2">
                        <div className="flex flex-wrap gap-1">
                          {canEdit && (
                            <Button size="sm" variant="outline" onClick={() => openEdit(category)}>
                              {t("categories.editAction")}
                            </Button>
                          )}
                          {canSoftDelete && category.active && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => void handleSoftDelete(category)}
                            >
                              {t("categories.softDeleteAction")}
                            </Button>
                          )}
                          {canRestore && !category.active && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => void handleRestore(category)}
                            >
                              {t("categories.restoreAction")}
                            </Button>
                          )}
                          {canHardDelete && (
                            <Button
                              size="sm"
                              variant="destructive"
                              onClick={() => setHardDeleteTarget(category)}
                            >
                              {t("categories.hardDeleteAction")}
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {totalPages > 1 && (
            <div className="mt-4 flex items-center gap-2">
              <Button
                size="sm"
                variant="outline"
                disabled={page === 0}
                onClick={() => {
                  const prev = page - 1;
                  setPage(prev);
                  void loadCategories(prev, search, activeFilter);
                }}
              >
                {t("categories.paginationPrevious")}
              </Button>
              <span className="text-sm text-muted-foreground">
                {t("categories.paginationInfo", {
                  page: String(page + 1),
                  totalPages: String(totalPages),
                })}
              </span>
              <Button
                size="sm"
                variant="outline"
                disabled={page + 1 >= totalPages}
                onClick={() => {
                  const next = page + 1;
                  setPage(next);
                  void loadCategories(next, search, activeFilter);
                }}
              >
                {t("categories.paginationNext")}
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Create / Edit dialog */}
      <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editingCategory ? t("categories.editDialogTitle") : t("categories.createDialogTitle")}
            </DialogTitle>
            <DialogDescription>
              {editingCategory ? t("categories.editDialogDescription") : t("categories.createDialogDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="category-name">{t("categories.nameLabel")}</Label>
              <Input
                id="category-name"
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="category-description">{t("categories.descriptionLabel")}</Label>
              <Input
                id="category-description"
                value={formDescription}
                onChange={(e) => setFormDescription(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="category-code">{t("categories.codeLabel")}</Label>
              <Input
                id="category-code"
                value={formCode}
                onChange={(e) => setFormCode(e.target.value)}
              />
              <p className="text-xs text-muted-foreground">{t("categories.codeHint")}</p>
            </div>
            <div className="space-y-1">
              <Label htmlFor="category-display-name">{t("categories.displayNameLabel")}</Label>
              <Input
                id="category-display-name"
                value={formDisplayName}
                onChange={(e) => setFormDisplayName(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="category-zone-type">{t("categories.requiredZoneTypeLabel")}</Label>
              <select
                id="category-zone-type"
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={formRequiredZoneTypeId}
                onChange={(e) => setFormRequiredZoneTypeId(e.target.value)}
              >
                <option value="">{t("categories.requiredZoneTypePlaceholder")}</option>
                {zoneTypes.map((zt) => (
                  <option key={zt.id} value={zt.id}>{zt.code} — {zt.displayName}</option>
                ))}
              </select>
            </div>
            {formError ? <p className="text-xs text-destructive">{formError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsFormOpen(false)} disabled={isSaving}>
              {t("categories.cancelAction")}
            </Button>
            <Button onClick={() => void handleFormSave()} disabled={isSaving}>
              {isSaving ? t("categories.saving") : editingCategory ? t("categories.editAction") : t("categories.createAction")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Hard delete confirm */}
      <AlertDialog
        open={hardDeleteTarget !== null}
        onOpenChange={(open) => { if (!open) setHardDeleteTarget(null); }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("categories.hardDeleteDialogTitle")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("categories.hardDeleteDialogDescription")}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDeleting}>{t("categories.cancelAction")}</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => void handleHardDelete()}
              disabled={isDeleting}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {isDeleting ? t("categories.deleting") : t("categories.hardDeleteAction")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
