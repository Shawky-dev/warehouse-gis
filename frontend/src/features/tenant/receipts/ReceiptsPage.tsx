import { useEffect, useMemo, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
    addLine,
    createDraft,
    deleteDraftReceipt,
    extractReceiptErrorMessage,
    getReceipt,
    listReceipts,
    postReceipt,
    removeLine,
    voidReceipt,
} from "@/features/tenant/api/receiptsApi";
import { listSuppliers } from "@/features/tenant/api/f0Api";
import { getDocumentMovements, getLocationLookups, getProductLookups } from "@/features/tenant/api/inventoryApi";
import type { MovementResult, ProductLookupItem, LocationLookupItem } from "@/features/tenant/types/inventory";
import type { SupplierResult } from "@/features/tenant/types/f0";
import type { ReceiptDetail, ReceiptLine, ReceiptListItem, ReceiptStatus } from "@/features/tenant/types/receipts";
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
import { Printer } from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import { ScanInput } from "@/shared/components/ScanInput";
import { DocumentQR } from "@/features/tenant/labels/DocumentQR";
import { LocationLabelSheet } from "@/features/tenant/labels/LocationLabelSheet";
import type { ScanResolveResult } from "@/features/tenant/types/scan";

interface ReceiptLineFormState {
    productId: string;
    destinationLocationId: string;
    qty: string;
    lotNumber: string;
    expiryDate: string;
    notes: string;
}

const DEFAULT_LINE_FORM: ReceiptLineFormState = {
    productId: "",
    destinationLocationId: "",
    qty: "",
    lotNumber: "",
    expiryDate: "",
    notes: "",
};

export default function ReceiptsPage() {
    const { t } = useI18n();
    const { hasPermission } = useAuth();
    const { tenantSlug } = useParams<{ tenantSlug: string }>();
    const [searchParams, setSearchParams] = useSearchParams();
    const slug = normalizeTenantSlug(tenantSlug ?? "");

    const canCreate = hasPermission(TENANT_PERMISSIONS.RECEIPTS_CREATE);
    const canEdit = hasPermission(TENANT_PERMISSIONS.RECEIPTS_EDIT);
    const canPost = hasPermission(TENANT_PERMISSIONS.RECEIPTS_POST);
    const canVoid = hasPermission(TENANT_PERMISSIONS.RECEIPTS_VOID);

    const [statusFilter, setStatusFilter] = useState<ReceiptStatus | "ALL">("ALL");
    const [search, setSearch] = useState("");
    const [pendingStatusFilter, setPendingStatusFilter] = useState<ReceiptStatus | "ALL">("ALL");
    const [pendingSearch, setPendingSearch] = useState("");

    const [listLoading, setListLoading] = useState(false);
    const [listError, setListError] = useState<string | null>(null);
    const [receipts, setReceipts] = useState<ReceiptListItem[]>([]);

    const [selectedReceiptId, setSelectedReceiptId] = useState<string | null>(null);
    const [invalidReceiptId, setInvalidReceiptId] = useState<string | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState<string | null>(null);
    const [zoneViolationError, setZoneViolationError] = useState<ZoneViolationError | null>(null);
    const [detail, setDetail] = useState<ReceiptDetail | null>(null);

    const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
    const [createReference, setCreateReference] = useState("");
    const [createSupplierId, setCreateSupplierId] = useState("");
    const [createNotes, setCreateNotes] = useState("");
    const [createSubmitting, setCreateSubmitting] = useState(false);

    const [lineForm, setLineForm] = useState<ReceiptLineFormState>(DEFAULT_LINE_FORM);
    const [isLineFormOpen, setIsLineFormOpen] = useState(false);
    const [lineSubmitting, setLineSubmitting] = useState(false);
    const [lineError, setLineError] = useState<string | null>(null);

    const [products, setProducts] = useState<ProductLookupItem[]>([]);
    const [locations, setLocations] = useState<LocationLookupItem[]>([]);
    const [suppliers, setSuppliers] = useState<SupplierResult[]>([]);
    const [productSearch, setProductSearch] = useState("");
    const [locationSearch, setLocationSearch] = useState("");

    const [isPostConfirmOpen, setIsPostConfirmOpen] = useState(false);
    const [isVoidConfirmOpen, setIsVoidConfirmOpen] = useState(false);
    const [isDeleteDraftConfirmOpen, setIsDeleteDraftConfirmOpen] = useState(false);

    const [docMovements, setDocMovements] = useState<MovementResult[]>([]);
    const [docMovementsLoading, setDocMovementsLoading] = useState(false);
    const [selectedLabelLine, setSelectedLabelLine] = useState<ReceiptLine | null>(null);
    const [isBulkLabelPrintOpen, setIsBulkLabelPrintOpen] = useState(false);

    const selectedProduct = useMemo(
        () => products.find((product) => product.id === lineForm.productId) ?? null,
        [products, lineForm.productId]
    );

    function clearSelectedReceipt() {
        setSelectedReceiptId(null);
        setDetail(null);
        const next = new URLSearchParams(searchParams);
        if (next.has("id")) {
            next.delete("id");
            setSearchParams(next, { replace: true });
        }
    }

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
        setLineForm((prev) => ({ ...prev, destinationLocationId: result.locationId! }));
    }

    useEffect(() => {
        void Promise.all([loadReceipts(0), loadLookupData(), loadSuppliers()]);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [slug]);

    useEffect(() => {
        const id = searchParams.get("id");
        if (!id) {
            return;
        }

        if (invalidReceiptId && id === invalidReceiptId) {
            const next = new URLSearchParams(searchParams);
            next.delete("id");
            setSearchParams(next, { replace: true });
            return;
        }

        if (selectedReceiptId !== id) {
            setSelectedReceiptId(id);
        }
    }, [invalidReceiptId, searchParams, selectedReceiptId, setSearchParams]);

    useEffect(() => {
        const next = new URLSearchParams(searchParams);
        if (selectedReceiptId) {
            if (searchParams.get("id") === selectedReceiptId) {
                return;
            }
            next.set("id", selectedReceiptId);
        } else {
            if (!searchParams.has("id")) {
                return;
            }
            next.delete("id");
        }
        setSearchParams(next, { replace: true });
    }, [searchParams, selectedReceiptId, setSearchParams]);

    useEffect(() => {
        if (!selectedReceiptId) {
            setDetail(null);
            setDetailError(null);
            setZoneViolationError(null);
            return;
        }
        void loadReceiptDetail(selectedReceiptId);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedReceiptId]);

    useEffect(() => {
        if (!detail || detail.status !== "POSTED") {
            setDocMovements([]);
            return;
        }
        setDocMovementsLoading(true);
        getDocumentMovements(slug, "receipts", detail.id)
            .then(setDocMovements)
            .catch(() => setDocMovements([]))
            .finally(() => setDocMovementsLoading(false));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [detail?.id, detail?.status]);

    async function loadReceipts(page: number) {
        setListLoading(true);
        setListError(null);
        try {
            const result = await listReceipts(slug, {
                page,
                size: 20,
                status: statusFilter === "ALL" ? undefined : statusFilter,
                search: search || undefined,
            });

            setReceipts(result.content);
        } catch (error) {
            setListError(extractReceiptErrorMessage(error, t("receipts.loadFailed")));
        } finally {
            setListLoading(false);
        }
    }

    async function loadReceiptDetail(receiptId: string) {
        setDetailLoading(true);
        setDetailError(null);
        setLineError(null);
        try {
            const result = await getReceipt(slug, receiptId);
            if (invalidReceiptId === receiptId) {
                setInvalidReceiptId(null);
            }
            setDetail(result);
        } catch (error) {
            const message = extractReceiptErrorMessage(error, t("receipts.loadFailed"));
            setDetailError(message);
            if (message.toLowerCase().includes("not found")) {
                setInvalidReceiptId(receiptId);
                clearSelectedReceipt();
            }
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

    async function loadSuppliers() {
        try {
            const result = await listSuppliers(slug, { size: 100, active: true });
            setSuppliers(result.content);
        } catch {
            // non-blocking supplier load
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
                supplierId: createSupplierId || null,
                notes: createNotes.trim() || null,
            });
            setIsCreateDialogOpen(false);
            setCreateReference("");
            setCreateSupplierId("");
            setCreateNotes("");
            setInvalidReceiptId(null);
            setSelectedReceiptId(created.id);
            await loadReceipts(0);
        } catch (error) {
            setListError(extractReceiptErrorMessage(error, t("receipts.actionFailed")));
        } finally {
            setCreateSubmitting(false);
        }
    }

    async function handleAddLine() {
        if (!detail) {
            return;
        }

        if (!lineForm.productId || !lineForm.destinationLocationId || !lineForm.qty) {
            setLineError(t("receipts.validation.requiredLineFields"));
            return;
        }

        setLineSubmitting(true);
        setLineError(null);
        try {
            await addLine(slug, detail.id, {
                productId: lineForm.productId,
                destinationLocationId: lineForm.destinationLocationId,
                qty: lineForm.qty,
                lotNumber: selectedProduct?.trackLot ? lineForm.lotNumber || null : null,
                expiryDate: selectedProduct?.trackExpiry ? lineForm.expiryDate || null : null,
                notes: lineForm.notes || null,
            });
            setLineForm(DEFAULT_LINE_FORM);
            setIsLineFormOpen(false);
            await loadReceiptDetail(detail.id);
            await loadReceipts(0);
        } catch (error) {
            setLineError(extractReceiptErrorMessage(error, t("receipts.actionFailed")));
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
            await loadReceiptDetail(detail.id);
            await loadReceipts(0);
        } catch (error) {
            setLineError(extractReceiptErrorMessage(error, t("receipts.actionFailed")));
        }
    }

    async function handlePostReceipt() {
        if (!detail) {
            return;
        }
        try {
            const updated = await postReceipt(slug, detail.id);
            setIsPostConfirmOpen(false);
            setZoneViolationError(null);
            setDetail(updated);
            await loadReceipts(0);
        } catch (error) {
            setIsPostConfirmOpen(false);
            if (isZoneViolationError(error)) {
                setZoneViolationError(error.response.data);
            } else {
                setDetailError(extractReceiptErrorMessage(error, t("receipts.actionFailed")));
            }
        }
    }

    async function handlePostReceiptOverride() {
        if (!detail) return;
        try {
            const updated = await postReceipt(slug, detail.id, true);
            setZoneViolationError(null);
            setDetail(updated);
            await loadReceipts(0);
        } catch (error) {
            setDetailError(extractReceiptErrorMessage(error, t("receipts.actionFailed")));
        }
    }

    async function handleVoidReceipt() {
        if (!detail) {
            return;
        }
        try {
            const updated = await voidReceipt(slug, detail.id);
            setIsVoidConfirmOpen(false);
            setDetail(updated);
            await loadReceipts(0);
        } catch (error) {
            setDetailError(extractReceiptErrorMessage(error, t("receipts.actionFailed")));
        }
    }

    async function handleDeleteDraftReceipt() {
        if (!detail || detail.status !== "DRAFT") {
            return;
        }
        try {
            await deleteDraftReceipt(slug, detail.id);
            setIsDeleteDraftConfirmOpen(false);
            clearSelectedReceipt();
            await loadReceipts(0);
        } catch (error) {
            setDetailError(extractReceiptErrorMessage(error, t("receipts.actionFailed")));
        }
    }

    const statusBadgeClass = (status: ReceiptStatus) => {
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

    if (selectedReceiptId && detailLoading) {
        return (
            <div className="flex flex-col gap-4 p-6">
                <h1 className="text-2xl font-semibold">{t("receipts.title")}</h1>
                <p className="text-sm text-muted-foreground">{t("receipts.loading")}</p>
            </div>
        );
    }

    if (selectedReceiptId && detail) {
        const isDraft = detail.status === "DRAFT";
        const isPosted = detail.status === "POSTED";

        return (
            <div className="flex flex-col gap-4 p-6">
                <div className="flex items-center justify-between">
                    <h1 className="text-2xl font-semibold">{t("receipts.title")}</h1>
                    <Button variant="outline" onClick={clearSelectedReceipt}>
                        {t("receipts.backToList")}
                    </Button>
                </div>

                {detailError ? <p className="text-sm text-destructive">{detailError}</p> : null}
                <ZoneViolationBanner error={zoneViolationError} onOverride={handlePostReceiptOverride} />

                <Card>
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <span>{detail.reference || t("receipts.referenceFallback")}</span>
                            <Badge className={statusBadgeClass(detail.status)}>{t(`receipts.status.${detail.status}`)}</Badge>
                        </CardTitle>
                        <CardDescription>
                            {detail.supplierName || t("receipts.supplierFallback")} • {new Date(detail.createdAt).toLocaleString()}
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-3 text-sm">
                        <p><strong>{t("receipts.form.notes")}: </strong>{detail.notes || "—"}</p>
                        <p><strong>{t("receipts.form.createdBy")}: </strong>{detail.createdBy}</p>
                        {detail.postedAt ? (
                            <p><strong>{t("receipts.form.postedAt")}: </strong>{new Date(detail.postedAt).toLocaleString()}</p>
                        ) : null}
                        <DocumentQR
                            qrData={detail.qrData}
                            label={detail.reference ?? t("receipts.referenceFallback")}
                        />
                    </CardContent>
                </Card>

                {isDraft && canEdit ? (
                    <Card>
                        <CardHeader>
                            <div className="flex items-center justify-between gap-2">
                                <CardTitle>{t("receipts.addLine")}</CardTitle>
                                <Button
                                    variant="outline"
                                    onClick={() => setIsLineFormOpen((current) => !current)}
                                >
                                    {t("receipts.addLine")}
                                </Button>
                            </div>
                        </CardHeader>
                        {isLineFormOpen ? (
                            <CardContent className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
                                <div className="space-y-2 xl:col-span-2">
                                    <Label>{t("receipts.form.productSearch")}</Label>
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
                                    <Label>{t("receipts.form.locationSearch")}</Label>
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
                                    <Label>{t("receipts.form.product")}</Label>
                                    <select
                                        className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                                        value={lineForm.productId}
                                        onChange={(event) =>
                                            setLineForm((current) => ({ ...current, productId: event.target.value }))
                                        }
                                    >
                                        <option value="">{t("receipts.form.selectProduct")}</option>
                                        {products.map((product) => (
                                            <option key={product.id} value={product.id}>
                                                {product.sku} · {product.name}
                                            </option>
                                        ))}
                                    </select>
                                </div>

                                <div className="space-y-2">
                                    <Label>{t("receipts.form.destination")}</Label>
                                    <select
                                        className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                                        value={lineForm.destinationLocationId}
                                        onChange={(event) =>
                                            setLineForm((current) => ({ ...current, destinationLocationId: event.target.value }))
                                        }
                                    >
                                        <option value="">{t("receipts.form.selectDestination")}</option>
                                        {locations.map((location) => (
                                            <option key={location.id} value={location.id}>
                                                {location.pathLabel}
                                            </option>
                                        ))}
                                    </select>
                                </div>

                                <div className="space-y-2">
                                    <Label>{t("receipts.form.qty")}</Label>
                                    <Input
                                        type="number"
                                        min="0.0001"
                                        step="0.0001"
                                        value={lineForm.qty}
                                        onChange={(event) => setLineForm((current) => ({ ...current, qty: event.target.value }))}
                                    />
                                </div>

                                {selectedProduct?.trackLot ? (
                                    <div className="space-y-2">
                                        <Label>{t("receipts.form.lot")}</Label>
                                        <Input
                                            value={lineForm.lotNumber}
                                            onChange={(event) => setLineForm((current) => ({ ...current, lotNumber: event.target.value }))}
                                        />
                                    </div>
                                ) : null}

                                {selectedProduct?.trackExpiry ? (
                                    <div className="space-y-2">
                                        <Label>{t("receipts.form.expiry")}</Label>
                                        <Input
                                            type="date"
                                            value={lineForm.expiryDate}
                                            onChange={(event) => setLineForm((current) => ({ ...current, expiryDate: event.target.value }))}
                                        />
                                    </div>
                                ) : null}

                                <div className="space-y-2 md:col-span-2 xl:col-span-4">
                                    <Label>{t("receipts.form.notes")}</Label>
                                    <Textarea
                                        value={lineForm.notes}
                                        onChange={(event) => setLineForm((current) => ({ ...current, notes: event.target.value }))}
                                    />
                                </div>

                                {lineError ? <p className="text-sm text-destructive md:col-span-2 xl:col-span-4">{lineError}</p> : null}

                                <div className="md:col-span-2 xl:col-span-4">
                                    <Button onClick={handleAddLine} disabled={lineSubmitting}>
                                        {lineSubmitting ? t("receipts.form.saving") : t("receipts.form.addLine")}
                                    </Button>
                                </div>
                            </CardContent>
                        ) : null}
                    </Card>
                ) : null}

                <Card>
                    <CardHeader>
                        <CardTitle>{t("receipts.linesTitle")}</CardTitle>
                    </CardHeader>
                    <CardContent className="p-0">
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>{t("receipts.columns.product")}</TableHead>
                                    <TableHead>{t("receipts.columns.destination")}</TableHead>
                                    <TableHead className="text-end">{t("receipts.columns.qty")}</TableHead>
                                    <TableHead>{t("receipts.columns.lot")}</TableHead>
                                    <TableHead>{t("receipts.columns.expiry")}</TableHead>
                                    <TableHead>{t("receipts.columns.notes")}</TableHead>
                                    {isDraft && canEdit ? <TableHead>{t("receipts.columns.actions")}</TableHead> : null}
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {detail.lines.map((line) => (
                                    <TableRow key={line.id}>
                                        <TableCell>{line.productName ?? line.productSku ?? line.productId}</TableCell>
                                        <TableCell>{renderLocationCell(line.locationPathLabel, line.destinationLocationId)}</TableCell>
                                        <TableCell className="text-end">{line.qty}</TableCell>
                                        <TableCell>{line.lotNumber ?? "—"}</TableCell>
                                        <TableCell>{line.expiryDate ?? "—"}</TableCell>
                                        <TableCell>{line.notes ?? "—"}</TableCell>
                                        {isDraft && canEdit ? (
                                            <TableCell>
                                                <Button variant="ghost" size="sm" onClick={() => void handleDeleteLine(line.id)}>
                                                    {t("receipts.deleteLine")}
                                                </Button>
                                            </TableCell>
                                        ) : null}
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                        {detail.lines.length === 0 ? (
                            <p className="p-4 text-sm text-muted-foreground">{t("receipts.emptyLines")}</p>
                        ) : null}
                    </CardContent>
                </Card>

                <div className="flex gap-2">
                    {isDraft && canPost ? (
                        <Button disabled={detail.lines.length === 0} onClick={() => setIsPostConfirmOpen(true)}>
                            {t("receipts.postAction")}
                        </Button>
                    ) : null}
                    {isDraft && canEdit ? (
                        <Button variant="destructive" onClick={() => setIsDeleteDraftConfirmOpen(true)}>
                            {t("receipts.deleteDraftAction")}
                        </Button>
                    ) : null}
                    {isPosted && canVoid ? (
                        <Button variant="destructive" onClick={() => setIsVoidConfirmOpen(true)}>
                            {t("receipts.voidAction")}
                        </Button>
                    ) : null}
                </div>

                {isPosted ? (
                    <Card>
                        <CardHeader>
                            <div className="flex items-center justify-between">
                                <CardTitle className="text-base">{t("receipts.stockUnitLabels.title")}</CardTitle>
                                <Button variant="outline" size="sm" onClick={() => setIsBulkLabelPrintOpen(true)}>
                                    <Printer className="h-4 w-4" />
                                    {t("receipts.stockUnitLabels.printAll")}
                                </Button>
                            </div>
                        </CardHeader>
                        <CardContent>
                            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
                                {detail.lines.map((line) => (
                                    <div key={line.id} className="flex flex-col items-center gap-2 rounded-lg border p-3">
                                        <QRCodeSVG value={line.stockUnitQrData} size={80} />
                                        <p className="text-center text-xs font-medium leading-tight">
                                            {line.productName ?? line.productSku ?? line.productId}
                                        </p>
                                        {line.lotNumber ? (
                                            <span className="rounded-full bg-muted px-2 py-0.5 text-[10px] font-medium">
                                                {line.lotNumber}
                                            </span>
                                        ) : null}
                                        <p className="font-mono text-[10px] tabular-nums text-muted-foreground">×{line.qty}</p>
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            className="w-full text-xs"
                                            onClick={() => setSelectedLabelLine(line)}
                                        >
                                            <Printer className="h-3 w-3" />
                                            {t("receipts.stockUnitLabels.printLabel")}
                                        </Button>
                                    </div>
                                ))}
                            </div>
                        </CardContent>
                    </Card>
                ) : null}

                {isPosted ? (
                    <Card>
                        <CardHeader>
                            <CardTitle className="text-base">{t("inventory.movements.documentMovements")}</CardTitle>
                        </CardHeader>
                        <CardContent className="p-0">
                            {docMovementsLoading ? (
                                <p className="p-4 text-sm text-muted-foreground">{t("receipts.loading")}</p>
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

                <Dialog open={selectedLabelLine !== null} onOpenChange={(open) => { if (!open) setSelectedLabelLine(null); }}>
                    <DialogContent>
                        <DialogHeader>
                            <DialogTitle>{t("receipts.stockUnitLabels.printLabel")}</DialogTitle>
                        </DialogHeader>
                        {selectedLabelLine ? (
                            <DocumentQR
                                qrData={selectedLabelLine.stockUnitQrData}
                                label={`${selectedLabelLine.productName ?? selectedLabelLine.productSku ?? selectedLabelLine.productId}${selectedLabelLine.lotNumber ? " / Lot " + selectedLabelLine.lotNumber : ""}`}
                                size={160}
                            />
                        ) : null}
                    </DialogContent>
                </Dialog>

                <Dialog open={isBulkLabelPrintOpen} onOpenChange={setIsBulkLabelPrintOpen}>
                    <DialogContent className="max-w-2xl">
                        <DialogHeader>
                            <DialogTitle>{t("receipts.stockUnitLabels.title")}</DialogTitle>
                        </DialogHeader>
                        <LocationLabelSheet
                            items={detail.lines.map((line) => ({
                                scanCode: line.stockUnitQrData,
                                displayLabel: `${line.productName ?? line.productSku ?? line.productId} ×${line.qty}`,
                                locationKindName: line.lotNumber ? `Lot: ${line.lotNumber}` : null,
                            }))}
                            printButtonLabel={t("receipts.stockUnitLabels.printAll")}
                        />
                    </DialogContent>
                </Dialog>

                <AlertDialog open={isPostConfirmOpen} onOpenChange={setIsPostConfirmOpen}>
                    <AlertDialogContent>
                        <AlertDialogHeader>
                            <AlertDialogTitle>{t("receipts.post.confirm")}</AlertDialogTitle>
                            <AlertDialogDescription>{t("receipts.post.confirmDescription")}</AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                            <AlertDialogCancel>{t("receipts.cancel")}</AlertDialogCancel>
                            <AlertDialogAction onClick={handlePostReceipt}>{t("receipts.postAction")}</AlertDialogAction>
                        </AlertDialogFooter>
                    </AlertDialogContent>
                </AlertDialog>

                <AlertDialog open={isVoidConfirmOpen} onOpenChange={setIsVoidConfirmOpen}>
                    <AlertDialogContent>
                        <AlertDialogHeader>
                            <AlertDialogTitle>{t("receipts.void.confirm")}</AlertDialogTitle>
                            <AlertDialogDescription>{t("receipts.void.confirmDescription")}</AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                            <AlertDialogCancel>{t("receipts.cancel")}</AlertDialogCancel>
                            <AlertDialogAction onClick={handleVoidReceipt}>{t("receipts.voidAction")}</AlertDialogAction>
                        </AlertDialogFooter>
                    </AlertDialogContent>
                </AlertDialog>

                <AlertDialog open={isDeleteDraftConfirmOpen} onOpenChange={setIsDeleteDraftConfirmOpen}>
                    <AlertDialogContent>
                        <AlertDialogHeader>
                            <AlertDialogTitle>{t("receipts.deleteDraft.confirm")}</AlertDialogTitle>
                            <AlertDialogDescription>{t("receipts.deleteDraft.confirmDescription")}</AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                            <AlertDialogCancel>{t("receipts.cancel")}</AlertDialogCancel>
                            <AlertDialogAction onClick={handleDeleteDraftReceipt}>{t("receipts.deleteDraftAction")}</AlertDialogAction>
                        </AlertDialogFooter>
                    </AlertDialogContent>
                </AlertDialog>
            </div>
        );
    }

    return (
        <div className="flex flex-col gap-4 p-6">
            <div className="flex items-center justify-between">
                <h1 className="text-2xl font-semibold">{t("receipts.title")}</h1>
                {canCreate ? (
                    <Button onClick={() => setIsCreateDialogOpen(true)}>{t("receipts.newReceipt")}</Button>
                ) : null}
            </div>

            {listError ? <p className="text-sm text-destructive">{listError}</p> : null}

            <Card>
                <CardHeader>
                    <CardTitle>{t("receipts.filtersTitle")}</CardTitle>
                    <CardDescription>{t("receipts.filtersDescription")}</CardDescription>
                </CardHeader>
                <CardContent className="flex flex-wrap gap-3">
                    <select
                        className="rounded-md border border-input bg-background px-3 py-2 text-sm"
                        value={pendingStatusFilter}
                        onChange={(event) => setPendingStatusFilter(event.target.value as ReceiptStatus | "ALL")}
                    >
                        <option value="ALL">{t("receipts.status.all")}</option>
                        <option value="DRAFT">{t("receipts.status.DRAFT")}</option>
                        <option value="POSTED">{t("receipts.status.POSTED")}</option>
                        <option value="VOID">{t("receipts.status.VOID")}</option>
                    </select>
                    <Input
                        className="max-w-xs"
                        placeholder={t("receipts.searchPlaceholder")}
                        value={pendingSearch}
                        onChange={(event) => setPendingSearch(event.target.value)}
                    />
                    <Button
                        variant="outline"
                        onClick={() => {
                            setStatusFilter(pendingStatusFilter);
                            setSearch(pendingSearch);
                            void loadReceipts(0);
                        }}
                    >
                        {t("receipts.applyFilters")}
                    </Button>
                </CardContent>
            </Card>

            <Card>
                <CardContent className="p-0">
                    {listLoading ? <p className="p-4 text-sm text-muted-foreground">{t("receipts.loading")}</p> : null}
                    {!listLoading && receipts.length === 0 ? (
                        <p className="p-4 text-sm text-muted-foreground">{t("receipts.empty")}</p>
                    ) : null}

                    {receipts.length > 0 ? (
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>{t("receipts.columns.reference")}</TableHead>
                                    <TableHead>{t("receipts.columns.supplier")}</TableHead>
                                    <TableHead>{t("receipts.columns.status")}</TableHead>
                                    <TableHead>{t("receipts.columns.createdAt")}</TableHead>
                                    <TableHead>{t("receipts.columns.actions")}</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {receipts.map((receipt) => (
                                    <TableRow
                                        key={receipt.id}
                                        className="cursor-pointer"
                                        onClick={() => setSelectedReceiptId(receipt.id)}
                                    >
                                        <TableCell>
                                            <div className="flex flex-col">
                                                <span>{receipt.reference || t("receipts.referenceFallback")}</span>
                                                <span className="text-xs text-muted-foreground">{receipt.id}</span>
                                            </div>
                                        </TableCell>
                                        <TableCell>{receipt.supplierName || t("receipts.supplierFallback")}</TableCell>
                                        <TableCell>
                                            <Badge className={statusBadgeClass(receipt.status)}>{t(`receipts.status.${receipt.status}`)}</Badge>
                                        </TableCell>
                                        <TableCell>{new Date(receipt.createdAt).toLocaleString()}</TableCell>
                                        <TableCell>
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={(event) => {
                                                    event.stopPropagation();
                                                    setSelectedReceiptId(receipt.id);
                                                }}
                                            >
                                                {t("receipts.open")}
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
                        <DialogTitle>{t("receipts.newReceipt")}</DialogTitle>
                        <DialogDescription>{t("receipts.newReceiptDescription")}</DialogDescription>
                    </DialogHeader>
                    <div className="space-y-3">
                        <div className="space-y-2">
                            <Label>{t("receipts.form.reference")}</Label>
                            <Input value={createReference} onChange={(event) => setCreateReference(event.target.value)} />
                        </div>
                        <div className="space-y-2">
                            <Label>{t("receipts.form.supplier")}</Label>
                            <select
                                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                                value={createSupplierId}
                                onChange={(event) => setCreateSupplierId(event.target.value)}
                            >
                                <option value="">{t("receipts.form.selectSupplier")}</option>
                                {suppliers.map((supplier) => (
                                    <option key={supplier.id} value={supplier.id}>
                                        {supplier.code} · {supplier.name}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div className="space-y-2">
                            <Label>{t("receipts.form.notes")}</Label>
                            <Textarea value={createNotes} onChange={(event) => setCreateNotes(event.target.value)} />
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setIsCreateDialogOpen(false)}>{t("receipts.cancel")}</Button>
                        <Button onClick={() => void handleCreateDraft()} disabled={createSubmitting}>
                            {createSubmitting ? t("receipts.form.saving") : t("receipts.createDraft")}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}
