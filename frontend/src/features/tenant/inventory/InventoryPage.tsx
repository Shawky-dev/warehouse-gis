import { useCallback, useEffect, useState, type FormEvent } from "react";
import { useParams } from "react-router-dom";
import { PackageOpen, ArrowLeftRight, SlidersHorizontal, List } from "lucide-react";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
  getAllOnHand,
  getMovementsByLocation,
  getMovementsByProduct,
  receiveStock,
  transferStock,
  adjustStock,
  extractInventoryErrorMessage,
} from "@/features/tenant/api/inventoryApi";
import type { MovementPageResult, OnHandEntry } from "@/features/tenant/types/inventory";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Separator } from "@/components/ui/separator";

type Tab = "onhand" | "operations" | "movements";
type Operation = "receive" | "transfer" | "adjust";

export default function InventoryPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");

  const canView = hasPermission(TENANT_PERMISSIONS.INVENTORY_VIEW);
  const canReceive = hasPermission(TENANT_PERMISSIONS.INVENTORY_RECEIVE);
  const canTransfer = hasPermission(TENANT_PERMISSIONS.INVENTORY_TRANSFER);
  const canAdjust = hasPermission(TENANT_PERMISSIONS.INVENTORY_ADJUST);
  const canManageOperations = canReceive || canTransfer || canAdjust;

  const availableOperations: Operation[] = [
    ...(canReceive ? ["receive" as const] : []),
    ...(canTransfer ? ["transfer" as const] : []),
    ...(canAdjust ? ["adjust" as const] : []),
  ];

  const availableTabs: Tab[] = [
    ...(canView ? ["onhand" as const] : []),
    ...(canManageOperations ? ["operations" as const] : []),
    ...(canView ? ["movements" as const] : []),
  ];

  const [activeTab, setActiveTab] = useState<Tab>(availableTabs[0] ?? "onhand");

  // ── On-hand state ──────────────────────────────────────────────────────────
  const [onHand, setOnHand] = useState<OnHandEntry[]>([]);
  const [onHandLoading, setOnHandLoading] = useState(false);
  const [onHandError, setOnHandError] = useState<string | null>(null);

  const loadOnHand = useCallback(async () => {
    if (!canView) return;
    setOnHandLoading(true);
    setOnHandError(null);
    try {
      setOnHand(await getAllOnHand(slug));
    } catch (err) {
      setOnHandError(extractInventoryErrorMessage(err, t("inventory.onHand.loadFailed")));
    } finally {
      setOnHandLoading(false);
    }
  }, [slug, canView, t]);

  useEffect(() => {
    if (activeTab === "onhand") void loadOnHand();
  }, [activeTab, loadOnHand]);

  useEffect(() => {
    const nextTab = availableTabs[0];
    if (nextTab && !availableTabs.includes(activeTab)) {
      setActiveTab(nextTab);
    }
  }, [activeTab, availableTabs]);

  // ── Operations state ───────────────────────────────────────────────────────
  const [operation, setOperation] = useState<Operation>(availableOperations[0] ?? "receive");
  const [opLocationId, setOpLocationId] = useState("");
  const [opFromLocationId, setOpFromLocationId] = useState("");
  const [opToLocationId, setOpToLocationId] = useState("");
  const [opProductId, setOpProductId] = useState("");
  const [opQty, setOpQty] = useState("");
  const [opLotNumber, setOpLotNumber] = useState("");
  const [opExpiryDate, setOpExpiryDate] = useState("");
  const [opNotes, setOpNotes] = useState("");
  const [opSubmitting, setOpSubmitting] = useState(false);
  const [opSuccess, setOpSuccess] = useState<string | null>(null);
  const [opError, setOpError] = useState<string | null>(null);

  useEffect(() => {
    const nextOperation = availableOperations[0];
    if (nextOperation && !availableOperations.includes(operation)) {
      setOperation(nextOperation);
    }
  }, [availableOperations, operation]);

  function resetOpForm() {
    setOpLocationId("");
    setOpFromLocationId("");
    setOpToLocationId("");
    setOpProductId("");
    setOpQty("");
    setOpLotNumber("");
    setOpExpiryDate("");
    setOpNotes("");
    setOpError(null);
  }

  async function handleOpSubmit(e: FormEvent) {
    e.preventDefault();
    if (!availableOperations.includes(operation)) {
      setOpError("Operation is not permitted.");
      return;
    }
    setOpError(null);
    setOpSuccess(null);
    setOpSubmitting(true);
    try {
      if (operation === "receive") {
        await receiveStock(slug, {
          locationId: opLocationId.trim(),
          productId: opProductId.trim(),
          qty: opQty.trim(),
          lotNumber: opLotNumber.trim() || null,
          expiryDate: opExpiryDate.trim() || null,
          notes: opNotes.trim() || null,
        });
        setOpSuccess(t("inventory.ops.successReceive"));
      } else if (operation === "transfer") {
        await transferStock(slug, {
          fromLocationId: opFromLocationId.trim(),
          toLocationId: opToLocationId.trim(),
          productId: opProductId.trim(),
          qty: opQty.trim(),
          lotNumber: opLotNumber.trim() || null,
          notes: opNotes.trim() || null,
        });
        setOpSuccess(t("inventory.ops.successTransfer"));
      } else {
        await adjustStock(slug, {
          locationId: opLocationId.trim(),
          productId: opProductId.trim(),
          qty: opQty.trim(),
          notes: opNotes.trim(),
        });
        setOpSuccess(t("inventory.ops.successAdjust"));
      }
      resetOpForm();
      void loadOnHand();
    } catch (err) {
      setOpError(extractInventoryErrorMessage(err, "Operation failed."));
    } finally {
      setOpSubmitting(false);
    }
  }

  // ── Movements state ────────────────────────────────────────────────────────
  const [movFilterLocationId, setMovFilterLocationId] = useState("");
  const [movFilterProductId, setMovFilterProductId] = useState("");
  const [movPage, setMovPage] = useState(0);
  const [movData, setMovData] = useState<MovementPageResult | null>(null);
  const [movLoading, setMovLoading] = useState(false);
  const [movError, setMovError] = useState<string | null>(null);

  const loadMovements = useCallback(async (locationId: string, productId: string, page: number) => {
    if (!canView) return;
    setMovLoading(true);
    setMovError(null);
    try {
      if (locationId.trim()) {
        setMovData(await getMovementsByLocation(slug, locationId.trim(), page));
      } else if (productId.trim()) {
        setMovData(await getMovementsByProduct(slug, productId.trim(), page));
      } else {
        setMovData(null);
      }
    } catch (err) {
      setMovError(extractInventoryErrorMessage(err, t("inventory.movements.loadFailed")));
    } finally {
      setMovLoading(false);
    }
  }, [slug, canView, t]);

  function handleMovSearch(e: FormEvent) {
    e.preventDefault();
    setMovPage(0);
    void loadMovements(movFilterLocationId, movFilterProductId, 0);
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  const tabs: { id: Tab; label: string; icon: typeof PackageOpen }[] = [
    ...(canView ? [{ id: "onhand" as const, label: t("inventory.tabOnHand"), icon: PackageOpen }] : []),
    ...(canManageOperations
      ? [{ id: "operations" as const, label: t("inventory.tabOperations"), icon: SlidersHorizontal }]
      : []),
    ...(canView ? [{ id: "movements" as const, label: t("inventory.tabMovements"), icon: List }] : []),
  ];

  return (
    <div className="flex flex-col gap-6 p-6">
      <h1 className="text-2xl font-semibold">{t("inventory.pageTitle")}</h1>

      {/* Tab bar */}
      <div className="flex gap-1 border-b">
        {tabs.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => setActiveTab(id)}
            className={`flex items-center gap-2 px-4 py-2 text-sm font-medium border-b-2 transition-colors ${
              activeTab === id
                ? "border-primary text-primary"
                : "border-transparent text-muted-foreground hover:text-foreground"
            }`}
          >
            <Icon className="h-4 w-4" />
            {label}
          </button>
        ))}
      </div>

      {/* On Hand tab */}
      {activeTab === "onhand" && (
        <Card>
          <CardContent className="p-0">
            {onHandLoading && (
              <p className="p-4 text-sm text-muted-foreground">{t("inventory.onHand.loading")}</p>
            )}
            {onHandError && (
              <p className="p-4 text-sm text-destructive">{onHandError}</p>
            )}
            {!onHandLoading && !onHandError && onHand.length === 0 && (
              <p className="p-4 text-sm text-muted-foreground">{t("inventory.onHand.empty")}</p>
            )}
            {!onHandLoading && onHand.length > 0 && (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b bg-muted/50">
                      <th className="px-4 py-2 text-start font-medium">{t("inventory.onHand.colLocation")}</th>
                      <th className="px-4 py-2 text-start font-medium">{t("inventory.onHand.colProduct")}</th>
                      <th className="px-4 py-2 text-end font-medium">{t("inventory.onHand.colQty")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {onHand.map((row) => (
                      <tr key={`${row.locationId}-${row.productId}`} className="border-b last:border-0">
                        <td className="px-4 py-2 font-mono text-xs">{row.locationId}</td>
                        <td className="px-4 py-2 font-mono text-xs">{row.productId}</td>
                        <td className="px-4 py-2 text-end tabular-nums">{row.qtyOnHand}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Operations tab */}
      {activeTab === "operations" && (
        <Card className="max-w-lg">
          <CardHeader>
            <CardTitle className="text-base">
              {t("inventory.ops.selectOperation")}
            </CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            {/* Operation selector */}
            <div className="flex gap-2">
              {canReceive && (
                <Button
                  variant={operation === "receive" ? "default" : "outline"}
                  size="sm"
                  onClick={() => { setOperation("receive"); resetOpForm(); }}
                >
                  {t("inventory.ops.receive")}
                </Button>
              )}
              {canTransfer && (
                <Button
                  variant={operation === "transfer" ? "default" : "outline"}
                  size="sm"
                  onClick={() => { setOperation("transfer"); resetOpForm(); }}
                >
                  <ArrowLeftRight className="me-1 h-3 w-3" />
                  {t("inventory.ops.transfer")}
                </Button>
              )}
              {canAdjust && (
                <Button
                  variant={operation === "adjust" ? "default" : "outline"}
                  size="sm"
                  onClick={() => { setOperation("adjust"); resetOpForm(); }}
                >
                  {t("inventory.ops.adjust")}
                </Button>
              )}
            </div>

            <Separator />

            <form onSubmit={handleOpSubmit} className="flex flex-col gap-3">
              {/* Location fields */}
              {operation !== "transfer" && (
                <div className="flex flex-col gap-1">
                  <Label htmlFor="op-location">{t("inventory.ops.locationId")}</Label>
                  <Input
                    id="op-location"
                    value={opLocationId}
                    onChange={(e) => setOpLocationId(e.target.value)}
                    placeholder="UUID"
                    required
                  />
                </div>
              )}
              {operation === "transfer" && (
                <>
                  <div className="flex flex-col gap-1">
                    <Label htmlFor="op-from">{t("inventory.ops.fromLocationId")}</Label>
                    <Input
                      id="op-from"
                      value={opFromLocationId}
                      onChange={(e) => setOpFromLocationId(e.target.value)}
                      placeholder="UUID"
                      required
                    />
                  </div>
                  <div className="flex flex-col gap-1">
                    <Label htmlFor="op-to">{t("inventory.ops.toLocationId")}</Label>
                    <Input
                      id="op-to"
                      value={opToLocationId}
                      onChange={(e) => setOpToLocationId(e.target.value)}
                      placeholder="UUID"
                      required
                    />
                  </div>
                </>
              )}

              {/* Product */}
              <div className="flex flex-col gap-1">
                <Label htmlFor="op-product">{t("inventory.ops.productId")}</Label>
                <Input
                  id="op-product"
                  value={opProductId}
                  onChange={(e) => setOpProductId(e.target.value)}
                  placeholder="UUID"
                  required
                />
              </div>

              {/* Qty */}
              <div className="flex flex-col gap-1">
                <Label htmlFor="op-qty">{t("inventory.ops.qty")}</Label>
                <Input
                  id="op-qty"
                  type="number"
                  step="0.0001"
                  value={opQty}
                  onChange={(e) => setOpQty(e.target.value)}
                  required
                />
              </div>

              {/* Lot / Expiry — receive only */}
              {operation === "receive" && (
                <>
                  <div className="flex flex-col gap-1">
                    <Label htmlFor="op-lot">{t("inventory.ops.lotNumber")}</Label>
                    <Input
                      id="op-lot"
                      value={opLotNumber}
                      onChange={(e) => setOpLotNumber(e.target.value)}
                    />
                  </div>
                  <div className="flex flex-col gap-1">
                    <Label htmlFor="op-expiry">{t("inventory.ops.expiryDate")}</Label>
                    <Input
                      id="op-expiry"
                      type="date"
                      value={opExpiryDate}
                      onChange={(e) => setOpExpiryDate(e.target.value)}
                    />
                  </div>
                </>
              )}

              {/* Notes */}
              <div className="flex flex-col gap-1">
                <Label htmlFor="op-notes">{t("inventory.ops.notes")}</Label>
                <Input
                  id="op-notes"
                  value={opNotes}
                  onChange={(e) => setOpNotes(e.target.value)}
                  required={operation === "adjust"}
                  placeholder={operation === "adjust" ? t("inventory.ops.notesRequired") : ""}
                />
              </div>

              {opError && <p className="text-sm text-destructive">{opError}</p>}
              {opSuccess && <p className="text-sm text-green-600">{opSuccess}</p>}

              <Button type="submit" disabled={opSubmitting}>
                {opSubmitting ? t("inventory.ops.submitting") : t("inventory.ops.submit")}
              </Button>
            </form>
          </CardContent>
        </Card>
      )}

      {/* Movements tab */}
      {activeTab === "movements" && (
        <div className="flex flex-col gap-4">
          {/* Filter form */}
          <form onSubmit={handleMovSearch} className="flex items-end gap-2">
            <div className="flex flex-col gap-1">
              <Label htmlFor="mov-location">{t("inventory.movements.filterByLocation")}</Label>
              <Input
                id="mov-location"
                value={movFilterLocationId}
                onChange={(e) => { setMovFilterLocationId(e.target.value); setMovFilterProductId(""); }}
                placeholder="UUID"
                className="w-64"
              />
            </div>
            <div className="flex flex-col gap-1">
              <Label htmlFor="mov-product">{t("inventory.movements.filterByProduct")}</Label>
              <Input
                id="mov-product"
                value={movFilterProductId}
                onChange={(e) => { setMovFilterProductId(e.target.value); setMovFilterLocationId(""); }}
                placeholder="UUID"
                className="w-64"
              />
            </div>
            <Button type="submit" variant="outline">Search</Button>
          </form>

          {movLoading && (
            <p className="text-sm text-muted-foreground">{t("inventory.movements.loading")}</p>
          )}
          {movError && <p className="text-sm text-destructive">{movError}</p>}
          {!movLoading && !movError && movData === null && (
            <p className="text-sm text-muted-foreground">{t("inventory.movements.empty")}</p>
          )}
          {!movLoading && movData && (
            <Card>
              <CardContent className="p-0">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b bg-muted/50">
                        <th className="px-4 py-2 text-start font-medium">{t("inventory.movements.colType")}</th>
                        <th className="px-4 py-2 text-end font-medium">{t("inventory.movements.colQty")}</th>
                        <th className="px-4 py-2 text-start font-medium">{t("inventory.movements.colLocation")}</th>
                        <th className="px-4 py-2 text-start font-medium">{t("inventory.movements.colProduct")}</th>
                        <th className="px-4 py-2 text-start font-medium">{t("inventory.movements.colNotes")}</th>
                        <th className="px-4 py-2 text-start font-medium">{t("inventory.movements.colBy")}</th>
                        <th className="px-4 py-2 text-start font-medium">{t("inventory.movements.colAt")}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {movData.content.map((row) => (
                        <tr key={row.id} className="border-b last:border-0">
                          <td className="px-4 py-2">
                            <span className={`rounded px-1.5 py-0.5 text-xs font-mono font-medium ${movTypeColor(row.type)}`}>
                              {row.type}
                            </span>
                          </td>
                          <td className="px-4 py-2 text-end tabular-nums">{row.qty}</td>
                          <td className="px-4 py-2 font-mono text-xs truncate max-w-[120px]">{row.locationId}</td>
                          <td className="px-4 py-2 font-mono text-xs truncate max-w-[120px]">{row.productId}</td>
                          <td className="px-4 py-2 text-xs text-muted-foreground">{row.notes ?? "—"}</td>
                          <td className="px-4 py-2 text-xs">{row.createdBy}</td>
                          <td className="px-4 py-2 text-xs text-muted-foreground">
                            {new Date(row.createdAt).toLocaleString()}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Pagination */}
                {movData.totalPages > 1 && (
                  <div className="flex items-center justify-between border-t px-4 py-2">
                    <span className="text-xs text-muted-foreground">
                      {t("inventory.movements.paginationInfo")
                        .replace("{page}", String(movData.page + 1))
                        .replace("{totalPages}", String(movData.totalPages))}
                    </span>
                    <div className="flex gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={movPage === 0}
                        onClick={() => {
                          const next = movPage - 1;
                          setMovPage(next);
                          void loadMovements(movFilterLocationId, movFilterProductId, next);
                        }}
                      >
                        {t("inventory.movements.paginationPrevious")}
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={movPage >= movData.totalPages - 1}
                        onClick={() => {
                          const next = movPage + 1;
                          setMovPage(next);
                          void loadMovements(movFilterLocationId, movFilterProductId, next);
                        }}
                      >
                        {t("inventory.movements.paginationNext")}
                      </Button>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          )}
        </div>
      )}
    </div>
  );
}

function movTypeColor(type: string): string {
  switch (type) {
    case "RECEIVE": return "bg-green-100 text-green-800";
    case "TRANSFER_IN": return "bg-blue-100 text-blue-800";
    case "TRANSFER_OUT": return "bg-orange-100 text-orange-800";
    case "ADJUST": return "bg-yellow-100 text-yellow-800";
    case "PICK": return "bg-red-100 text-red-800";
    default: return "bg-muted text-muted-foreground";
  }
}
