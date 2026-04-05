import { useEffect, useMemo, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
    addLine,
    createDraft,
    deleteDraftDispatch,
    extractDispatchErrorMessage,
    getDispatch,
    listDispatches,
    postDispatch,
    removeLine,
    voidDispatch,
} from "@/features/tenant/api/dispatchesApi";
import { getDocumentMovements, getLocationLookups, getProductLookups, getStock } from "@/features/tenant/api/inventoryApi";
import type { MovementResult, ProductLookupItem, LocationLookupItem } from "@/features/tenant/types/inventory";
import type { DispatchDetail, DispatchListItem, DispatchStatus } from "@/features/tenant/types/dispatches";
import { isZoneViolationError } from "@/features/gis/zones/zonesApi";
import type { ZoneViolationError } from "@/features/gis/zones/zonesApi";
import { ZoneViolationBanner } from "@/shared/components/ZoneViolationBanner";
import { Badge } from "@/shared/components/ui/badge";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";
import { Textarea } from "@/shared/components/ui/textarea";
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
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { ScanInput } from "@/shared/components/ScanInput";
import { DocumentQR } from "@/features/tenant/labels/DocumentQR";
import type { ScanResolveResult } from "@/features/tenant/types/scan";

interface DispatchLineFormState {
    productId: string;
    sourceLocationId: string;
    qty: string;
    lotNumber: string;
    notes: string;
}

const DEFAULT_LINE_FORM: DispatchLineFormState = {
    productId: "",
    sourceLocationId: "",
    qty: "",
    lotNumber: "",
    notes: "",
};

export default function DispatchesPage() {
    const { t } = useI18n();
    const { hasPermission } = useAuth();
    const { tenantSlug } = useParams<{ tenantSlug: string }>();
    const [searchParams, setSearchParams] = useSearchParams();
    const slug = normalizeTenantSlug(tenantSlug ?? "");

    const canCreate = hasPermission(TENANT_PERMISSIONS.DISPATCHES_CREATE);
    const canEdit = hasPermission(TENANT_PERMISSIONS.DISPATCHES_EDIT);
    const canPost = hasPermission(TENANT_PERMISSIONS.DISPATCHES_POST);
    const canVoid = hasPermission(TENANT_PERMISSIONS.DISPATCHES_VOID);

    const [statusFilter, setStatusFilter] = useState<DispatchStatus | "ALL">("ALL");
    const [search, setSearch] = useState("");
    const [pendingStatusFilter, setPendingStatusFilter] = useState<DispatchStatus | "ALL">("ALL");
    const [pendingSearch, setPendingSearch] = useState("");

    const [listLoading, setListLoading] = useState(false);
    const [listError, setListError] = useState<string | null>(null);
    const [dispatches, setDispatches] = useState<DispatchListItem[]>([]);

    const [selectedDispatchId, setSelectedDispatchId] = useState<string | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState<string | null>(null);
    const [zoneViolationError, setZoneViolationError] = useState<ZoneViolationError | null>(null);
    const [detail, setDetail] = useState<DispatchDetail | null>(null);

    const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
    const [createReference, setCreateReference] = useState("");
    const [createDestination, setCreateDestination] = useState("");
    const [createNotes, setCreateNotes] = useState("");
    const [createSubmitting, setCreateSubmitting] = useState(false);

    const [lineForm, setLineForm] = useState<DispatchLineFormState>(DEFAULT_LINE_FORM);
    const [isLineFormOpen, setIsLineFormOpen] = useState(false);
    const [lineSubmitting, setLineSubmitting] = useState(false);
    const [lineError, setLineError] = useState<string | null>(null);

    const [products, setProducts] = useState<ProductLookupItem[]>([]);
    const [locations, setLocations] = useState<LocationLookupItem[]>([]);
    const [productSearch, setProductSearch] = useState("");
    const [locationSearch, setLocationSearch] = useState("");

    const [isPostConfirmOpen, setIsPostConfirmOpen] = useState(false);
    const [isVoidConfirmOpen, setIsVoidConfirmOpen] = useState(false);
    const [isDeleteDraftConfirmOpen, setIsDeleteDraftConfirmOpen] = useState(false);

    const [docMovements, setDocMovements] = useState<MovementResult[]>([]);
    const [docMovementsLoading, setDocMovementsLoading] = useState(false);

    const selectedProduct = useMemo(
        () => products.find((product) => product.id === lineForm.productId) ?? null,
        [products, lineForm.productId]
    );

    function handleProductScanned(result: ScanResolveResult) {
        if (!result.productId) return;
        setProducts((prev) => {
            if (prev.some((p) => p.id === result.productId)) return prev;
            return [
                ...prev,
                {
                    id: result.productId!,
                    sku: result.productSku ?? "",
                    name: result.productName ?? result.productSku ?? result.productId!,
                    baseUomCode: "",
                    trackLot: result.trackLot ?? false,
                    trackExpiry: result.trackExpiry ?? false,
                    active: true,
                },
            ];
        });
        setLineForm((prev) => ({ ...prev, productId: result.productId! }));
    }

    function handleLocationScanned(result: ScanResolveResult) {
        if (!result.locationId) return;
        setLocations((prev) => {
            if (prev.some((l) => l.id === result.locationId)) return prev;
            return [
                ...prev,
                {
                    id: result.locationId!,
                    layoutId: "",
                    layoutName: null,
                    label: result.locationPathLabel ?? result.locationId!,
                    pathLabel: result.locationPathLabel ?? result.locationId!,
                    identifier: null,
                    side: null,
                    locationKind: result.locationKindName ?? null,
                    scanCode: result.scanCode ?? null,
                },
            ];
        });
        setLineForm((prev) => ({ ...prev, sourceLocationId: result.locationId! }));
    }

    async function handleStockUnitScanned(result: ScanResolveResult) {
        if (result.type !== "RECEIPT_LINE" && result.type !== "STOCK_ROW") return;

        const normalizeLot = (value: string | null | undefined) => (value ?? "").trim().toLowerCase();

        let resolvedQty = result.lineQty ?? null;
        if (result.productId && result.locationId) {
            try {
                const rows = await getStock(slug, {
                    productId: result.productId,
                    locationId: result.locationId,
                });
                const currentRow = rows.find((row) => normalizeLot(row.lotNumber) === normalizeLot(result.lotNumber));
                if (currentRow?.qtyStock) {
                    const liveQty = Number.parseFloat(currentRow.qtyStock);
                    const alreadyInDraft = (detail?.lines ?? [])
                        .filter(
                            (line) =>
                                line.productId === result.productId &&
                                line.sourceLocationId === result.locationId &&
                                normalizeLot(line.lotNumber) === normalizeLot(result.lotNumber)
                        )
                        .reduce((sum, line) => sum + Number.parseFloat(line.qty || "0"), 0);

                    const remaining = Math.max(liveQty - alreadyInDraft, 0);
                    resolvedQty = String(remaining);
                }
            } catch {
                // fallback to encoded line quantity when stock lookup fails
            }
        }

        if (result.productId) {
            setProducts((prev) => {
                if (prev.some((p) => p.id === result.productId)) return prev;
                return [
                    ...prev,
                    {
                        id: result.productId!,
                        sku: result.productSku ?? "",
                        name: result.productName ?? result.productSku ?? result.productId!,
                        baseUomCode: "",
                        trackLot: result.trackLot ?? false,
                        trackExpiry: result.trackExpiry ?? false,
                        active: true,
                    },
                ];
            });
        }

        if (result.locationId) {
            setLocations((prev) => {
                if (prev.some((l) => l.id === result.locationId)) return prev;
                return [
                    ...prev,
                    {
                        id: result.locationId!,
                        layoutId: "",
                        layoutName: null,
                        label: result.locationPathLabel ?? result.locationId!,
                        pathLabel: result.locationPathLabel ?? result.locationId!,
                        identifier: null,
                        side: null,
                        locationKind: result.locationKindName ?? null,
                        scanCode: result.scanCode ?? null,
                    },
                ];
            });
        }

        setLineForm((prev) => ({
            ...prev,
            productId: result.productId ?? prev.productId,
            sourceLocationId: result.locationId ?? prev.sourceLocationId,
            lotNumber: result.lotNumber ?? prev.lotNumber,
            qty: resolvedQty ?? prev.qty,
        }));
        setLineError(null);
    }

    useEffect(() => {
        void Promise.all([loadDispatches(0), loadLookupData()]);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [slug]);

    useEffect(() => {
        const id = searchParams.get("id");
        if (!id) {
            return;
        }

        if (selectedDispatchId !== id) {
            setSelectedDispatchId(id);
        }
    }, [searchParams, selectedDispatchId]);

    useEffect(() => {
        const next = new URLSearchParams(searchParams);
        if (selectedDispatchId) {
            if (searchParams.get("id") === selectedDispatchId) {
                return;
            }
            next.set("id", selectedDispatchId);
        } else {
            if (!searchParams.has("id")) {
                return;
            }
            next.delete("id");
        }
        setSearchParams(next, { replace: true });
    }, [searchParams, selectedDispatchId, setSearchParams]);

    useEffect(() => {
        if (!selectedDispatchId) {
            setDetail(null);
            setDetailError(null);
            setZoneViolationError(null);
            return;
        }
        void loadDispatchDetail(selectedDispatchId);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedDispatchId]);

    useEffect(() => {
        if (!detail || detail.status !== "POSTED") {
            setDocMovements([]);
            return;
        }
        setDocMovementsLoading(true);
        getDocumentMovements(slug, "dispatches", detail.id)
            .then(setDocMovements)
            .catch(() => setDocMovements([]))
            .finally(() => setDocMovementsLoading(false));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [detail?.id, detail?.status]);

    async function loadDispatches(page: number) {
        setListLoading(true);
        setListError(null);
        try {
            const result = await listDispatches(slug, {
                page,
                size: 20,
                status: statusFilter === "ALL" ? undefined : statusFilter,
                search: search || undefined,
            });

            setDispatches(result.content);
        } catch (error) {
            setListError(extractDispatchErrorMessage(error, t("dispatches.loadFailed")));
        } finally {
            setListLoading(false);
        }
    }

    async function loadDispatchDetail(dispatchId: string) {
        setDetailLoading(true);
        setDetailError(null);
        setLineError(null);
        try {
            const result = await getDispatch(slug, dispatchId);
            setDetail(result);
        } catch (error) {
            setDetailError(extractDispatchErrorMessage(error, t("dispatches.loadFailed")));
        } finally {
            setDetailLoading(false);
        }
    }

    async function loadLookupData() {
        try {
            const [productResult, locationResult] = await Promise.all([
                getProductLookups(slug, { size: 50 }),
                getLocationLookups(slug, { size: 50 }),
            ]);
            setProducts(productResult.content);
            setLocations(locationResult.content);
        } catch {
            // non-blocking lookup load
        }
    }

    async function handleSearchProducts(value: string) {
        setProductSearch(value);
        try {
            const result = await getProductLookups(slug, { search: value || undefined, size: 50 });
            setProducts(result.content);
        } catch {
            // keep existing options
        }
    }

    async function handleSearchLocations(value: string) {
        setLocationSearch(value);
        try {
            const result = await getLocationLookups(slug, { search: value || undefined, size: 50 });
            setLocations(result.content);
        } catch {
            // keep existing options
        }
    }

    async function handleCreateDraft() {
        setCreateSubmitting(true);
        setListError(null);
        try {
            const created = await createDraft(slug, {
                reference: createReference.trim() || null,
                destination: createDestination.trim() || null,
                notes: createNotes.trim() || null,
            });
            setIsCreateDialogOpen(false);
            setCreateReference("");
            setCreateDestination("");
            setCreateNotes("");
            setSelectedDispatchId(created.id);
            await loadDispatches(0);
        } catch (error) {
            setListError(extractDispatchErrorMessage(error, t("dispatches.actionFailed")));
        } finally {
            setCreateSubmitting(false);
        }
    }

    async function handleAddLine() {
        if (!detail) {
            return;
        }

        if (!lineForm.productId || !lineForm.sourceLocationId || !lineForm.qty) {
            setLineError(t("dispatches.validation.requiredLineFields"));
            return;
        }

        setLineSubmitting(true);
        setLineError(null);
        try {
            await addLine(slug, detail.id, {
                productId: lineForm.productId,
                sourceLocationId: lineForm.sourceLocationId,
                qty: lineForm.qty,
                lotNumber: selectedProduct?.trackLot ? lineForm.lotNumber || null : null,
                notes: lineForm.notes || null,
            });
            setLineForm(DEFAULT_LINE_FORM);
            setIsLineFormOpen(false);
            await loadDispatchDetail(detail.id);
            await loadDispatches(0);
        } catch (error) {
            setLineError(extractDispatchErrorMessage(error, t("dispatches.actionFailed")));
        } finally {
            setLineSubmitting(false);
        }
    }

    async function handleDeleteLine(lineId: string) {
        if (!detail) {
            return;
        }
        try {
            await removeLine(slug, detail.id, lineId);
            await loadDispatchDetail(detail.id);
            await loadDispatches(0);
        } catch (error) {
            setLineError(extractDispatchErrorMessage(error, t("dispatches.actionFailed")));
        }
    }

    async function handlePostDispatch() {
        if (!detail) {
            return;
        }
        try {
            const updated = await postDispatch(slug, detail.id);
            setIsPostConfirmOpen(false);
            setZoneViolationError(null);
            setDetail(updated);
            await loadDispatches(0);
        } catch (error) {
            setIsPostConfirmOpen(false);
            if (isZoneViolationError(error)) {
                setZoneViolationError(error.response.data);
            } else {
                setDetailError(extractDispatchErrorMessage(error, t("dispatches.actionFailed")));
            }
        }
    }

    async function handlePostDispatchOverride() {
        if (!detail) return;
        try {
            const updated = await postDispatch(slug, detail.id, true);
            setZoneViolationError(null);
            setDetail(updated);
            await loadDispatches(0);
        } catch (error) {
            setDetailError(extractDispatchErrorMessage(error, t("dispatches.actionFailed")));
        }
    }

    async function handleVoidDispatch() {
        if (!detail) {
            return;
        }
        try {
            const updated = await voidDispatch(slug, detail.id);
            setIsVoidConfirmOpen(false);
            setDetail(updated);
            await loadDispatches(0);
        } catch (error) {
            setDetailError(extractDispatchErrorMessage(error, t("dispatches.actionFailed")));
        }
    }

    async function handleDeleteDraftDispatch() {
        if (!detail || detail.status !== "DRAFT") {
            return;
        }
        try {
            await deleteDraftDispatch(slug, detail.id);
            setIsDeleteDraftConfirmOpen(false);
            setSelectedDispatchId(null);
            await loadDispatches(0);
        } catch (error) {
            setDetailError(extractDispatchErrorMessage(error, t("dispatches.actionFailed")));
        }
    }

    const statusBadgeClass = (status: DispatchStatus) => {
        switch (status) {
            case "DRAFT":
                return "bg-muted text-foreground";
            case "POSTED":
                return "bg-green-100 text-green-800";
            case "VOID":
                return "bg-red-100 text-red-800";
            default:
                return "bg-muted text-foreground";
        }
    };

    const renderLocationCell = (pathLabel: string | null, locationId: string) => (
        <div className="flex flex-col">
            <span>{pathLabel ?? "—"}</span>
            <span className="text-xs text-muted-foreground">{locationId}</span>
        </div>
    );

    if (selectedDispatchId && detailLoading) {
        return (
            <div className="flex flex-col gap-4 p-6">
                <h1 className="text-2xl font-semibold">{t("dispatches.title")}</h1>
                <p className="text-sm text-muted-foreground">{t("dispatches.loading")}</p>
            </div>
        );
    }

    if (selectedDispatchId && detail) {
        const isDraft = detail.status === "DRAFT";
        const isPosted = detail.status === "POSTED";

        return (
            <div className="flex flex-col gap-4 p-6">
                <div className="flex items-center justify-between">
                    <h1 className="text-2xl font-semibold">{t("dispatches.title")}</h1>
                    <Button variant="outline" onClick={() => setSelectedDispatchId(null)}>
                        {t("dispatches.backToList")}
                    </Button>
                </div>

                {detailError ? <p className="text-sm text-destructive">{detailError}</p> : null}
                <ZoneViolationBanner error={zoneViolationError} onOverride={handlePostDispatchOverride} />

                <Card>
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <span>{detail.reference || t("dispatches.referenceFallback")}</span>
                            <Badge className={statusBadgeClass(detail.status)}>{t(`dispatches.status.${detail.status}`)}</Badge>
                        </CardTitle>
                        <CardDescription>
                            {detail.destination || t("dispatches.destinationFallback")} • {new Date(detail.createdAt).toLocaleString()}
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-3 text-sm">
                        <p><strong>{t("dispatches.form.notes")}: </strong>{detail.notes || "—"}</p>
                        <p><strong>{t("dispatches.form.createdBy")}: </strong>{detail.createdBy}</p>
                        {detail.postedAt ? (
                            <p><strong>{t("dispatches.form.postedAt")}: </strong>{new Date(detail.postedAt).toLocaleString()}</p>
                        ) : null}
                        <DocumentQR
                            qrData={detail.qrData}
                            label={detail.reference ?? t("dispatches.referenceFallback")}
                        />
                    </CardContent>
                </Card>

                {isDraft && canEdit ? (
                    <Card>
                        <CardHeader>
                            <div className="flex items-center justify-between gap-2">
                                <CardTitle>{t("dispatches.addLine")}</CardTitle>
                                <Button
                                    variant="outline"
                                    onClick={() => setIsLineFormOpen((current) => !current)}
                                >
                                    {t("dispatches.addLine")}
                                </Button>
                            </div>
                        </CardHeader>
                        {isLineFormOpen ? (
                            <CardContent className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                                <div className="space-y-2 md:col-span-2 xl:col-span-4">
                                    <Label>{t("dispatches.form.stockUnitScan")}</Label>
                                    <ScanInput
                                        tenantSlug={slug}
                                        acceptTypes={["RECEIPT_LINE", "STOCK_ROW"]}
                                        onResolved={handleStockUnitScanned}
                                        placeholder={t("scan.placeholder")}
                                    />
                                </div>

                                <div className="space-y-2 xl:col-span-2">
                                    <Label>{t("dispatches.form.productSearch")}</Label>
                                    <ScanInput
                                        tenantSlug={slug}
                                        acceptTypes={["PRODUCT"]}
                                        onResolved={handleProductScanned}
                                        placeholder={t("scan.placeholder")}
                                    />
                                    <Input
                                        value={productSearch}
                                        onChange={(event) => void handleSearchProducts(event.target.value)}
                                        placeholder={t("inventory.lookups.productPlaceholder")}
                                    />
                                </div>
                                <div className="space-y-2 xl:col-span-2">
                                    <Label>{t("dispatches.form.locationSearch")}</Label>
                                    <ScanInput
                                        tenantSlug={slug}
                                        acceptTypes={["LOCATION"]}
                                        onResolved={handleLocationScanned}
                                        placeholder={t("scan.placeholder")}
                                    />
                                    <Input
                                        value={locationSearch}
                                        onChange={(event) => void handleSearchLocations(event.target.value)}
                                        placeholder={t("inventory.lookups.locationPlaceholder")}
                                    />
                                </div>

                                <div className="space-y-2">
                                    <Label>{t("dispatches.form.product")}</Label>
                                    <select
                                        className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                                        value={lineForm.productId}
                                        onChange={(event) =>
                                            setLineForm((current) => ({ ...current, productId: event.target.value }))
                                        }
                                    >
                                        <option value="">{t("dispatches.form.selectProduct")}</option>
                                        {products.map((product) => (
                                            <option key={product.id} value={product.id}>
                                                {product.sku} · {product.name}
                                            </option>
                                        ))}
                                    </select>
                                </div>

                                <div className="space-y-2">
                                    <Label>{t("dispatches.form.source")}</Label>
                                    <select
                                        className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                                        value={lineForm.sourceLocationId}
                                        onChange={(event) =>
                                            setLineForm((current) => ({ ...current, sourceLocationId: event.target.value }))
                                        }
                                    >
                                        <option value="">{t("dispatches.form.selectSource")}</option>
                                        {locations.map((location) => (
                                            <option key={location.id} value={location.id}>
                                                {location.pathLabel}
                                            </option>
                                        ))}
                                    </select>
                                </div>

                                <div className="space-y-2">
                                    <Label>{t("dispatches.form.qty")}</Label>
                                    <Input
                                        type="number"
                                        min="1"
                                        step="1"
                                        value={lineForm.qty}
                                        onChange={(event) => setLineForm((current) => ({ ...current, qty: event.target.value }))}
                                    />
                                </div>

                                {selectedProduct?.trackLot ? (
                                    <div className="space-y-2">
                                        <Label>{t("dispatches.form.lot")}</Label>
                                        <Input
                                            value={lineForm.lotNumber}
                                            onChange={(event) => setLineForm((current) => ({ ...current, lotNumber: event.target.value }))}
                                        />
                                    </div>
                                ) : null}

                                <div className="space-y-2 md:col-span-2 xl:col-span-4">
                                    <Label>{t("dispatches.form.notes")}</Label>
                                    <Textarea
                                        value={lineForm.notes}
                                        onChange={(event) => setLineForm((current) => ({ ...current, notes: event.target.value }))}
                                    />
                                </div>

                                {lineError ? <p className="text-sm text-destructive md:col-span-2 xl:col-span-4">{lineError}</p> : null}

                                <div className="md:col-span-2 xl:col-span-4">
                                    <Button onClick={handleAddLine} disabled={lineSubmitting}>
                                        {lineSubmitting ? t("dispatches.form.saving") : t("dispatches.form.addLine")}
                                    </Button>
                                </div>
                            </CardContent>
                        ) : null}
                    </Card>
                ) : null}

                <Card>
                    <CardHeader>
                        <CardTitle>{t("dispatches.linesTitle")}</CardTitle>
                    </CardHeader>
                    <CardContent className="p-0">
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>{t("dispatches.columns.product")}</TableHead>
                                    <TableHead>{t("dispatches.columns.source")}</TableHead>
                                    <TableHead className="text-end">{t("dispatches.columns.qty")}</TableHead>
                                    <TableHead>{t("dispatches.columns.lot")}</TableHead>
                                    <TableHead>{t("dispatches.columns.notes")}</TableHead>
                                    {isDraft && canEdit ? <TableHead>{t("dispatches.columns.actions")}</TableHead> : null}
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {detail.lines.map((line) => (
                                    <TableRow key={line.id}>
                                        <TableCell>{line.productName ?? line.productSku ?? line.productId}</TableCell>
                                        <TableCell>{renderLocationCell(line.locationPathLabel, line.sourceLocationId)}</TableCell>
                                        <TableCell className="text-end">{line.qty}</TableCell>
                                        <TableCell>{line.lotNumber ?? "—"}</TableCell>
                                        <TableCell>{line.notes ?? "—"}</TableCell>
                                        {isDraft && canEdit ? (
                                            <TableCell>
                                                <Button variant="ghost" size="sm" onClick={() => void handleDeleteLine(line.id)}>
                                                    {t("dispatches.deleteLine")}
                                                </Button>
                                            </TableCell>
                                        ) : null}
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                        {detail.lines.length === 0 ? (
                            <p className="p-4 text-sm text-muted-foreground">{t("dispatches.emptyLines")}</p>
                        ) : null}
                    </CardContent>
                </Card>

                <div className="flex gap-2">
                    {isDraft && canPost ? (
                        <Button disabled={detail.lines.length === 0} onClick={() => setIsPostConfirmOpen(true)}>
                            {t("dispatches.postAction")}
                        </Button>
                    ) : null}
                    {isDraft && canEdit ? (
                        <Button variant="destructive" onClick={() => setIsDeleteDraftConfirmOpen(true)}>
                            {t("dispatches.deleteDraftAction")}
                        </Button>
                    ) : null}
                    {isPosted && canVoid ? (
                        <Button variant="destructive" onClick={() => setIsVoidConfirmOpen(true)}>
                            {t("dispatches.voidAction")}
                        </Button>
                    ) : null}
                </div>

                {isPosted ? (
                    <Card>
                        <CardHeader>
                            <CardTitle className="text-base">{t("inventory.movements.documentMovements")}</CardTitle>
                        </CardHeader>
                        <CardContent className="p-0">
                            {docMovementsLoading ? (
                                <p className="p-4 text-sm text-muted-foreground">{t("dispatches.loading")}</p>
                            ) : docMovements.length === 0 ? (
                                <p className="p-4 text-sm text-muted-foreground">{t("inventory.movements.documentMovementsEmpty")}</p>
                            ) : (
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>{t("inventory.movements.colProduct")}</TableHead>
                                            <TableHead>{t("inventory.movements.colLocation")}</TableHead>
                                            <TableHead className="text-end">{t("inventory.movements.colQty")}</TableHead>
                                            <TableHead>{t("inventory.columns.lot")}</TableHead>
                                            <TableHead>{t("inventory.movements.colAt")}</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {docMovements.map((mov) => (
                                            <TableRow key={mov.id}>
                                                <TableCell>
                                                    <div className="flex flex-col gap-1">
                                                        <span className="font-medium">{mov.productName ?? mov.productSku ?? mov.productId}</span>
                                                        <span className="text-xs text-muted-foreground">{[mov.productSku, mov.baseUomCode].filter(Boolean).join(" · ")}</span>
                                                    </div>
                                                </TableCell>
                                                <TableCell>
                                                    <div className="flex flex-col gap-1">
                                                        <span className="font-medium">{mov.locationLabel ?? mov.locationId}</span>
                                                        <span className="text-xs text-muted-foreground">{mov.locationPathLabel ?? ""}</span>
                                                    </div>
                                                </TableCell>
                                                <TableCell className="text-end font-medium tabular-nums">{mov.qty}</TableCell>
                                                <TableCell className="text-xs text-muted-foreground">{mov.lotNumber ?? "—"}</TableCell>
                                                <TableCell className="text-xs text-muted-foreground">{new Date(mov.createdAt).toLocaleString()}</TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                        </CardContent>
                    </Card>
                ) : null}

                <AlertDialog open={isPostConfirmOpen} onOpenChange={setIsPostConfirmOpen}>
                    <AlertDialogContent>
                        <AlertDialogHeader>
                            <AlertDialogTitle>{t("dispatches.post.confirm")}</AlertDialogTitle>
                            <AlertDialogDescription>{t("dispatches.post.confirmDescription")}</AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                            <AlertDialogCancel>{t("dispatches.cancel")}</AlertDialogCancel>
                            <AlertDialogAction onClick={handlePostDispatch}>{t("dispatches.postAction")}</AlertDialogAction>
                        </AlertDialogFooter>
                    </AlertDialogContent>
                </AlertDialog>

                <AlertDialog open={isVoidConfirmOpen} onOpenChange={setIsVoidConfirmOpen}>
                    <AlertDialogContent>
                        <AlertDialogHeader>
                            <AlertDialogTitle>{t("dispatches.void.confirm")}</AlertDialogTitle>
                            <AlertDialogDescription>{t("dispatches.void.confirmDescription")}</AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                            <AlertDialogCancel>{t("dispatches.cancel")}</AlertDialogCancel>
                            <AlertDialogAction onClick={handleVoidDispatch}>{t("dispatches.voidAction")}</AlertDialogAction>
                        </AlertDialogFooter>
                    </AlertDialogContent>
                </AlertDialog>

                <AlertDialog open={isDeleteDraftConfirmOpen} onOpenChange={setIsDeleteDraftConfirmOpen}>
                    <AlertDialogContent>
                        <AlertDialogHeader>
                            <AlertDialogTitle>{t("dispatches.deleteDraft.confirm")}</AlertDialogTitle>
                            <AlertDialogDescription>{t("dispatches.deleteDraft.confirmDescription")}</AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                            <AlertDialogCancel>{t("dispatches.cancel")}</AlertDialogCancel>
                            <AlertDialogAction onClick={handleDeleteDraftDispatch}>{t("dispatches.deleteDraftAction")}</AlertDialogAction>
                        </AlertDialogFooter>
                    </AlertDialogContent>
                </AlertDialog>
            </div>
        );
    }

    return (
        <div className="flex flex-col gap-4 p-6">
            <div className="flex items-center justify-between">
                <h1 className="text-2xl font-semibold">{t("dispatches.title")}</h1>
                {canCreate ? (
                    <Button onClick={() => setIsCreateDialogOpen(true)}>{t("dispatches.newDispatch")}</Button>
                ) : null}
            </div>

            {listError ? <p className="text-sm text-destructive">{listError}</p> : null}

            <Card>
                <CardHeader>
                    <CardTitle>{t("dispatches.filtersTitle")}</CardTitle>
                    <CardDescription>{t("dispatches.filtersDescription")}</CardDescription>
                </CardHeader>
                <CardContent className="flex flex-wrap gap-3">
                    <select
                        className="rounded-md border border-input bg-background px-3 py-2 text-sm"
                        value={pendingStatusFilter}
                        onChange={(event) => setPendingStatusFilter(event.target.value as DispatchStatus | "ALL")}
                    >
                        <option value="ALL">{t("dispatches.status.all")}</option>
                        <option value="DRAFT">{t("dispatches.status.DRAFT")}</option>
                        <option value="POSTED">{t("dispatches.status.POSTED")}</option>
                        <option value="VOID">{t("dispatches.status.VOID")}</option>
                    </select>
                    <Input
                        className="max-w-xs"
                        placeholder={t("dispatches.searchPlaceholder")}
                        value={pendingSearch}
                        onChange={(event) => setPendingSearch(event.target.value)}
                    />
                    <Button
                        variant="outline"
                        onClick={() => {
                            setStatusFilter(pendingStatusFilter);
                            setSearch(pendingSearch);
                            void loadDispatches(0);
                        }}
                    >
                        {t("dispatches.applyFilters")}
                    </Button>
                </CardContent>
            </Card>

            <Card>
                <CardContent className="p-0">
                    {listLoading ? <p className="p-4 text-sm text-muted-foreground">{t("dispatches.loading")}</p> : null}
                    {!listLoading && dispatches.length === 0 ? (
                        <p className="p-4 text-sm text-muted-foreground">{t("dispatches.empty")}</p>
                    ) : null}

                    {dispatches.length > 0 ? (
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>{t("dispatches.columns.reference")}</TableHead>
                                    <TableHead>{t("dispatches.columns.destination")}</TableHead>
                                    <TableHead>{t("dispatches.columns.status")}</TableHead>
                                    <TableHead>{t("dispatches.columns.createdAt")}</TableHead>
                                    <TableHead>{t("dispatches.columns.actions")}</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {dispatches.map((dispatch) => (
                                    <TableRow
                                        key={dispatch.id}
                                        className="cursor-pointer"
                                        onClick={() => setSelectedDispatchId(dispatch.id)}
                                    >
                                        <TableCell>
                                            <div className="flex flex-col">
                                                <span>{dispatch.reference || t("dispatches.referenceFallback")}</span>
                                                <span className="text-xs text-muted-foreground">{dispatch.id}</span>
                                            </div>
                                        </TableCell>
                                        <TableCell>{dispatch.destination || t("dispatches.destinationFallback")}</TableCell>
                                        <TableCell>
                                            <Badge className={statusBadgeClass(dispatch.status)}>{t(`dispatches.status.${dispatch.status}`)}</Badge>
                                        </TableCell>
                                        <TableCell>{new Date(dispatch.createdAt).toLocaleString()}</TableCell>
                                        <TableCell>
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={(event) => {
                                                    event.stopPropagation();
                                                    setSelectedDispatchId(dispatch.id);
                                                }}
                                            >
                                                {t("dispatches.open")}
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    ) : null}
                </CardContent>
            </Card>

            <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>{t("dispatches.newDispatch")}</DialogTitle>
                        <DialogDescription>{t("dispatches.newDispatchDescription")}</DialogDescription>
                    </DialogHeader>
                    <div className="space-y-3">
                        <div className="space-y-2">
                            <Label>{t("dispatches.form.reference")}</Label>
                            <Input value={createReference} onChange={(event) => setCreateReference(event.target.value)} />
                        </div>
                        <div className="space-y-2">
                            <Label>{t("dispatches.form.destination")}</Label>
                            <Input value={createDestination} onChange={(event) => setCreateDestination(event.target.value)} />
                        </div>
                        <div className="space-y-2">
                            <Label>{t("dispatches.form.notes")}</Label>
                            <Textarea value={createNotes} onChange={(event) => setCreateNotes(event.target.value)} />
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setIsCreateDialogOpen(false)}>{t("dispatches.cancel")}</Button>
                        <Button onClick={() => void handleCreateDraft()} disabled={createSubmitting}>
                            {createSubmitting ? t("dispatches.form.saving") : t("dispatches.createDraft")}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}
