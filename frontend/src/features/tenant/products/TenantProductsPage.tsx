import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
  createProduct,
  extractF0ErrorMessage,
  hardDeleteProduct,
  listCategories,
  listProducts,
  listSuppliers,
  listUoms,
  restoreProduct,
  softDeleteProduct,
  updateProduct,
} from "@/features/tenant/api/f0Api";
import { listHazardTypes } from "@/features/tenant/api/hazardTypeApi";
import type { CategoryResult, HazardTypeResult, ProductResult, SupplierResult, UomResult } from "@/features/tenant/types/f0";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
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
import { ProductLabel } from "@/features/tenant/labels/ProductLabel";

type FilterActive = "all" | "active" | "inactive";

function toActiveParam(filter: FilterActive): boolean | undefined {
  if (filter === "active") return true;
  if (filter === "inactive") return false;
  return undefined;
}

export default function TenantProductsPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");

  const canCreate = hasPermission(TENANT_PERMISSIONS.PRODUCTS_CREATE);
  const canEdit = hasPermission(TENANT_PERMISSIONS.PRODUCTS_EDIT);
  const canSoftDelete = hasPermission(TENANT_PERMISSIONS.PRODUCTS_SOFT_DELETE);
  const canRestore = hasPermission(TENANT_PERMISSIONS.PRODUCTS_RESTORE);
  const canHardDelete = hasPermission(TENANT_PERMISSIONS.PRODUCTS_HARD_DELETE);

  const [products, setProducts] = useState<ProductResult[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);

  const [allUoms, setAllUoms] = useState<UomResult[]>([]);
  const [allSuppliers, setAllSuppliers] = useState<SupplierResult[]>([]);
  const [allCategories, setAllCategories] = useState<CategoryResult[]>([]);
  const [allHazardTypes, setAllHazardTypes] = useState<HazardTypeResult[]>([]);

  const [pendingSearch, setPendingSearch] = useState("");
  const [pendingActive, setPendingActive] = useState<FilterActive>("all");
  const [pendingBaseUomId, setPendingBaseUomId] = useState("");
  const [pendingSupplierId, setPendingSupplierId] = useState("");
  const [pendingCategoryId, setPendingCategoryId] = useState("");
  const [search, setSearch] = useState("");
  const [activeFilter, setActiveFilter] = useState<FilterActive>("all");
  const [baseUomId, setBaseUomId] = useState("");
  const [supplierId, setSupplierId] = useState("");
  const [categoryId, setCategoryId] = useState("");

  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<ProductResult | null>(null);
  const [formSku, setFormSku] = useState("");
  const [formName, setFormName] = useState("");
  const [formDescription, setFormDescription] = useState("");
  const [formBaseUomId, setFormBaseUomId] = useState("");
  const [formCategoryId, setFormCategoryId] = useState("");
  const [formHazardTypeId, setFormHazardTypeId] = useState("");
  const [formTrackLot, setFormTrackLot] = useState(false);
  const [formTrackExpiry, setFormTrackExpiry] = useState(false);
  const [formSupplierIds, setFormSupplierIds] = useState<string[]>([]);
  const [formPrimarySupplierId, setFormPrimarySupplierId] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const [hardDeleteTarget, setHardDeleteTarget] = useState<ProductResult | null>(null);
  const [labelProduct, setLabelProduct] = useState<ProductResult | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const loadLookups = useCallback(async (applyFirstUom = false) => {
    try {
      const [uomsResult, suppliersResult, categoriesResult, hazardTypesResult] = await Promise.all([
        listUoms(slug, { size: 100, active: true }),
        listSuppliers(slug, { size: 100, active: true }),
        listCategories(slug, { size: 100, active: true }),
        listHazardTypes(slug, { active: true }),
      ]);
      setAllUoms(uomsResult.content);
      setAllSuppliers(suppliersResult.content);
      setAllCategories(categoriesResult.content);
      setAllHazardTypes(hazardTypesResult);
      if (applyFirstUom && uomsResult.content.length > 0) {
        setFormBaseUomId((prev) => prev || uomsResult.content[0].id);
      }
    } catch {
      // lookup failure should not block the page
    }
  }, [slug]);

  const loadData = useCallback(
    async (pg: number, srch: string, act: FilterActive, uomId: string, supId: string, catId: string) => {
      setIsLoading(true);
      setPageError(null);
      try {
        const result = await listProducts(slug, {
          page: pg,
          size: 20,
          search: srch || undefined,
          active: toActiveParam(act),
          baseUomId: uomId || undefined,
          supplierId: supId || undefined,
          categoryId: catId || undefined,
        });
        setProducts(result.content);
        setTotalElements(result.totalElements);
        setTotalPages(result.totalPages);
        setPage(result.page);
      } catch (error) {
        setPageError(extractF0ErrorMessage(error) ?? t("products.loadFailed"));
      } finally {
        setIsLoading(false);
      }
    },
    [slug, t]
  );

  useEffect(() => {
    void loadLookups();
    void loadData(0, search, activeFilter, baseUomId, supplierId, categoryId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadLookups, loadData]);

  const applyFilters = () => {
    setSearch(pendingSearch);
    setActiveFilter(pendingActive);
    setBaseUomId(pendingBaseUomId);
    setSupplierId(pendingSupplierId);
    setCategoryId(pendingCategoryId);
    void loadData(0, pendingSearch, pendingActive, pendingBaseUomId, pendingSupplierId, pendingCategoryId);
  };

  const openCreate = () => {
    setEditingProduct(null);
    setFormSku("");
    setFormName("");
    setFormDescription("");
    setFormBaseUomId(allUoms[0]?.id ?? "");
    setFormCategoryId("");
    setFormHazardTypeId("");
    setFormTrackLot(false);
    setFormTrackExpiry(false);
    setFormSupplierIds([]);
    setFormPrimarySupplierId("");
    setFormError(null);
    void loadLookups(true);
    setIsFormOpen(true);
  };

  const openEdit = (product: ProductResult) => {
    void loadLookups();
    setEditingProduct(product);
    setFormSku(product.sku);
    setFormName(product.name);
    setFormDescription(product.description ?? "");
    setFormBaseUomId(product.baseUomId);
    setFormCategoryId(product.categoryId ?? "");
    setFormHazardTypeId(product.hazardTypeId ?? "");
    setFormTrackLot(product.trackLot);
    setFormTrackExpiry(product.trackExpiry);
    const ids = product.suppliers.map((s) => s.supplierId);
    setFormSupplierIds(ids);
    const primary = product.suppliers.find((s) => s.primary);
    setFormPrimarySupplierId(primary?.supplierId ?? "");
    setFormError(null);
    setIsFormOpen(true);
  };

  const toggleFormSupplier = (id: string) => {
    setFormSupplierIds((prev) =>
      prev.includes(id) ? prev.filter((s) => s !== id) : [...prev, id]
    );
    if (formPrimarySupplierId === id) {
      setFormPrimarySupplierId("");
    }
  };

  const handleFormSave = async () => {
    if (!formSku.trim() || !formName.trim()) {
      setFormError(t("products.validationSkuName"));
      return;
    }
    if (!formBaseUomId) {
      setFormError(t("products.validationBaseUom"));
      return;
    }
    if (formPrimarySupplierId && !formSupplierIds.includes(formPrimarySupplierId)) {
      setFormError(t("products.validationPrimarySupplier"));
      return;
    }
    setIsSaving(true);
    setFormError(null);
    try {
      const payload = {
        sku: formSku.trim(),
        name: formName.trim(),
        description: formDescription.trim() || null,
        baseUomId: formBaseUomId,
        categoryId: formCategoryId || null,
        hazardTypeId: formHazardTypeId || null,
        trackLot: formTrackLot,
        trackExpiry: formTrackExpiry,
        supplierIds: formSupplierIds,
        primarySupplierId: formPrimarySupplierId || null,
      };
      if (editingProduct) {
        await updateProduct(slug, editingProduct.id, payload);
      } else {
        await createProduct(slug, payload);
      }
      setIsFormOpen(false);
      void loadData(0, search, activeFilter, baseUomId, supplierId, categoryId);
    } catch (error) {
      setFormError(extractF0ErrorMessage(error) ?? t("products.actionFailed"));
    } finally {
      setIsSaving(false);
    }
  };

  const handleSoftDelete = async (product: ProductResult) => {
    try {
      await softDeleteProduct(slug, product.id);
      void loadData(page, search, activeFilter, baseUomId, supplierId, categoryId);
    } catch (error) {
      setPageError(extractF0ErrorMessage(error) ?? t("products.actionFailed"));
    }
  };

  const handleRestore = async (product: ProductResult) => {
    try {
      await restoreProduct(slug, product.id);
      void loadData(page, search, activeFilter, baseUomId, supplierId, categoryId);
    } catch (error) {
      setPageError(extractF0ErrorMessage(error) ?? t("products.actionFailed"));
    }
  };

  const handleHardDelete = async () => {
    if (!hardDeleteTarget) return;
    setIsDeleting(true);
    try {
      await hardDeleteProduct(slug, hardDeleteTarget.id);
      setHardDeleteTarget(null);
      void loadData(0, search, activeFilter, baseUomId, supplierId, categoryId);
    } catch (error) {
      setPageError(extractF0ErrorMessage(error) ?? t("products.actionFailed"));
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold">{t("products.pageTitle")}</h1>
        <p className="text-sm text-muted-foreground">{t("products.pageDescription")}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("products.filtersTitle")}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-3">
            <Input
              className="max-w-xs"
              placeholder={t("products.searchPlaceholder")}
              value={pendingSearch}
              onChange={(e) => setPendingSearch(e.target.value)}
            />
            <select
              className="rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={pendingActive}
              onChange={(e) => setPendingActive(e.target.value as FilterActive)}
              aria-label={t("products.activeFilterLabel")}
            >
              <option value="all">{t("products.activeFilterAll")}</option>
              <option value="active">{t("products.activeFilterActive")}</option>
              <option value="inactive">{t("products.activeFilterInactive")}</option>
            </select>
            <select
              className="rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={pendingBaseUomId}
              onChange={(e) => setPendingBaseUomId(e.target.value)}
              aria-label={t("products.uomFilterLabel")}
            >
              <option value="">{t("products.uomFilterAll")}</option>
              {allUoms.map((uom) => (
                <option key={uom.id} value={uom.id}>
                  {uom.code} – {uom.name}
                </option>
              ))}
            </select>
            <select
              className="rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={pendingSupplierId}
              onChange={(e) => setPendingSupplierId(e.target.value)}
              aria-label={t("products.supplierFilterLabel")}
            >
              <option value="">{t("products.supplierFilterAll")}</option>
              {allSuppliers.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.code} – {s.name}
                </option>
              ))}
            </select>
            <select
              className="rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={pendingCategoryId}
              onChange={(e) => setPendingCategoryId(e.target.value)}
              aria-label={t("products.categoryFilterLabel")}
            >
              <option value="">{t("products.categoryFilterAll")}</option>
              {allCategories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
            <Button variant="outline" onClick={applyFilters}>
              {t("products.applyFilters")}
            </Button>
            {canCreate && (
              <Button className="ms-auto" onClick={openCreate}>
                {t("products.createAction")}
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("products.listTitle")}</CardTitle>
          <CardDescription>
            {t("products.listCount", { count: String(totalElements) })}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {pageError ? <p className="mb-2 text-xs text-destructive">{pageError}</p> : null}
          {isLoading ? (
            <p className="text-sm text-muted-foreground">{t("products.loading")}</p>
          ) : products.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("products.empty")}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="py-2 pe-4 text-start font-medium">{t("products.tableSku")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("products.tableName")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("products.tableUom")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("products.tableCategory")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("products.tableStatus")}</th>
                    <th className="py-2 text-start font-medium">{t("products.tableActions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {products.map((product) => (
                    <tr key={product.id} className="border-b last:border-0">
                      <td className="py-2 pe-4 font-medium">{product.sku}</td>
                      <td className="py-2 pe-4">{product.name}</td>
                      <td className="py-2 pe-4 text-muted-foreground">
                        {product.baseUomCode}
                      </td>
                      <td className="py-2 pe-4 text-muted-foreground">
                        {product.categoryName ?? "—"}
                      </td>
                      <td className="py-2 pe-4">
                        <span
                          className={
                            product.active ? "text-emerald-600" : "text-muted-foreground"
                          }
                        >
                          {product.active
                            ? t("products.statusActive")
                            : t("products.statusInactive")}
                        </span>
                      </td>
                      <td className="py-2">
                        <div className="flex flex-wrap gap-1">
                          <Button size="sm" variant="outline" onClick={() => setLabelProduct(product)}>
                            {t("labels.printLabel")}
                          </Button>
                          {canEdit && (
                            <Button size="sm" variant="outline" onClick={() => openEdit(product)}>
                              {t("products.editAction")}
                            </Button>
                          )}
                          {canSoftDelete && product.active && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => void handleSoftDelete(product)}
                            >
                              {t("products.softDeleteAction")}
                            </Button>
                          )}
                          {canRestore && !product.active && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => void handleRestore(product)}
                            >
                              {t("products.restoreAction")}
                            </Button>
                          )}
                          {canHardDelete && (
                            <Button
                              size="sm"
                              variant="destructive"
                              onClick={() => setHardDeleteTarget(product)}
                            >
                              {t("products.hardDeleteAction")}
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
                  void loadData(prev, search, activeFilter, baseUomId, supplierId, categoryId);
                }}
              >
                {t("products.paginationPrevious")}
              </Button>
              <span className="text-sm text-muted-foreground">
                {t("products.paginationInfo", {
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
                  void loadData(next, search, activeFilter, baseUomId, supplierId, categoryId);
                }}
              >
                {t("products.paginationNext")}
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Create / Edit dialog */}
      <Dialog open={isFormOpen} onOpenChange={setIsFormOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>
              {editingProduct ? t("products.editDialogTitle") : t("products.createDialogTitle")}
            </DialogTitle>
            <DialogDescription>
              {editingProduct
                ? t("products.editDialogDescription")
                : t("products.createDialogDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="product-sku">{t("products.skuLabel")}</Label>
              <Input
                id="product-sku"
                value={formSku}
                onChange={(e) => setFormSku(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="product-name">{t("products.nameLabel")}</Label>
              <Input
                id="product-name"
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="product-description">{t("products.descriptionLabel")}</Label>
              <Input
                id="product-description"
                value={formDescription}
                onChange={(e) => setFormDescription(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="product-base-uom">{t("products.baseUomLabel")}</Label>
              <select
                id="product-base-uom"
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={formBaseUomId}
                onChange={(e) => setFormBaseUomId(e.target.value)}
              >
                <option value="">{t("products.baseUomPlaceholder")}</option>
                {allUoms.map((uom) => (
                  <option key={uom.id} value={uom.id}>
                    {uom.code} – {uom.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="product-category">{t("products.categoryLabel")}</Label>
              <select
                id="product-category"
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={formCategoryId}
                onChange={(e) => setFormCategoryId(e.target.value)}
              >
                <option value="">{t("products.categoryPlaceholder")}</option>
                {allCategories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="product-hazard-type">{t("products.hazardTypeLabel")}</Label>
              <select
                id="product-hazard-type"
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                value={formHazardTypeId}
                onChange={(e) => setFormHazardTypeId(e.target.value)}
              >
                <option value="">{t("products.hazardTypePlaceholder")}</option>
                {allHazardTypes.map((ht) => (
                  <option key={ht.id} value={ht.id}>
                    {ht.code} — {ht.displayName}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex gap-4">
              <label className="flex items-center gap-2 text-sm">
                <Checkbox
                  checked={formTrackLot}
                  onCheckedChange={(v) => setFormTrackLot(v === true)}
                />
                {t("products.trackLotLabel")}
              </label>
              <label className="flex items-center gap-2 text-sm">
                <Checkbox
                  checked={formTrackExpiry}
                  onCheckedChange={(v) => setFormTrackExpiry(v === true)}
                />
                {t("products.trackExpiryLabel")}
              </label>
            </div>
            {allSuppliers.length > 0 && (
              <div className="space-y-2">
                <Label>{t("products.suppliersLabel")}</Label>
                <div className="max-h-40 space-y-1 overflow-y-auto border p-2">
                  {allSuppliers.map((s) => (
                    <label key={s.id} className="flex items-center gap-2 text-sm">
                      <Checkbox
                        checked={formSupplierIds.includes(s.id)}
                        onCheckedChange={() => toggleFormSupplier(s.id)}
                      />
                      {s.code} – {s.name}
                    </label>
                  ))}
                </div>
              </div>
            )}
            {formSupplierIds.length > 0 && (
              <div className="space-y-1">
                <Label htmlFor="product-primary-supplier">{t("products.primarySupplierLabel")}</Label>
                <select
                  id="product-primary-supplier"
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  value={formPrimarySupplierId}
                  onChange={(e) => setFormPrimarySupplierId(e.target.value)}
                >
                  <option value="">{t("products.primarySupplierPlaceholder")}</option>
                  {allSuppliers
                    .filter((s) => formSupplierIds.includes(s.id))
                    .map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.code} – {s.name}
                      </option>
                    ))}
                </select>
              </div>
            )}
            {formError ? <p className="text-xs text-destructive">{formError}</p> : null}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsFormOpen(false)} disabled={isSaving}>
              {t("products.cancelAction")}
            </Button>
            <Button onClick={() => void handleFormSave()} disabled={isSaving}>
              {isSaving
                ? t("products.saving")
                : editingProduct
                  ? t("products.editAction")
                  : t("products.createAction")}
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
            <AlertDialogTitle>{t("products.hardDeleteDialogTitle")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("products.hardDeleteDialogDescription")}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDeleting}>
              {t("products.cancelAction")}
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={() => void handleHardDelete()}
              disabled={isDeleting}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {isDeleting ? t("products.deleting") : t("products.hardDeleteAction")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog open={labelProduct !== null} onOpenChange={(open) => { if (!open) setLabelProduct(null); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("labels.printLabel")}</DialogTitle>
          </DialogHeader>
          {labelProduct ? (
            <ProductLabel sku={labelProduct.sku} name={labelProduct.name} categoryName={labelProduct.categoryName} />
          ) : null}
        </DialogContent>
      </Dialog>
    </div>
  );
}
