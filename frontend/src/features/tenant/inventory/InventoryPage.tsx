import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  ArrowLeftRight,
  Plus,
  RefreshCw,
} from "lucide-react";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import { PATHS } from "@/shared/consts/paths";
import {
  adjustStock,
  extractInventoryErrorMessage,
  getLocationLookups,
  getMovements,
  getStock,
  getProductLookups,
  receiveStock,
  transferStock,
} from "@/features/tenant/api/inventoryApi";
import type {
  LocationLookupItem,
  MovementPageResult,
  MovementResult,
  StockEntry,
  ProductLookupItem,
} from "@/features/tenant/types/inventory";
import { Badge } from "@/shared/components/ui/badge";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";
import { Textarea } from "@/shared/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/shared/components/ui/select";
import {
  Combobox,
  ComboboxCollection,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
} from "@/shared/components/ui/combobox";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

type Tab = "stock" | "operations" | "movements";
type Operation = "receive" | "transfer" | "adjust";
type AdjustmentDirection = "increase" | "decrease";

interface InventoryPageProps {
  section?: Tab;
}

interface OperationFormState {
  locationId: string;
  fromLocationId: string;
  toLocationId: string;
  productId: string;
  qty: string;
  reasonCode: string;
  lotNumber: string;
  expiryDate: string;
  notes: string;
  adjustmentDirection: AdjustmentDirection;
}

const DEFAULT_OPERATION_FORM: OperationFormState = {
  locationId: "",
  fromLocationId: "",
  toLocationId: "",
  productId: "",
  qty: "",
  reasonCode: "",
  lotNumber: "",
  expiryDate: "",
  notes: "",
  adjustmentDirection: "increase",
};

export default function InventoryPage({ section = "stock" }: InventoryPageProps) {
  const { t, locale } = useI18n();
  const { hasPermission } = useAuth();
  const navigate = useNavigate();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const [searchParams] = useSearchParams();
  const slug = normalizeTenantSlug(tenantSlug ?? "");
  const activeTab = section;

  const canView = hasPermission(TENANT_PERMISSIONS.INVENTORY_VIEW);
  const canReceive = hasPermission(TENANT_PERMISSIONS.INVENTORY_RECEIVE);
  const canTransfer = hasPermission(TENANT_PERMISSIONS.INVENTORY_TRANSFER);
  const canAdjust = hasPermission(TENANT_PERMISSIONS.INVENTORY_ADJUST);

  const availableOperations: Operation[] = [
    ...(canReceive ? ["receive" as const] : []),
    ...(canTransfer ? ["transfer" as const] : []),
    ...(canAdjust ? ["adjust" as const] : []),
  ];
  const [operation, setOperation] = useState<Operation>(availableOperations[0] ?? "receive");

  const [products, setProducts] = useState<ProductLookupItem[]>([]);
  const [locations, setLocations] = useState<LocationLookupItem[]>([]);
  const [productSearch, setProductSearch] = useState("");
  const [locationSearch, setLocationSearch] = useState("");

  const [lookupLoading, setLookupLoading] = useState(false);
  const [lookupError, setLookupError] = useState<string | null>(null);

  const [stockFilters, setStockFilters] = useState({ productId: "", locationId: "" });
  const [stock, setStock] = useState<StockEntry[]>([]);
  const [stockLoading, setStockLoading] = useState(false);
  const [stockError, setStockError] = useState<string | null>(null);

  const [opForm, setOpForm] = useState<OperationFormState>(DEFAULT_OPERATION_FORM);
  const [opSubmitting, setOpSubmitting] = useState(false);
  const [opSuccess, setOpSuccess] = useState<string | null>(null);
  const [opError, setOpError] = useState<string | null>(null);

  const [movementFilters, setMovementFilters] = useState({ productId: "", locationId: "" });
  const [movPage, setMovPage] = useState(0);
  const [movData, setMovData] = useState<MovementPageResult | null>(null);
  const [movLoading, setMovLoading] = useState(false);
  const [movError, setMovError] = useState<string | null>(null);

  const isRtl = locale === "ar";
  const hasSelectableLocations = locations.length > 0;

  const selectedOpProduct = useMemo(
    () => products.find((product) => product.id === opForm.productId) ?? null,
    [products, opForm.productId]
  );
  const selectedStockProduct = useMemo(
    () => products.find((product) => product.id === stockFilters.productId) ?? null,
    [products, stockFilters.productId]
  );
  const selectedStockLocation = useMemo(
    () => locations.find((location) => location.id === stockFilters.locationId) ?? null,
    [locations, stockFilters.locationId]
  );
  const selectedMovementProduct = useMemo(
    () => products.find((product) => product.id === movementFilters.productId) ?? null,
    [products, movementFilters.productId]
  );
  const selectedMovementLocation = useMemo(
    () => locations.find((location) => location.id === movementFilters.locationId) ?? null,
    [locations, movementFilters.locationId]
  );
  const selectedOpLocation = useMemo(
    () => locations.find((location) => location.id === opForm.locationId) ?? null,
    [locations, opForm.locationId]
  );
  const selectedFromLocation = useMemo(
    () => locations.find((location) => location.id === opForm.fromLocationId) ?? null,
    [locations, opForm.fromLocationId]
  );
  const selectedToLocation = useMemo(
    () => locations.find((location) => location.id === opForm.toLocationId) ?? null,
    [locations, opForm.toLocationId]
  );

  const transferDestinationOptions = useMemo(
    () => locations.filter((location) => location.id !== opForm.fromLocationId),
    [locations, opForm.fromLocationId]
  );

  const sortedStock = useMemo(
    () =>
      [...stock].sort((left, right) => {
        const leftProduct = (left.productName ?? left.productSku ?? left.productId).toLowerCase();
        const rightProduct = (right.productName ?? right.productSku ?? right.productId).toLowerCase();
        const byProduct = leftProduct.localeCompare(rightProduct);
        if (byProduct !== 0) {
          return byProduct;
        }

        const leftLocation = (left.locationPathLabel ?? left.locationLabel ?? left.locationId).toLowerCase();
        const rightLocation = (right.locationPathLabel ?? right.locationLabel ?? right.locationId).toLowerCase();
        const byLocation = leftLocation.localeCompare(rightLocation);
        if (byLocation !== 0) {
          return byLocation;
        }

        const leftLot = (left.lotNumber ?? "").toLowerCase();
        const rightLot = (right.lotNumber ?? "").toLowerCase();
        return leftLot.localeCompare(rightLot);
      }),
    [stock]
  );

  useEffect(() => {
    const nextOperation = availableOperations[0];
    if (nextOperation && !availableOperations.includes(operation)) {
      setOperation(nextOperation);
    }
  }, [availableOperations, operation]);

  useEffect(() => {
    if (activeTab !== "operations") {
      return;
    }

    const opFromQuery = searchParams.get("operation");
    const productId = searchParams.get("productId") ?? "";
    const locationId = searchParams.get("locationId") ?? "";

    const nextOperation =
      opFromQuery === "receive" || opFromQuery === "transfer" || opFromQuery === "adjust"
        ? opFromQuery
        : null;

    if (nextOperation && availableOperations.includes(nextOperation)) {
      setOperation(nextOperation);
    }

    if (productId || locationId) {
      setOpForm((current) => ({
        ...current,
        productId: productId || current.productId,
        locationId: locationId || current.locationId,
        fromLocationId:
          (nextOperation ?? operation) === "transfer" ? (locationId || current.fromLocationId) : current.fromLocationId,
      }));
    }
  }, [activeTab, availableOperations, operation, searchParams]);

  useEffect(() => {
    if (activeTab !== "movements" || !canView) {
      return;
    }

    const productId = searchParams.get("productId") ?? "";
    const locationId = searchParams.get("locationId") ?? "";
    if (!productId && !locationId) {
      return;
    }

    const nextFilters = { productId, locationId };
    setMovementFilters(nextFilters);
    setMovPage(0);
    void loadMovements(nextFilters, 0);
  }, [activeTab, canView, searchParams]);

  useEffect(() => {
    void loadPickers();
  }, [slug]);

  useEffect(() => {
    if (activeTab === "stock" && canView) {
      void loadStock(stockFilters);
    }
  }, [activeTab, canView]);

  useEffect(() => {
    if (activeTab === "movements" && canView) {
      void loadMovements(movementFilters, movPage);
    }
  }, [activeTab, canView, movPage]);

  async function loadPickers() {
    setLookupLoading(true);
    setLookupError(null);
    try {
      const [productPage, locationPage] = await Promise.all([
        getProductLookups(slug, { search: productSearch || undefined }),
        getLocationLookups(slug, { search: locationSearch || undefined }),
      ]);
      setProducts(productPage.content);
      setLocations(locationPage.content);
    } catch (error) {
      setLookupError(extractInventoryErrorMessage(error, t("inventory.lookups.loadFailed")));
    } finally {
      setLookupLoading(false);
    }
  }

  async function searchProducts(search: string) {
    setProductSearch(search);
    try {
      const result = await getProductLookups(slug, { search: search || undefined });
      setProducts(mergeById(result.content, products));
    } catch (error) {
      setLookupError(extractInventoryErrorMessage(error, t("inventory.lookups.loadFailed")));
    }
  }

  async function searchLocations(search: string) {
    setLocationSearch(search);
    try {
      const result = await getLocationLookups(slug, { search: search || undefined });
      setLocations(mergeById(result.content, locations));
    } catch (error) {
      setLookupError(extractInventoryErrorMessage(error, t("inventory.lookups.loadFailed")));
    }
  }

  async function loadStock(filters: { productId: string; locationId: string }) {
    setStockLoading(true);
    setStockError(null);
    try {
      const result = await getStock(slug, {
        productId: filters.productId || undefined,
        locationId: filters.locationId || undefined,
      });
      setStock(result);
    } catch (error) {
      setStockError(extractInventoryErrorMessage(error, t("inventory.stock.loadFailed")));
    } finally {
      setStockLoading(false);
    }
  }

  async function loadMovements(filters: { productId: string; locationId: string }, page: number) {
    setMovLoading(true);
    setMovError(null);
    try {
      const result = await getMovements(slug, {
        productId: filters.productId || undefined,
        locationId: filters.locationId || undefined,
        page,
        size: 25,
      });
      setMovData(result);
    } catch (error) {
      setMovError(extractInventoryErrorMessage(error, t("inventory.movements.loadFailed")));
    } finally {
      setMovLoading(false);
    }
  }

  function resetOperationForm() {
    setOpForm(DEFAULT_OPERATION_FORM);
    setOpSuccess(null);
    setOpError(null);
  }

  function switchOperation(nextOperation: Operation) {
    setOperation(nextOperation);
    resetOperationForm();
  }

  async function handleOperationSubmit(event: FormEvent) {
    event.preventDefault();
    if (!availableOperations.includes(operation)) {
      setOpError(t("inventory.ops.notAllowed"));
      return;
    }
    if (!hasSelectableLocations) {
      setOpError(t("inventory.ops.noLocations"));
      return;
    }
    if (!opForm.productId || !opForm.qty) {
      setOpError(t("inventory.ops.validationRequired"));
      return;
    }
    if (operation === "transfer" && (!opForm.fromLocationId || !opForm.toLocationId)) {
      setOpError(t("inventory.ops.validationRequired"));
      return;
    }
    if (operation !== "transfer" && !opForm.locationId) {
      setOpError(t("inventory.ops.validationRequired"));
      return;
    }
    if (operation === "adjust" && !opForm.notes.trim()) {
      setOpError(t("inventory.ops.notesRequired"));
      return;
    }

    setOpSubmitting(true);
    setOpError(null);
    setOpSuccess(null);

    try {
      if (operation === "receive") {
        await receiveStock(slug, {
          locationId: opForm.locationId,
          productId: opForm.productId,
          qty: opForm.qty,
          lotNumber: selectedOpProduct?.trackLot ? opForm.lotNumber || null : null,
          expiryDate: selectedOpProduct?.trackExpiry ? opForm.expiryDate || null : null,
          notes: opForm.notes || null,
        });
        setOpSuccess(t("inventory.ops.successReceive"));
      } else if (operation === "transfer") {
        await transferStock(slug, {
          fromLocationId: opForm.fromLocationId,
          toLocationId: opForm.toLocationId,
          productId: opForm.productId,
          qty: opForm.qty,
          lotNumber: selectedOpProduct?.trackLot ? opForm.lotNumber || null : null,
          notes: opForm.notes || null,
        });
        setOpSuccess(t("inventory.ops.successTransfer"));
      } else {
        const normalizedQty = Number.parseFloat(opForm.qty || "0");
        const signedQty =
          opForm.adjustmentDirection === "decrease" ? String(normalizedQty * -1) : String(normalizedQty);
        await adjustStock(slug, {
          locationId: opForm.locationId,
          productId: opForm.productId,
          qty: signedQty,
          notes: opForm.notes,
          reasonCode: opForm.reasonCode.trim() ? opForm.reasonCode.trim() : null,
        });
        setOpSuccess(t("inventory.ops.successAdjust"));
      }

      setOpForm(DEFAULT_OPERATION_FORM);
      if (canView) {
        void loadStock(stockFilters);
        if (activeTab === "movements") {
          void loadMovements(movementFilters, movPage);
        }
      }
    } catch (error) {
      setOpError(extractInventoryErrorMessage(error, t("inventory.ops.failed")));
    } finally {
      setOpSubmitting(false);
    }
  }

  function handleStockApplyFilters(event: FormEvent) {
    event.preventDefault();
    void loadStock(stockFilters);
  }

  function handleMovementApplyFilters(event: FormEvent) {
    event.preventDefault();
    setMovPage(0);
    void loadMovements(movementFilters, 0);
  }

  function handleQuickAction(action: "receive" | "transfer" | "adjust" | "movements", row: StockEntry) {
    const params = new URLSearchParams({
      productId: row.productId,
      locationId: row.locationId,
    });

    if (action === "movements") {
      navigate(`${PATHS.TENANT.inventoryMovements(slug)}?${params.toString()}`);
      return;
    }

    params.set("operation", action);
    navigate(`${PATHS.TENANT.inventoryOperations(slug)}?${params.toString()}`);
  }
  const operationHelpKey = {
    receive: "inventory.ops.helper.receive",
    transfer: "inventory.ops.helper.transfer",
    adjust: "inventory.ops.helper.adjust",
  } as const;
  const movementTypeKey = {
    RECEIVE: "inventory.movements.type.RECEIVE",
    TRANSFER_IN: "inventory.movements.type.TRANSFER_IN",
    TRANSFER_OUT: "inventory.movements.type.TRANSFER_OUT",
    ADJUST: "inventory.movements.type.ADJUST",
    PICK: "inventory.movements.type.PICK",
  } as const;

  return (
    <div className="flex flex-col gap-6 p-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold">{t("inventory.pageTitle")}</h1>
        <p className="text-sm text-muted-foreground">{t("inventory.pageDescription")}</p>
      </div>

      {lookupError ? <p className="text-sm text-destructive">{lookupError}</p> : null}

      {activeTab === "stock" ? (
        <div className="flex flex-col gap-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t("inventory.stock.filtersTitle")}</CardTitle>
              <CardDescription>{t("inventory.stock.filtersDescription")}</CardDescription>
            </CardHeader>
            <CardContent>
              <form className="grid gap-4 lg:grid-cols-[1fr_1fr_auto_auto]" onSubmit={handleStockApplyFilters}>
                <PickerField
                  id="stock-product"
                  label={t("inventory.filters.product")}
                  value={selectedStockProduct}
                  options={products}
                  onSearch={searchProducts}
                  onChange={(next) => setStockFilters((current) => ({ ...current, productId: next?.id ?? "" }))}
                  placeholder={t("inventory.lookups.productPlaceholder")}
                  emptyMessage={t("inventory.lookups.noProducts")}
                  loading={lookupLoading}
                  renderOption={renderProductOption}
                  getOptionLabel={getProductLabel}
                />
                <PickerField
                  id="stock-location"
                  label={t("inventory.filters.location")}
                  value={selectedStockLocation}
                  options={locations}
                  onSearch={searchLocations}
                  onChange={(next) => setStockFilters((current) => ({ ...current, locationId: next?.id ?? "" }))}
                  placeholder={t("inventory.lookups.locationPlaceholder")}
                  emptyMessage={t("inventory.lookups.noLocations")}
                  loading={lookupLoading}
                  renderOption={renderLocationOption}
                  getOptionLabel={getLocationLabel}
                />
                <Button type="submit" variant="outline" className="self-end">
                  {t("inventory.filters.apply")}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  className="self-end"
                  onClick={() => {
                    const nextFilters = { productId: "", locationId: "" };
                    setStockFilters(nextFilters);
                    void loadStock(nextFilters);
                  }}
                >
                  {t("inventory.filters.reset")}
                </Button>
              </form>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-0">
              {stockLoading ? (
                <p className="p-4 text-sm text-muted-foreground">{t("inventory.stock.loading")}</p>
              ) : null}
              {stockError ? <p className="p-4 text-sm text-destructive">{stockError}</p> : null}
              {!stockLoading && !stockError && stock.length === 0 ? (
                <p className="p-4 text-sm text-muted-foreground">{t("inventory.stock.empty")}</p>
              ) : null}
              {!stockLoading && stock.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t("inventory.stock.colProduct")}</TableHead>
                      <TableHead>{t("inventory.columns.lot")}</TableHead>
                      <TableHead>{t("inventory.stock.colLocation")}</TableHead>
                      <TableHead className="text-end">{t("inventory.stock.colQty")}</TableHead>
                      <TableHead>{t("inventory.stock.colActions")}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {sortedStock.map((row) => (
                      <TableRow key={`${row.locationId}-${row.productId}-${row.lotNumber ?? "no-lot"}`}>
                        <TableCell>
                          <div className="flex flex-col gap-1">
                            <span className="font-medium">
                              {row.productName ?? row.productSku ?? row.productId}
                            </span>
                            <span className="text-xs text-muted-foreground">
                              {[row.productSku, row.baseUomCode].filter(Boolean).join(" · ")}
                            </span>
                          </div>
                        </TableCell>
                        <TableCell className="text-sm text-muted-foreground">
                          {row.trackLot ? row.lotNumber ?? "—" : "—"}
                        </TableCell>
                        <TableCell>
                          <div className="flex flex-col gap-1">
                            <span className="font-medium">
                              {row.locationLabel ?? row.locationId}
                            </span>
                            <span className="text-xs text-muted-foreground">
                              {row.locationPathLabel ?? row.layoutName ?? ""}
                            </span>
                          </div>
                        </TableCell>
                        <TableCell className="text-end font-medium tabular-nums">
                          {row.qtyStock} {row.baseUomCode ?? ""}
                        </TableCell>
                        <TableCell>
                          <div className={`flex flex-wrap gap-2 ${isRtl ? "justify-end" : ""}`}>
                            {canReceive ? (
                              <Button type="button" variant="outline" size="sm" onClick={() => handleQuickAction("receive", row)}>
                                {t("inventory.actions.receive")}
                              </Button>
                            ) : null}
                            {canTransfer ? (
                              <Button type="button" variant="outline" size="sm" onClick={() => handleQuickAction("transfer", row)}>
                                {t("inventory.actions.transfer")}
                              </Button>
                            ) : null}
                            {canAdjust ? (
                              <Button type="button" variant="outline" size="sm" onClick={() => handleQuickAction("adjust", row)}>
                                {t("inventory.actions.adjust")}
                              </Button>
                            ) : null}
                            {canView ? (
                              <Button type="button" variant="ghost" size="sm" onClick={() => handleQuickAction("movements", row)}>
                                {t("inventory.actions.movements")}
                              </Button>
                            ) : null}
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : null}
            </CardContent>
          </Card>
        </div>
      ) : null}

      {activeTab === "operations" ? (
        <div className="grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(280px,1fr)]">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t("inventory.ops.title")}</CardTitle>
              <CardDescription>{t("inventory.ops.description")}</CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <div className="flex flex-wrap gap-2">
                {canReceive ? (
                  <Button
                    type="button"
                    variant={operation === "receive" ? "default" : "outline"}
                    size="sm"
                    onClick={() => switchOperation("receive")}
                  >
                    <Plus className="h-3 w-3" />
                    {t("inventory.ops.receive")}
                  </Button>
                ) : null}
                {canTransfer ? (
                  <Button
                    type="button"
                    variant={operation === "transfer" ? "default" : "outline"}
                    size="sm"
                    onClick={() => switchOperation("transfer")}
                  >
                    <ArrowLeftRight className="h-3 w-3" />
                    {t("inventory.ops.transfer")}
                  </Button>
                ) : null}
                {canAdjust ? (
                  <Button
                    type="button"
                    variant={operation === "adjust" ? "default" : "outline"}
                    size="sm"
                    onClick={() => switchOperation("adjust")}
                  >
                    <RefreshCw className="h-3 w-3" />
                    {t("inventory.ops.adjust")}
                  </Button>
                ) : null}
              </div>

              {!hasSelectableLocations ? (
                <p className="text-sm text-muted-foreground">{t("inventory.ops.noLocations")}</p>
              ) : null}

              <form className="grid gap-4 md:grid-cols-2" onSubmit={handleOperationSubmit}>
                {operation === "transfer" ? (
                  <>
                    <PickerField
                      id="op-from-location"
                      label={t("inventory.ops.fromLocationId")}
                      value={selectedFromLocation}
                      options={locations}
                      onSearch={searchLocations}
                      onChange={(next) =>
                        setOpForm((current) => ({
                          ...current,
                          fromLocationId: next?.id ?? "",
                          toLocationId: current.toLocationId === next?.id ? "" : current.toLocationId,
                        }))
                      }
                      placeholder={t("inventory.lookups.locationPlaceholder")}
                      emptyMessage={t("inventory.lookups.noLocations")}
                      loading={lookupLoading}
                      disabled={!hasSelectableLocations}
                      renderOption={renderLocationOption}
                      getOptionLabel={getLocationLabel}
                    />
                    <PickerField
                      id="op-to-location"
                      label={t("inventory.ops.toLocationId")}
                      value={selectedToLocation}
                      options={transferDestinationOptions}
                      onSearch={searchLocations}
                      onChange={(next) => setOpForm((current) => ({ ...current, toLocationId: next?.id ?? "" }))}
                      placeholder={t("inventory.lookups.locationPlaceholder")}
                      emptyMessage={t("inventory.lookups.noLocations")}
                      loading={lookupLoading}
                      disabled={!hasSelectableLocations}
                      renderOption={renderLocationOption}
                      getOptionLabel={getLocationLabel}
                    />
                  </>
                ) : (
                  <PickerField
                    id="op-location"
                    label={t("inventory.ops.locationId")}
                    value={selectedOpLocation}
                    options={locations}
                    onSearch={searchLocations}
                    onChange={(next) => setOpForm((current) => ({ ...current, locationId: next?.id ?? "" }))}
                    placeholder={t("inventory.lookups.locationPlaceholder")}
                    emptyMessage={t("inventory.lookups.noLocations")}
                    loading={lookupLoading}
                    disabled={!hasSelectableLocations}
                    renderOption={renderLocationOption}
                    getOptionLabel={getLocationLabel}
                  />
                )}

                <PickerField
                  id="op-product"
                  label={t("inventory.ops.productId")}
                  value={selectedOpProduct}
                  options={products}
                  onSearch={searchProducts}
                  onChange={(next) => setOpForm((current) => ({ ...current, productId: next?.id ?? "" }))}
                  placeholder={t("inventory.lookups.productPlaceholder")}
                  emptyMessage={t("inventory.lookups.noProducts")}
                  loading={lookupLoading}
                  renderOption={renderProductOption}
                  getOptionLabel={getProductLabel}
                />

                {operation === "adjust" ? (
                  <div className="space-y-2">
                    <Label htmlFor="op-adjustment-direction">{t("inventory.ops.adjustmentDirection")}</Label>
                    <Select
                      value={opForm.adjustmentDirection}
                      onValueChange={(value) =>
                        setOpForm((current) => ({ ...current, adjustmentDirection: value as AdjustmentDirection }))
                      }
                    >
                      <SelectTrigger id="op-adjustment-direction" className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="increase">{t("inventory.ops.adjustIncrease")}</SelectItem>
                        <SelectItem value="decrease">{t("inventory.ops.adjustDecrease")}</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                ) : null}

                <div className="space-y-2">
                  <Label htmlFor="op-qty">{t("inventory.ops.qty")}</Label>
                  <Input
                    id="op-qty"
                    type="number"
                    min="0.0001"
                    step="0.0001"
                    value={opForm.qty}
                    onChange={(event) => setOpForm((current) => ({ ...current, qty: event.target.value }))}
                    required
                  />
                </div>

                {selectedOpProduct?.trackLot ? (
                  <div className="space-y-2">
                    <Label htmlFor="op-lot">{t("inventory.ops.lotNumber")}</Label>
                    <Input
                      id="op-lot"
                      value={opForm.lotNumber}
                      onChange={(event) => setOpForm((current) => ({ ...current, lotNumber: event.target.value }))}
                    />
                  </div>
                ) : null}

                {selectedOpProduct?.trackExpiry ? (
                  <div className="space-y-2">
                    <Label htmlFor="op-expiry">{t("inventory.ops.expiryDate")}</Label>
                    <Input
                      id="op-expiry"
                      type="date"
                      value={opForm.expiryDate}
                      onChange={(event) => setOpForm((current) => ({ ...current, expiryDate: event.target.value }))}
                    />
                  </div>
                ) : null}

                {operation === "adjust" ? (
                  <div className="space-y-2">
                    <Label htmlFor="op-reason-code">{t("inventory.adjust.reasonCode")}</Label>
                    <Input
                      id="op-reason-code"
                      maxLength={50}
                      value={opForm.reasonCode}
                      onChange={(event) => setOpForm((current) => ({ ...current, reasonCode: event.target.value }))}
                      placeholder={t("inventory.adjust.reasonCodePlaceholder")}
                    />
                  </div>
                ) : null}

                <div className="space-y-2 md:col-span-2">
                  <Label htmlFor="op-notes">{t("inventory.ops.notes")}</Label>
                  <Textarea
                    id="op-notes"
                    value={opForm.notes}
                    onChange={(event) => setOpForm((current) => ({ ...current, notes: event.target.value }))}
                    required={operation === "adjust"}
                    placeholder={operation === "adjust" ? t("inventory.ops.notesRequired") : t("inventory.ops.notesHint")}
                  />
                </div>

                {opError ? <p className="text-sm text-destructive md:col-span-2">{opError}</p> : null}
                {opSuccess ? <p className="text-sm text-green-700 md:col-span-2">{opSuccess}</p> : null}

                <div className="flex flex-wrap gap-2 md:col-span-2">
                  <Button type="submit" disabled={opSubmitting || !hasSelectableLocations}>
                    {opSubmitting ? t("inventory.ops.submitting") : t("inventory.ops.submit")}
                  </Button>
                  <Button type="button" variant="ghost" onClick={resetOperationForm}>
                    {t("inventory.ops.clear")}
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t("inventory.ops.helperTitle")}</CardTitle>
              <CardDescription>{t("inventory.ops.helperDescription")}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4 text-sm">
              <p className="text-muted-foreground">{t(operationHelpKey[operation])}</p>
              <div className="space-y-2 border p-3">
                <p className="font-medium">{t("inventory.ops.selectionSummary")}</p>
                <p className="text-muted-foreground">
                  {selectedOpProduct ? getProductSecondaryText(selectedOpProduct) : t("inventory.ops.pickProductFirst")}
                </p>
                <p className="text-muted-foreground">
                  {operation === "transfer"
                    ? selectedFromLocation
                      ? getLocationSecondaryText(selectedFromLocation)
                      : t("inventory.ops.pickLocationFirst")
                    : selectedOpLocation
                      ? getLocationSecondaryText(selectedOpLocation)
                      : t("inventory.ops.pickLocationFirst")}
                </p>
              </div>
            </CardContent>
          </Card>
        </div>
      ) : null}

      {activeTab === "movements" ? (
        <div className="flex flex-col gap-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t("inventory.movements.filtersTitle")}</CardTitle>
              <CardDescription>{t("inventory.movements.filtersDescription")}</CardDescription>
            </CardHeader>
            <CardContent>
              <form className="grid gap-4 lg:grid-cols-[1fr_1fr_auto_auto]" onSubmit={handleMovementApplyFilters}>
                <PickerField
                  id="movement-product"
                  label={t("inventory.filters.product")}
                  value={selectedMovementProduct}
                  options={products}
                  onSearch={searchProducts}
                  onChange={(next) => setMovementFilters((current) => ({ ...current, productId: next?.id ?? "" }))}
                  placeholder={t("inventory.lookups.productPlaceholder")}
                  emptyMessage={t("inventory.lookups.noProducts")}
                  loading={lookupLoading}
                  renderOption={renderProductOption}
                  getOptionLabel={getProductLabel}
                />
                <PickerField
                  id="movement-location"
                  label={t("inventory.filters.location")}
                  value={selectedMovementLocation}
                  options={locations}
                  onSearch={searchLocations}
                  onChange={(next) => setMovementFilters((current) => ({ ...current, locationId: next?.id ?? "" }))}
                  placeholder={t("inventory.lookups.locationPlaceholder")}
                  emptyMessage={t("inventory.lookups.noLocations")}
                  loading={lookupLoading}
                  renderOption={renderLocationOption}
                  getOptionLabel={getLocationLabel}
                />
                <Button type="submit" variant="outline" className="self-end">
                  {t("inventory.filters.apply")}
                </Button>
                <Button
                  type="button"
                  variant="ghost"
                  className="self-end"
                  onClick={() => {
                    const nextFilters = { productId: "", locationId: "" };
                    setMovementFilters(nextFilters);
                    setMovPage(0);
                    void loadMovements(nextFilters, 0);
                  }}
                >
                  {t("inventory.filters.reset")}
                </Button>
              </form>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-0">
              {movLoading ? (
                <p className="p-4 text-sm text-muted-foreground">{t("inventory.movements.loading")}</p>
              ) : null}
              {movError ? <p className="p-4 text-sm text-destructive">{movError}</p> : null}
              {!movLoading && !movError && (!movData || movData.content.length === 0) ? (
                <p className="p-4 text-sm text-muted-foreground">{t("inventory.movements.empty")}</p>
              ) : null}
              {movData && movData.content.length > 0 ? (
                <>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t("inventory.movements.colType")}</TableHead>
                        <TableHead>{t("inventory.movements.colProduct")}</TableHead>
                        <TableHead>{t("inventory.movements.colLocation")}</TableHead>
                        <TableHead className="text-end">{t("inventory.movements.colQty")}</TableHead>
                        <TableHead>{t("inventory.columns.reason")}</TableHead>
                        <TableHead>{t("inventory.movements.colNotes")}</TableHead>
                        <TableHead>{t("inventory.movements.colBy")}</TableHead>
                        <TableHead>{t("inventory.movements.colAt")}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {movData.content.map((row) => (
                        <TableRow key={row.id}>
                          <TableCell>
                            <span className={`inline-flex px-2 py-1 text-xs font-medium ${movTypeColor(row.type)}`}>
                              {t(movementTypeKey[row.type])}
                            </span>
                          </TableCell>
                          <TableCell>
                            <div className="flex flex-col gap-1">
                              <span className="font-medium">
                                {row.productName ?? row.productSku ?? row.productId}
                              </span>
                              <span className="text-xs text-muted-foreground">
                                {[row.productSku, row.baseUomCode].filter(Boolean).join(" · ")}
                              </span>
                            </div>
                          </TableCell>
                          <TableCell>
                            <div className="flex flex-col gap-1">
                              <span className="font-medium">{row.locationLabel ?? row.locationId}</span>
                              <span className="text-xs text-muted-foreground">
                                {row.counterpartLocationLabel
                                  ? t("inventory.movements.transferContext")
                                    .replace("{from}", row.type === "TRANSFER_IN" ? row.counterpartLocationLabel : row.locationLabel ?? row.locationId)
                                    .replace("{to}", row.type === "TRANSFER_IN" ? row.locationLabel ?? row.locationId : row.counterpartLocationLabel)
                                  : row.locationPathLabel ?? ""}
                              </span>
                            </div>
                          </TableCell>
                          <TableCell className="text-end font-medium tabular-nums">{row.qty}</TableCell>
                          <TableCell>
                            {row.reasonCode ? (
                              <Badge variant="secondary" className="text-xs">
                                {row.reasonCode}
                              </Badge>
                            ) : (
                              <span className="text-xs text-muted-foreground">—</span>
                            )}
                          </TableCell>
                          <TableCell className="text-xs text-muted-foreground">{row.notes ?? "—"}</TableCell>
                          <TableCell className="text-xs">{row.createdBy}</TableCell>
                          <TableCell className="text-xs text-muted-foreground">
                            {new Date(row.createdAt).toLocaleString()}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>

                  {movData.totalPages > 1 ? (
                    <div className="flex items-center justify-between border-t px-4 py-3">
                      <span className="text-xs text-muted-foreground">
                        {t("inventory.movements.paginationInfo")
                          .replace("{page}", String(movData.page + 1))
                          .replace("{totalPages}", String(movData.totalPages))}
                      </span>
                      <div className="flex gap-2">
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          disabled={movPage === 0}
                          onClick={() => setMovPage((current) => current - 1)}
                        >
                          {t("inventory.movements.paginationPrevious")}
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          disabled={movPage >= movData.totalPages - 1}
                          onClick={() => setMovPage((current) => current + 1)}
                        >
                          {t("inventory.movements.paginationNext")}
                        </Button>
                      </div>
                    </div>
                  ) : null}
                </>
              ) : null}
            </CardContent>
          </Card>
        </div>
      ) : null}
    </div>
  );
}

interface PickerFieldProps<T extends { id: string }> {
  id: string;
  label: string;
  value: T | null;
  options: T[];
  onSearch: (search: string) => void;
  onChange: (value: T | null) => void;
  placeholder: string;
  emptyMessage: string;
  loading?: boolean;
  disabled?: boolean;
  renderOption: (option: T) => ReactNode;
  getOptionLabel: (option: T) => string;
}

function PickerField<T extends { id: string }>({
  id,
  label,
  value,
  options,
  onSearch,
  onChange,
  placeholder,
  emptyMessage,
  loading = false,
  disabled = false,
  renderOption,
  getOptionLabel,
}: PickerFieldProps<T>) {
  const items = useMemo(() => mergeById(options, value ? [value] : []), [options, value]);

  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Combobox
        items={items}
        value={value}
        onInputValueChange={onSearch}
        onValueChange={(nextValue) => onChange(nextValue ?? null)}
        itemToStringLabel={(item) => getOptionLabel(item)}
        itemToStringValue={(item) => getOptionLabel(item)}
        isItemEqualToValue={(item, selectedValue) =>
          String((item as { id?: string }).id) === String((selectedValue as { id?: string }).id)
        }
      >
        <ComboboxInput
          id={id}
          aria-label={label}
          className="w-full"
          disabled={disabled}
          placeholder={loading ? `${placeholder}...` : placeholder}
          showClear
        />
        <ComboboxContent>
          <ComboboxEmpty>{emptyMessage}</ComboboxEmpty>
          <ComboboxList>
            <ComboboxCollection>
              {(option) => (
                <ComboboxItem key={String((option as { id?: string }).id)} value={option}>
                  {renderOption(option)}
                </ComboboxItem>
              )}
            </ComboboxCollection>
          </ComboboxList>
        </ComboboxContent>
      </Combobox>
    </div>
  );
}

function renderProductOption(option: ProductLookupItem) {
  return (
    <div className="flex flex-col gap-1">
      <span className="font-medium">{option.name}</span>
      <span className="text-muted-foreground text-[11px]">
        {[option.sku, option.baseUomCode].filter(Boolean).join(" · ")}
      </span>
    </div>
  );
}

function renderLocationOption(option: LocationLookupItem) {
  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center gap-2">
        <span className="font-medium">{option.label}</span>
        <Badge variant="outline" className="rounded-none px-1 py-0 text-[10px]">
          {option.locationKind}
        </Badge>
      </div>
      <span className="text-muted-foreground text-[11px]">{option.pathLabel}</span>
    </div>
  );
}

function getProductLabel(option: ProductLookupItem) {
  return `${option.name} (${option.sku})`;
}

function getLocationLabel(option: LocationLookupItem) {
  return option.pathLabel;
}

function getProductSecondaryText(option: ProductLookupItem) {
  return [option.name, option.sku, option.baseUomCode].filter(Boolean).join(" · ");
}

function getLocationSecondaryText(option: LocationLookupItem) {
  return [option.label, option.pathLabel].filter(Boolean).join(" · ");
}

function mergeById<T extends { id: string }>(primary: T[], secondary: T[]): T[] {
  const map = new Map<string, T>();
  [...secondary, ...primary].forEach((item) => map.set(item.id, item));
  return Array.from(map.values());
}

function movTypeColor(type: MovementResult["type"]): string {
  switch (type) {
    case "RECEIVE":
      return "bg-green-100 text-green-800";
    case "TRANSFER_IN":
      return "bg-blue-100 text-blue-800";
    case "TRANSFER_OUT":
      return "bg-orange-100 text-orange-800";
    case "ADJUST":
      return "bg-yellow-100 text-yellow-800";
    case "PICK":
      return "bg-red-100 text-red-800";
    default:
      return "bg-muted text-muted-foreground";
  }
}
