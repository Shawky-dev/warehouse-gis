import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
  createSupplier,
  extractF0ErrorMessage,
  hardDeleteSupplier,
  listSuppliers,
  restoreSupplier,
  softDeleteSupplier,
  updateSupplier,
} from "@/features/tenant/api/f0Api";
import type { SupplierResult } from "@/features/tenant/types/f0";
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

export default function TenantSuppliersPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");

  const canCreate = hasPermission(TENANT_PERMISSIONS.SUPPLIERS_CREATE);
  const canEdit = hasPermission(TENANT_PERMISSIONS.SUPPLIERS_EDIT);
  const canSoftDelete = hasPermission(TENANT_PERMISSIONS.SUPPLIERS_SOFT_DELETE);
  const canRestore = hasPermission(TENANT_PERMISSIONS.SUPPLIERS_RESTORE);
  const canHardDelete = hasPermission(TENANT_PERMISSIONS.SUPPLIERS_HARD_DELETE);

  const [suppliers, setSuppliers] = useState<SupplierResult[]>([]);
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
  const [editingSupplier, setEditingSupplier] = useState<SupplierResult | null>(null);
  const [formCode, setFormCode] = useState("");
  const [formName, setFormName] = useState("");
  const [formContactName, setFormContactName] = useState("");
  const [formContactEmail, setFormContactEmail] = useState("");
  const [formContactPhone, setFormContactPhone] = useState("");
  const [formNotes, setFormNotes] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [hardDeleteTarget, setHardDeleteTarget] = useState<SupplierResult | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const loadData = useCallback(
    async (pg: number, srch: string, act: FilterActive) => {
      setIsLoading(true);
      setPageError(null);
      try {
        const result = await listSuppliers(slug, {
          page: pg,
          size: 20,
          search: srch || undefined,
          active: toActiveParam(act),
        });
        setSuppliers(result.content);
        setTotalElements(result.totalElements);
        setTotalPages(result.totalPages);
        setPage(result.page);
      } catch (error) {
        setPageError(extractF0ErrorMessage(error) ?? t("suppliers.loadFailed"));
      } finally {
        setIsLoading(false);
      }
    },
    [slug, t]
  );

  useEffect(() => {
    void loadData(0, search, activeFilter);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadData]);

  const applyFilters = () => {
    setSearch(pendingSearch);
    setActiveFilter(pendingActive);
    void loadData(0, pendingSearch, pendingActive);
  };

  const openCreate = () => {
    setEditingSupplier(null);
    setFormCode("");
    setFormName("");
    setFormContactName("");
    setFormContactEmail("");
    setFormContactPhone("");
    setFormNotes("");
    setFormError(null);
    setIsFormOpen(true);
  };

  const openEdit = (supplier: SupplierResult) => {
    setEditingSupplier(supplier);
    setFormCode(supplier.code);
    setFormName(supplier.name);
    setFormContactName(supplier.contactName ?? "");
    setFormContactEmail(supplier.contactEmail ?? "");
    setFormContactPhone(supplier.contactPhone ?? "");
    setFormNotes(supplier.notes ?? "");
    setFormError(null);
    setIsFormOpen(true);
  };

  const handleFormSave = async () => {
    if (!formCode.trim() || !formName.trim()) {
      setFormError(t("suppliers.validationRequired"));
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      const payload = {
        code: formCode.trim(),
        name: formName.trim(),
        contactName: formContactName.trim() || null,
        contactEmail: formContactEmail.trim() || null,
        contactPhone: formContactPhone.trim() || null,
        notes: formNotes.trim() || null,
      };
      if (editingSupplier) {
        await updateSupplier(slug, editingSupplier.id, payload);
      } else {
        await createSupplier(slug, payload);
      }
      setIsFormOpen(false);
      void loadData(0, search, activeFilter);
    } catch (error) {
      setFormError(extractF0ErrorMessage(error) ?? t("suppliers.actionFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  const handleSoftDelete = async (supplier: SupplierResult) => {
    try {
      await softDeleteSupplier(slug, supplier.id);
      void loadData(page, search, activeFilter);
    } catch (error) {
      setPageError(extractF0ErrorMessage(error) ?? t("suppliers.actionFailed"));
    }
  };

  const handleRestore = async (supplier: SupplierResult) => {
    try {
      await restoreSupplier(slug, supplier.id);
      void loadData(page, search, activeFilter);
    } catch (error) {
      setPageError(extractF0ErrorMessage(error) ?? t("suppliers.actionFailed"));
    }
  };

  const handleHardDelete = async () => {
    if (!hardDeleteTarget) return;
    setIsDeleting(true);
    try {
      await hardDeleteSupplier(slug, hardDeleteTarget.id);
      setHardDeleteTarget(null);
      void loadData(0, search, activeFilter);
    } catch (error) {
      setPageError(extractF0ErrorMessage(error) ?? t("suppliers.actionFailed"));
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold">{t("suppliers.pageTitle")}</h1>
        <p className="text-sm text-muted-foreground">{t("suppliers.pageDescription")}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("suppliers.filtersTitle")}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-3">
            <Input
              className="max-w-xs"
              placeholder={t("suppliers.searchPlaceholder")}
              value={pendingSearch}
              onChange={(e) => setPendingSearch(e.target.value)}
            />
            <select
              className="rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={pendingActive}
              onChange={(e) => setPendingActive(e.target.value as FilterActive)}
              aria-label={t("suppliers.activeFilterLabel")}
            >
              <option value="all">{t("suppliers.activeFilterAll")}</option>
              <option value="active">{t("suppliers.activeFilterActive")}</option>
              <option value="inactive">{t("suppliers.activeFilterInactive")}</option>
            </select>
            <Button variant="outline" onClick={applyFilters}>
              {t("suppliers.applyFilters")}
            </Button>
            {canCreate && (
              <Button className="ms-auto" onClick={openCreate}>
                {t("suppliers.createAction")}
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("suppliers.listTitle")}</CardTitle>
          <CardDescription>
            {t("suppliers.listCount", { count: String(totalElements) })}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {pageError ? <p className="mb-2 text-xs text-destructive">{pageError}</p> : null}
          {isLoading ? (
            <p className="text-sm text-muted-foreground">{t("suppliers.loading")}</p>
          ) : suppliers.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("suppliers.empty")}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="py-2 pe-4 text-start font-medium">{t("suppliers.tableCode")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("suppliers.tableName")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("suppliers.tableContact")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("suppliers.tableStatus")}</th>
                    <th className="py-2 text-start font-medium">{t("suppliers.tableActions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {suppliers.map((supplier) => (
                    <tr key={supplier.id} className="border-b last:border-0">
                      <td className="py-2 pe-4 font-medium">{supplier.code}</td>
                      <td className="py-2 pe-4">{supplier.name}</td>
                      <td className="py-2 pe-4 text-muted-foreground">
                        {supplier.contactEmail ?? supplier.contactName ?? "—"}
                      </td>
                      <td className="py-2 pe-4">
                        <span
                          className={
                            supplier.active ? "text-emerald-600" : "text-muted-foreground"
                          }
                        >
                          {supplier.active
                            ? t("suppliers.statusActive")
                            : t("suppliers.statusInactive")}
                        </span>
                      </td>
                      <td className="py-2">
                        <div className="flex flex-wrap gap-1">
                          {canEdit && (
                            <Button size="sm" variant="outline" onClick={() => openEdit(supplier)}>
                              {t("suppliers.editAction")}
                            </Button>
                          )}
                          {canSoftDelete && supplier.active && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => void handleSoftDelete(supplier)}
                            >
                              {t("suppliers.softDeleteAction")}
                            </Button>
                          )}
                          {canRestore && !supplier.active && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => void handleRestore(supplier)}
                            >
                              {t("suppliers.restoreAction")}
                            </Button>
                          )}
                          {canHardDelete && (
                            <Button
                              size="sm"
                              variant="destructive"
                              onClick={() => setHardDeleteTarget(supplier)}
                            >
                              {t("suppliers.hardDeleteAction")}
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
                  void loadData(prev, search, activeFilter);
                }}
              >
                {t("suppliers.paginationPrevious")}
              </Button>
              <span className="text-sm text-muted-foreground">
                {t("suppliers.paginationInfo", {
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
                  void loadData(next, search, activeFilter);
                }}
              >
                {t("suppliers.paginationNext")}
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
              {editingSupplier ? t("suppliers.editDialogTitle") : t("suppliers.createDialogTitle")}
            </DialogTitle>
            <DialogDescription>
              {editingSupplier
                ? t("suppliers.editDialogDescription")
                : t("suppliers.createDialogDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="supplier-code">{t("suppliers.codeLabel")}</Label>
              <Input
                id="supplier-code"
                value={formCode}
                onChange={(e) => setFormCode(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="supplier-name">{t("suppliers.nameLabel")}</Label>
              <Input
                id="supplier-name"
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="supplier-contact-name">{t("suppliers.contactNameLabel")}</Label>
              <Input
                id="supplier-contact-name"
                value={formContactName}
                onChange={(e) => setFormContactName(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="supplier-contact-email">{t("suppliers.contactEmailLabel")}</Label>
              <Input
                id="supplier-contact-email"
                type="email"
                value={formContactEmail}
                onChange={(e) => setFormContactEmail(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="supplier-contact-phone">{t("suppliers.contactPhoneLabel")}</Label>
              <Input
                id="supplier-contact-phone"
                value={formContactPhone}
                onChange={(e) => setFormContactPhone(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="supplier-notes">{t("suppliers.notesLabel")}</Label>
              <Input
                id="supplier-notes"
                value={formNotes}
                onChange={(e) => setFormNotes(e.target.value)}
              />
            </div>
            {formError ? <p className="text-xs text-destructive">{formError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsFormOpen(false)} disabled={isSaving}>
              {t("suppliers.cancelAction")}
            </Button>
            <Button onClick={() => void handleFormSave()} disabled={isSaving}>
              {isSaving
                ? t("suppliers.saving")
                : editingSupplier
                  ? t("suppliers.editAction")
                  : t("suppliers.createAction")}
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
            <AlertDialogTitle>{t("suppliers.hardDeleteDialogTitle")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("suppliers.hardDeleteDialogDescription")}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDeleting}>
              {t("suppliers.cancelAction")}
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={() => void handleHardDelete()}
              disabled={isDeleting}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {isDeleting ? t("suppliers.deleting") : t("suppliers.hardDeleteAction")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
