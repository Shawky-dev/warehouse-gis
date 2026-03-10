import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
  createUom,
  extractF0ErrorMessage,
  hardDeleteUom,
  listUoms,
  restoreUom,
  softDeleteUom,
  updateUom,
} from "@/features/tenant/api/f0Api";
import type { UomResult } from "@/features/tenant/types/f0";
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

export default function TenantUomsPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");

  const canCreate = hasPermission(TENANT_PERMISSIONS.UOMS_CREATE);
  const canEdit = hasPermission(TENANT_PERMISSIONS.UOMS_EDIT);
  const canSoftDelete = hasPermission(TENANT_PERMISSIONS.UOMS_SOFT_DELETE);
  const canRestore = hasPermission(TENANT_PERMISSIONS.UOMS_RESTORE);
  const canHardDelete = hasPermission(TENANT_PERMISSIONS.UOMS_HARD_DELETE);

  const [uoms, setUoms] = useState<UomResult[]>([]);
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
  const [editingUom, setEditingUom] = useState<UomResult | null>(null);
  const [formCode, setFormCode] = useState("");
  const [formName, setFormName] = useState("");
  const [formSymbol, setFormSymbol] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [hardDeleteTarget, setHardDeleteTarget] = useState<UomResult | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const loadUoms = useCallback(
    async (pg: number, srch: string, act: FilterActive) => {
      setIsLoading(true);
      setPageError(null);
      try {
        const result = await listUoms(slug, {
          page: pg,
          size: 20,
          search: srch || undefined,
          active: toActiveParam(act),
        });
        setUoms(result.content);
        setTotalElements(result.totalElements);
        setTotalPages(result.totalPages);
        setPage(result.page);
      } catch (error) {
        setPageError(extractF0ErrorMessage(error) ?? t("uoms.loadFailed"));
      } finally {
        setIsLoading(false);
      }
    },
    [slug, t]
  );

  useEffect(() => {
    void loadUoms(0, search, activeFilter);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadUoms]);

  const applyFilters = () => {
    setSearch(pendingSearch);
    setActiveFilter(pendingActive);
    void loadUoms(0, pendingSearch, pendingActive);
  };

  const openCreate = () => {
    setEditingUom(null);
    setFormCode("");
    setFormName("");
    setFormSymbol("");
    setFormError(null);
    setIsFormOpen(true);
  };

  const openEdit = (uom: UomResult) => {
    setEditingUom(uom);
    setFormCode(uom.code);
    setFormName(uom.name);
    setFormSymbol(uom.symbol ?? "");
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleFormSave = async () => {
    if (!formCode.trim() || !formName.trim()) {
      setFormError(t("uoms.validationRequired"));
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      const payload = {
        code: formCode.trim(),
        name: formName.trim(),
        symbol: formSymbol.trim() || null,
      };
      if (editingUom) {
        await updateUom(slug, editingUom.id, payload);
      } else {
        await createUom(slug, payload);
      }
      setIsFormOpen(false);
      void loadUoms(0, search, activeFilter);
    } catch (error) {
      setFormError(extractF0ErrorMessage(error) ?? t("uoms.actionFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  const handleSoftDelete = async (uom: UomResult) => {
    try {
      await softDeleteUom(slug, uom.id);
      void loadUoms(page, search, activeFilter);
    } catch (error) {
      setPageError(extractF0ErrorMessage(error) ?? t("uoms.actionFailed"));
    }
  };

  const handleRestore = async (uom: UomResult) => {
    try {
      await restoreUom(slug, uom.id);
      void loadUoms(page, search, activeFilter);
    } catch (error) {
      setPageError(extractF0ErrorMessage(error) ?? t("uoms.actionFailed"));
    }
  };

  const handleHardDelete = async () => {
    if (!hardDeleteTarget) return;
    setIsDeleting(true);
    try {
      await hardDeleteUom(slug, hardDeleteTarget.id);
      setHardDeleteTarget(null);
      void loadUoms(0, search, activeFilter);
    } catch (error) {
      setPageError(extractF0ErrorMessage(error) ?? t("uoms.actionFailed"));
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold">{t("uoms.pageTitle")}</h1>
        <p className="text-sm text-muted-foreground">{t("uoms.pageDescription")}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("uoms.filtersTitle")}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-3">
            <Input
              className="max-w-xs"
              placeholder={t("uoms.searchPlaceholder")}
              value={pendingSearch}
              onChange={(e) => setPendingSearch(e.target.value)}
            />
            <select
              className="rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={pendingActive}
              onChange={(e) => setPendingActive(e.target.value as FilterActive)}
              aria-label={t("uoms.activeFilterLabel")}
            >
              <option value="all">{t("uoms.activeFilterAll")}</option>
              <option value="active">{t("uoms.activeFilterActive")}</option>
              <option value="inactive">{t("uoms.activeFilterInactive")}</option>
            </select>
            <Button variant="outline" onClick={applyFilters}>
              {t("uoms.applyFilters")}
            </Button>
            {canCreate && (
              <Button className="ms-auto" onClick={openCreate}>
                {t("uoms.createAction")}
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("uoms.listTitle")}</CardTitle>
          <CardDescription>{t("uoms.listCount", { count: String(totalElements) })}</CardDescription>
        </CardHeader>
        <CardContent>
          {pageError ? <p className="mb-2 text-xs text-destructive">{pageError}</p> : null}
          {isLoading ? (
            <p className="text-sm text-muted-foreground">{t("uoms.loading")}</p>
          ) : uoms.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("uoms.empty")}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-start">
                    <th className="py-2 pe-4 text-start font-medium">{t("uoms.tableCode")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("uoms.tableName")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("uoms.tableSymbol")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("uoms.tableStatus")}</th>
                    <th className="py-2 text-start font-medium">{t("uoms.tableActions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {uoms.map((uom) => (
                    <tr key={uom.id} className="border-b last:border-0">
                      <td className="py-2 pe-4 font-medium">{uom.code}</td>
                      <td className="py-2 pe-4">{uom.name}</td>
                      <td className="py-2 pe-4">{uom.symbol ?? "—"}</td>
                      <td className="py-2 pe-4">
                        <span
                          className={
                            uom.active
                              ? "text-emerald-600"
                              : "text-muted-foreground"
                          }
                        >
                          {uom.active ? t("uoms.statusActive") : t("uoms.statusInactive")}
                        </span>
                      </td>
                      <td className="py-2">
                        <div className="flex flex-wrap gap-1">
                          {canEdit && (
                            <Button size="sm" variant="outline" onClick={() => openEdit(uom)}>
                              {t("uoms.editAction")}
                            </Button>
                          )}
                          {canSoftDelete && uom.active && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => void handleSoftDelete(uom)}
                            >
                              {t("uoms.softDeleteAction")}
                            </Button>
                          )}
                          {canRestore && !uom.active && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => void handleRestore(uom)}
                            >
                              {t("uoms.restoreAction")}
                            </Button>
                          )}
                          {canHardDelete && (
                            <Button
                              size="sm"
                              variant="destructive"
                              onClick={() => setHardDeleteTarget(uom)}
                            >
                              {t("uoms.hardDeleteAction")}
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
                  void loadUoms(prev, search, activeFilter);
                }}
              >
                {t("uoms.paginationPrevious")}
              </Button>
              <span className="text-sm text-muted-foreground">
                {t("uoms.paginationInfo", {
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
                  void loadUoms(next, search, activeFilter);
                }}
              >
                {t("uoms.paginationNext")}
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
              {editingUom ? t("uoms.editDialogTitle") : t("uoms.createDialogTitle")}
            </DialogTitle>
            <DialogDescription>
              {editingUom ? t("uoms.editDialogDescription") : t("uoms.createDialogDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="uom-code">{t("uoms.codeLabel")}</Label>
              <Input
                id="uom-code"
                value={formCode}
                onChange={(e) => setFormCode(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="uom-name">{t("uoms.nameLabel")}</Label>
              <Input
                id="uom-name"
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="uom-symbol">{t("uoms.symbolLabel")}</Label>
              <Input
                id="uom-symbol"
                value={formSymbol}
                onChange={(e) => setFormSymbol(e.target.value)}
              />
            </div>
            {formError ? <p className="text-xs text-destructive">{formError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsFormOpen(false)} disabled={isSaving}>
              {t("uoms.cancelAction")}
            </Button>
            <Button onClick={() => void handleFormSave()} disabled={isSaving}>
              {isSaving ? t("uoms.saving") : editingUom ? t("uoms.editAction") : t("uoms.createAction")}
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
            <AlertDialogTitle>{t("uoms.hardDeleteDialogTitle")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("uoms.hardDeleteDialogDescription")}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDeleting}>{t("uoms.cancelAction")}</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => void handleHardDelete()}
              disabled={isDeleting}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {isDeleting ? t("uoms.deleting") : t("uoms.hardDeleteAction")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
