import { useEffect, useState } from "react";
import { X, Package, MapPin, Loader2, Inbox } from "lucide-react";
import { api } from "@/lib/api";
import { Badge } from "@/shared/components/ui/badge";
import { Separator } from "@/shared/components/ui/separator";
import { useI18n } from "@/i18n";
import { getTemplateStroke } from "../floorplans/templateColors";

// ── Types ─────────────────────────────────────────────────────────────────────

interface StockRow {
    locationId: string;
    productId: string;
    lotNumber: string | null;
    qtyStock: number;
    locationLabel: string | null;
    locationPathLabel: string | null;
    productSku: string | null;
    productName: string | null;
    baseUomCode: string | null;
    trackLot: boolean | null;
    trackExpiry: boolean | null;
}

interface Props {
    slug: string;
    layoutBlockId: string;
    templateName: string;
    label: string;
    positionPath: string;
    onClose: () => void;
}

// ── Component ─────────────────────────────────────────────────────────────────
// Parent mounts this with key={layoutBlockId} so state resets on block change.

export function LocationInspectPanel({
    slug,
    layoutBlockId,
    templateName,
    label,
    positionPath,
    onClose,
}: Props) {
    const { t } = useI18n();
    // null  → still fetching
    // []    → fetch done, empty
    // [...] → fetch done, has stock
    const [stock, setStock] = useState<StockRow[] | null>(null);

    useEffect(() => {
        if (!layoutBlockId) return;
        let cancelled = false;

        void (async () => {
            try {
                const res = await api.get<StockRow[]>(
                    `/${slug}/inventory/stock/by-location/${layoutBlockId}`,
                    { headers: { "X-TENANT-ID": slug } }
                );
                if (!cancelled) setStock(res.data);
            } catch {
                if (!cancelled) setStock([]);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [slug, layoutBlockId]);

    const strokeColor = getTemplateStroke(templateName);

    // Use path from first stock row when available, fall back to positionPath / label
    const displayPath = stock?.[0]?.locationPathLabel ?? positionPath ?? label;

    const uniqueProductIds = new Set(stock?.map((r) => r.productId) ?? []);
    const totalQty = stock?.reduce((sum, r) => sum + Number(r.qtyStock ?? 0), 0) ?? 0;

    return (
        <div className="flex h-full flex-col overflow-hidden rounded-md border bg-card">
            {/* ── Header ─────────────────────────────────────────────────────────── */}
            <div className="flex items-center justify-between border-b px-3 py-2.5">
                <div className="flex min-w-0 items-center gap-2">
                    <span
                        className="h-2.5 w-2.5 shrink-0 rounded-full"
                        style={{ backgroundColor: strokeColor }}
                    />
                    <span className="truncate text-sm font-semibold leading-tight">{label}</span>
                </div>
                <button
                    type="button"
                    onClick={onClose}
                    className="ml-2 shrink-0 rounded p-0.5 text-muted-foreground transition-colors hover:bg-accent/50"
                >
                    <X className="h-3.5 w-3.5" />
                </button>
            </div>

            {/* ── Location meta ───────────────────────────────────────────────────── */}
            <div className="border-b px-3 py-2.5">
                <div className="flex items-start gap-1.5 text-[11px] text-muted-foreground">
                    <MapPin className="mt-0.5 h-3 w-3 shrink-0" />
                    <span className="leading-snug">{displayPath}</span>
                </div>
                <div className="mt-1.5">
                    <Badge variant="secondary" className="text-[10px]">
                        {templateName}
                    </Badge>
                </div>
            </div>

            {/* ── Stock section ───────────────────────────────────────────────────── */}
            <p className="px-3 pt-2.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                {t("gis.viewer.inspect.stockTitle")}
            </p>

            {stock === null && (
                <div className="flex flex-1 items-center justify-center gap-2 text-xs text-muted-foreground">
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    {t("gis.viewer.inspect.loading")}
                </div>
            )}

            {stock !== null && stock.length === 0 && (
                <div className="flex flex-1 flex-col items-center justify-center gap-2 px-4 pb-6 text-center">
                    <Inbox className="h-8 w-8 text-muted-foreground/30" />
                    <p className="text-xs text-muted-foreground">{t("gis.viewer.inspect.empty")}</p>
                </div>
            )}

            {stock !== null && stock.length > 0 && (
                <>
                    <ul className="flex-1 divide-y overflow-y-auto px-1 pb-1">
                        {stock.map((row, i) => (
                            <li key={i} className="flex items-start justify-between gap-2 px-2 py-2.5">
                                <div className="flex min-w-0 items-start gap-2">
                                    <Package className="mt-0.5 h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                                    <div className="min-w-0">
                                        <p className="truncate text-sm font-medium leading-tight">
                                            {row.productName ?? row.productSku ?? "—"}
                                        </p>
                                        {row.productSku && (
                                            <p className="mt-0.5 text-[11px] text-muted-foreground">{row.productSku}</p>
                                        )}
                                        {row.trackLot && row.lotNumber && (
                                            <p className="mt-0.5 text-[11px] text-muted-foreground">
                                                {t("gis.viewer.inspect.lot")}: {row.lotNumber}
                                            </p>
                                        )}
                                    </div>
                                </div>
                                <div className="shrink-0 text-right">
                                    <p className="text-sm font-semibold tabular-nums">
                                        {Number(row.qtyStock).toLocaleString()}
                                    </p>
                                    {row.baseUomCode && (
                                        <p className="text-[11px] text-muted-foreground">{row.baseUomCode}</p>
                                    )}
                                </div>
                            </li>
                        ))}
                    </ul>

                    {/* ── Footer totals ─────────────────────────────────────────────── */}
                    <Separator />
                    <div className="bg-muted/20 px-3 py-2">
                        <div className="flex items-center justify-between">
                            <span className="text-xs text-muted-foreground">
                                {uniqueProductIds.size}{" "}
                                {uniqueProductIds.size === 1
                                    ? t("gis.viewer.inspect.product")
                                    : t("gis.viewer.inspect.products")}
                            </span>
                            <span className="text-xs font-semibold tabular-nums">
                                {totalQty.toLocaleString()} {t("gis.viewer.inspect.units")}
                            </span>
                        </div>
                    </div>
                </>
            )}
        </div>
    );
}
