import { useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import {
    extractCountingErrorMessage,
    getCountSession,
    listCountSessions,
    openCountSession,
    postCountSession,
    updateCountLine,
    voidCountSession,
} from "@/features/tenant/api/countingApi";
import { getLocationLookups } from "@/features/tenant/api/inventoryApi";
import type { LocationLookupItem } from "@/features/tenant/types/inventory";
import type { CountLine, CountSessionDetail, CountSessionListItem, CountStatus } from "@/features/tenant/types/counting";
import { Badge } from "@/shared/components/ui/badge";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
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

export default function CountSessionsPage() {
    const { t } = useI18n();
    const { hasPermission } = useAuth();
    const { tenantSlug } = useParams<{ tenantSlug: string }>();
    const slug = normalizeTenantSlug(tenantSlug ?? "");

    const canCreate = hasPermission(TENANT_PERMISSIONS.COUNTING_CREATE);
    const canPost = hasPermission(TENANT_PERMISSIONS.COUNTING_POST);
    const canVoid = hasPermission(TENANT_PERMISSIONS.COUNTING_VOID);

    const [statusFilter, setStatusFilter] = useState<CountStatus | "ALL">("ALL");
    const [search, setSearch] = useState("");
    const [pendingStatusFilter, setPendingStatusFilter] = useState<CountStatus | "ALL">("ALL");
    const [pendingSearch, setPendingSearch] = useState("");

    const [listLoading, setListLoading] = useState(false);
    const [listError, setListError] = useState<string | null>(null);
    const [sessions, setSessions] = useState<CountSessionListItem[]>([]);

    const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState<string | null>(null);
    const [detail, setDetail] = useState<CountSessionDetail | null>(null);

    const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
    const [createName, setCreateName] = useState("");
    const [locationSearch, setLocationSearch] = useState("");
    const [selectedLocationIds, setSelectedLocationIds] = useState<string[]>([]);
    const [createSubmitting, setCreateSubmitting] = useState(false);

    const [locations, setLocations] = useState<LocationLookupItem[]>([]);

    const [lineDrafts, setLineDrafts] = useState<Record<string, string>>({});
    const [lineTouched, setLineTouched] = useState<Record<string, boolean>>({});
    const [lineSavingId, setLineSavingId] = useState<string | null>(null);
    const [lineError, setLineError] = useState<string | null>(null);

    const [isPostConfirmOpen, setIsPostConfirmOpen] = useState(false);
    const [isVoidConfirmOpen, setIsVoidConfirmOpen] = useState(false);

    const locationMap = useMemo(
        () =>
            new Map<string, string>(
                locations.map((location) => [location.id, location.pathLabel])
            ),
        [locations]
    );

    const unfilledCount = useMemo(() => {
        if (!detail) {
            return 0;
        }
        return detail.lines.reduce((missing, line) => {
            const value = lineDrafts[line.id] ?? line.countedQty ?? "";
            return value.trim() === "" ? missing + 1 : missing;
        }, 0);
    }, [detail, lineDrafts]);

    useEffect(() => {
        void Promise.all([loadSessions(0), loadLocations()]);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [slug]);

    useEffect(() => {
        if (!selectedSessionId) {
            setDetail(null);
            setDetailError(null);
            setLineError(null);
            return;
        }
        void loadSessionDetail(selectedSessionId);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [selectedSessionId]);

    useEffect(() => {
        if (!detail) {
            setLineDrafts({});
            setLineTouched({});
            return;
        }

        const nextDrafts = detail.lines.reduce<Record<string, string>>((acc, line) => {
            acc[line.id] = line.countedQty ?? "";
            return acc;
        }, {});
        setLineDrafts(nextDrafts);
        setLineTouched({});
    }, [detail]);

    async function loadSessions(page: number) {
        setListLoading(true);
        setListError(null);
        try {
            const result = await listCountSessions(slug, {
                page,
                size: 20,
                status: statusFilter === "ALL" ? undefined : statusFilter,
                search: search || undefined,
            });

            setSessions(result.content);
        } catch (error) {
            setListError(extractCountingErrorMessage(error, t("counting.loadFailed")));
        } finally {
            setListLoading(false);
        }
    }

    async function loadSessionDetail(sessionId: string) {
        setDetailLoading(true);
        setDetailError(null);
        setLineError(null);
        try {
            const result = await getCountSession(slug, sessionId);
            setDetail(result);
        } catch (error) {
            setDetailError(extractCountingErrorMessage(error, t("counting.loadFailed")));
        } finally {
            setDetailLoading(false);
        }
    }

    async function loadLocations(searchTerm?: string) {
        try {
            const result = await getLocationLookups(slug, {
                search: searchTerm || undefined,
                size: 200,
            });
            setLocations(result.content);
        } catch {
            // non-blocking locations load
        }
    }

    async function handleSearchLocations(value: string) {
        setLocationSearch(value);
        await loadLocations(value);
    }

    function toggleLocation(locationId: string, checked: boolean) {
        setSelectedLocationIds((current) => {
            if (checked) {
                if (current.includes(locationId)) {
                    return current;
                }
                return [...current, locationId];
            }
            return current.filter((id) => id !== locationId);
        });
    }

    async function handleOpenSession() {
        if (!createName.trim() || selectedLocationIds.length === 0) {
            setListError(t("counting.validation.requiredOpenFields"));
            return;
        }

        setCreateSubmitting(true);
        setListError(null);
        try {
            const created = await openCountSession(slug, {
                name: createName.trim(),
                locationIds: selectedLocationIds,
            });

            setIsCreateDialogOpen(false);
            setCreateName("");
            setLocationSearch("");
            setSelectedLocationIds([]);
            setSelectedSessionId(created.id);

            await Promise.all([loadSessions(0), loadLocations()]);
        } catch (error) {
            setListError(extractCountingErrorMessage(error, t("counting.actionFailed")));
        } finally {
            setCreateSubmitting(false);
        }
    }

    function resolveEffectiveCountedQty(line: CountLine): string {
        return (lineDrafts[line.id] ?? line.countedQty ?? "").trim();
    }

    function computeVariance(line: CountLine): number | null {
        const countedRaw = resolveEffectiveCountedQty(line);
        if (countedRaw === "") {
            return null;
        }
        const counted = Number(countedRaw);
        const expected = Number(line.expectedQty);
        if (!Number.isFinite(counted) || !Number.isFinite(expected)) {
            return null;
        }
        return counted - expected;
    }

    function getLineInputFeedback(line: CountLine): { message: string; tone: "muted" | "danger" | "success" } | null {
        const draft = lineDrafts[line.id] ?? "";
        const trimmed = draft.trim();
        const touched = Boolean(lineTouched[line.id]);

        if (lineSavingId === line.id) {
            return { message: t("counting.feedback.saving"), tone: "muted" };
        }

        if (!touched) {
            return null;
        }

        if (trimmed === "") {
            return { message: t("counting.feedback.required"), tone: "danger" };
        }

        const parsed = Number(trimmed);
        if (!Number.isFinite(parsed) || parsed < 0) {
            return { message: t("counting.feedback.invalid"), tone: "danger" };
        }

        const persisted = (line.countedQty ?? "").trim();
        if (trimmed !== persisted) {
            return { message: t("counting.feedback.unsaved"), tone: "muted" };
        }

        return { message: t("counting.feedback.saved"), tone: "success" };
    }

    async function handleLineBlur(line: CountLine) {
        if (!detail || detail.status !== "OPEN") {
            return;
        }

        const nextValue = resolveEffectiveCountedQty(line);
        const currentValue = (line.countedQty ?? "").trim();
        if (nextValue === currentValue) {
            return;
        }
        if (nextValue === "") {
            setLineError(t("counting.validation.countedQtyRequired"));
            return;
        }

        const parsed = Number(nextValue);
        if (!Number.isFinite(parsed) || parsed < 0) {
            setLineError(t("counting.validation.countedQtyInvalid"));
            return;
        }

        setLineSavingId(line.id);
        setLineError(null);
        try {
            const updatedLine = await updateCountLine(slug, detail.id, line.id, { countedQty: nextValue });
            setDetail((current) => {
                if (!current) {
                    return current;
                }
                return {
                    ...current,
                    lines: current.lines.map((item) => (item.id === updatedLine.id ? updatedLine : item)),
                };
            });
            setLineDrafts((current) => ({ ...current, [line.id]: updatedLine.countedQty ?? "" }));
        } catch (error) {
            setLineError(extractCountingErrorMessage(error, t("counting.actionFailed")));
        } finally {
            setLineSavingId(null);
        }
    }

    async function handlePostSession() {
        if (!detail) {
            return;
        }
        try {
            const updated = await postCountSession(slug, detail.id);
            setIsPostConfirmOpen(false);
            setDetail(updated);
            await loadSessions(0);
        } catch (error) {
            setDetailError(extractCountingErrorMessage(error, t("counting.actionFailed")));
        }
    }

    async function handleVoidSession() {
        if (!detail) {
            return;
        }
        try {
            const updated = await voidCountSession(slug, detail.id);
            setIsVoidConfirmOpen(false);
            setDetail(updated);
            await loadSessions(0);
        } catch (error) {
            setDetailError(extractCountingErrorMessage(error, t("counting.actionFailed")));
        }
    }

    const statusBadgeClass = (status: CountStatus) => {
        switch (status) {
            case "OPEN":
                return "bg-muted text-foreground";
            case "POSTED":
                return "bg-green-100 text-green-800";
            case "VOID":
                return "bg-red-100 text-red-800";
            default:
                return "bg-muted text-foreground";
        }
    };

    if (selectedSessionId && detailLoading) {
        return (
            <div className="flex flex-col gap-4 p-6">
                <h1 className="text-2xl font-semibold">{t("counting.title")}</h1>
                <p className="text-sm text-muted-foreground">{t("counting.loading")}</p>
            </div>
        );
    }

    if (selectedSessionId && detail) {
        const isOpen = detail.status === "OPEN";
        const isPosted = detail.status === "POSTED";

        return (
            <div className="flex flex-col gap-4 p-6">
                <div className="flex items-center justify-between">
                    <h1 className="text-2xl font-semibold">{t("counting.title")}</h1>
                    <Button variant="outline" onClick={() => setSelectedSessionId(null)}>
                        {t("counting.backToList")}
                    </Button>
                </div>

                {detailError ? <p className="text-sm text-destructive">{detailError}</p> : null}

                <Card>
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2">
                            <span>{detail.name}</span>
                            <Badge className={statusBadgeClass(detail.status)}>{t(`counting.status.${detail.status}`)}</Badge>
                        </CardTitle>
                        <CardDescription>{new Date(detail.createdAt).toLocaleString()}</CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-3 text-sm">
                        <p><strong>{t("counting.form.createdBy")}: </strong>{detail.createdBy}</p>
                        {detail.postedAt ? (
                            <p><strong>{t("counting.form.postedAt")}: </strong>{new Date(detail.postedAt).toLocaleString()}</p>
                        ) : null}
                        <div>
                            <strong>{t("counting.form.locationsCovered")}: </strong>
                            {detail.locationIds.length === 0
                                ? "—"
                                : detail.locationIds.map((locationId) => locationMap.get(locationId) ?? locationId).join(", ")}
                        </div>
                        <p>
                            <strong>{t("counting.form.unfilledLines")}: </strong>
                            {unfilledCount}
                        </p>
                        {lineError ? <p className="text-sm text-destructive">{lineError}</p> : null}
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle>{t("counting.linesTitle")}</CardTitle>
                        <CardDescription>{t("counting.linesDescription")}</CardDescription>
                    </CardHeader>
                    <CardContent className="p-0">
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>{t("counting.columns.location")}</TableHead>
                                    <TableHead>{t("counting.columns.product")}</TableHead>
                                    <TableHead>{t("counting.columns.lot")}</TableHead>
                                    <TableHead className="text-end">{t("counting.columns.expectedQty")}</TableHead>
                                    <TableHead className="text-end">{t("counting.columns.countedQty")}</TableHead>
                                    <TableHead className="text-end">{t("counting.columns.variance")}</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {detail.lines.map((line) => {
                                    const variance = computeVariance(line);
                                    const varianceClass =
                                        variance == null
                                            ? "text-muted-foreground"
                                            : variance > 0
                                                ? "text-green-700"
                                                : variance < 0
                                                    ? "text-red-700"
                                                    : "text-muted-foreground";

                                    return (
                                        <TableRow key={line.id}>
                                            <TableCell>{line.locationPathLabel ?? line.locationId}</TableCell>
                                            <TableCell>{line.productName ?? line.productSku ?? line.productId}</TableCell>
                                            <TableCell>{line.lotNumber ?? "—"}</TableCell>
                                            <TableCell className="text-end font-mono tabular-nums">{line.expectedQty}</TableCell>
                                            <TableCell className="text-end">
                                                {isOpen ? (
                                                    <div className="ml-auto flex w-32 flex-col items-end gap-1">
                                                        <Input
                                                            className="w-full text-end font-mono tabular-nums"
                                                            type="number"
                                                            min="0"
                                                            step="0.0001"
                                                            value={lineDrafts[line.id] ?? ""}
                                                            onChange={(event) => {
                                                                const nextValue = event.target.value;
                                                                setLineTouched((current) => ({ ...current, [line.id]: true }));
                                                                setLineDrafts((current) => ({
                                                                    ...current,
                                                                    [line.id]: nextValue,
                                                                }));
                                                            }}
                                                            onBlur={() => void handleLineBlur(line)}
                                                            onKeyDown={(event) => {
                                                                if (event.key === "Enter") {
                                                                    event.currentTarget.blur();
                                                                }
                                                            }}
                                                            disabled={lineSavingId === line.id}
                                                            aria-label={`${t("counting.form.countedQty")} ${line.id}`}
                                                        />
                                                        {(() => {
                                                            const feedback = getLineInputFeedback(line);
                                                            if (!feedback) {
                                                                return null;
                                                            }

                                                            const className =
                                                                feedback.tone === "danger"
                                                                    ? "text-destructive"
                                                                    : feedback.tone === "success"
                                                                        ? "text-green-700"
                                                                        : "text-muted-foreground";

                                                            return (
                                                                <span className={`text-[11px] leading-none ${className}`}>
                                                                    {feedback.message}
                                                                </span>
                                                            );
                                                        })()}
                                                    </div>
                                                ) : (
                                                    line.countedQty ?? "—"
                                                )}
                                            </TableCell>
                                            <TableCell className={`text-end font-mono tabular-nums ${varianceClass}`}>
                                                {variance == null ? "—" : variance.toFixed(4)}
                                            </TableCell>
                                        </TableRow>
                                    );
                                })}
                            </TableBody>
                        </Table>
                        {detail.lines.length === 0 ? (
                            <p className="p-4 text-sm text-muted-foreground">{t("counting.emptyLines")}</p>
                        ) : null}
                    </CardContent>
                </Card>

                <div className="flex gap-2">
                    {isOpen && canPost ? (
                        <Button disabled={detail.lines.length === 0 || unfilledCount > 0} onClick={() => setIsPostConfirmOpen(true)}>
                            {t("counting.postAction")}
                        </Button>
                    ) : null}
                    {isOpen && canVoid ? (
                        <Button variant="destructive" onClick={() => setIsVoidConfirmOpen(true)}>
                            {t("counting.voidAction")}
                        </Button>
                    ) : null}
                </div>

                <AlertDialog open={isPostConfirmOpen} onOpenChange={setIsPostConfirmOpen}>
                    <AlertDialogContent>
                        <AlertDialogHeader>
                            <AlertDialogTitle>{t("counting.post.confirm")}</AlertDialogTitle>
                            <AlertDialogDescription>{t("counting.post.confirmDescription")}</AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                            <AlertDialogCancel>{t("counting.cancel")}</AlertDialogCancel>
                            <AlertDialogAction onClick={handlePostSession}>{t("counting.postAction")}</AlertDialogAction>
                        </AlertDialogFooter>
                    </AlertDialogContent>
                </AlertDialog>

                <AlertDialog open={isVoidConfirmOpen} onOpenChange={setIsVoidConfirmOpen}>
                    <AlertDialogContent>
                        <AlertDialogHeader>
                            <AlertDialogTitle>{t("counting.void.confirm")}</AlertDialogTitle>
                            <AlertDialogDescription>{t("counting.void.confirmDescription")}</AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                            <AlertDialogCancel>{t("counting.cancel")}</AlertDialogCancel>
                            <AlertDialogAction onClick={handleVoidSession}>{t("counting.voidAction")}</AlertDialogAction>
                        </AlertDialogFooter>
                    </AlertDialogContent>
                </AlertDialog>
            </div>
        );
    }

    return (
        <div className="flex flex-col gap-4 p-6">
            <div className="flex items-center justify-between">
                <h1 className="text-2xl font-semibold">{t("counting.title")}</h1>
                {canCreate ? (
                    <Button onClick={() => setIsCreateDialogOpen(true)}>{t("counting.newSession")}</Button>
                ) : null}
            </div>

            {listError ? <p className="text-sm text-destructive">{listError}</p> : null}

            <Card>
                <CardHeader>
                    <CardTitle>{t("counting.filtersTitle")}</CardTitle>
                    <CardDescription>{t("counting.filtersDescription")}</CardDescription>
                </CardHeader>
                <CardContent className="flex flex-wrap gap-3">
                    <select
                        className="rounded-md border border-input bg-background px-3 py-2 text-sm"
                        value={pendingStatusFilter}
                        onChange={(event) => setPendingStatusFilter(event.target.value as CountStatus | "ALL")}
                    >
                        <option value="ALL">{t("counting.status.all")}</option>
                        <option value="OPEN">{t("counting.status.OPEN")}</option>
                        <option value="POSTED">{t("counting.status.POSTED")}</option>
                        <option value="VOID">{t("counting.status.VOID")}</option>
                    </select>
                    <Input
                        className="max-w-xs"
                        placeholder={t("counting.searchPlaceholder")}
                        value={pendingSearch}
                        onChange={(event) => setPendingSearch(event.target.value)}
                    />
                    <Button
                        variant="outline"
                        onClick={() => {
                            setStatusFilter(pendingStatusFilter);
                            setSearch(pendingSearch);
                            void loadSessions(0);
                        }}
                    >
                        {t("counting.applyFilters")}
                    </Button>
                </CardContent>
            </Card>

            <Card>
                <CardContent className="p-0">
                    {listLoading ? <p className="p-4 text-sm text-muted-foreground">{t("counting.loading")}</p> : null}
                    {!listLoading && sessions.length === 0 ? (
                        <p className="p-4 text-sm text-muted-foreground">{t("counting.empty")}</p>
                    ) : null}

                    {sessions.length > 0 ? (
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>{t("counting.columns.name")}</TableHead>
                                    <TableHead>{t("counting.columns.status")}</TableHead>
                                    <TableHead className="text-end">{t("counting.columns.locationCount")}</TableHead>
                                    <TableHead className="text-end">{t("counting.columns.lineCount")}</TableHead>
                                    <TableHead>{t("counting.columns.createdAt")}</TableHead>
                                    <TableHead>{t("counting.columns.actions")}</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {sessions.map((session) => (
                                    <TableRow
                                        key={session.id}
                                        className="cursor-pointer"
                                        onClick={() => setSelectedSessionId(session.id)}
                                    >
                                        <TableCell>{session.name}</TableCell>
                                        <TableCell>
                                            <Badge className={statusBadgeClass(session.status)}>{t(`counting.status.${session.status}`)}</Badge>
                                        </TableCell>
                                        <TableCell className="text-end">{session.locationCount}</TableCell>
                                        <TableCell className="text-end">{session.lineCount}</TableCell>
                                        <TableCell>{new Date(session.createdAt).toLocaleString()}</TableCell>
                                        <TableCell>
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={(event) => {
                                                    event.stopPropagation();
                                                    setSelectedSessionId(session.id);
                                                }}
                                            >
                                                {t("counting.open")}
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
                        <DialogTitle>{t("counting.newSession")}</DialogTitle>
                        <DialogDescription>{t("counting.newSessionDescription")}</DialogDescription>
                    </DialogHeader>
                    <div className="space-y-3">
                        <div className="space-y-2">
                            <Label>{t("counting.form.name")}</Label>
                            <Input value={createName} onChange={(event) => setCreateName(event.target.value)} />
                        </div>
                        <div className="space-y-2">
                            <Label>{t("counting.form.locationSearch")}</Label>
                            <Input
                                value={locationSearch}
                                onChange={(event) => void handleSearchLocations(event.target.value)}
                                placeholder={t("inventory.lookups.locationPlaceholder")}
                            />
                        </div>
                        <div className="space-y-2">
                            <Label>{t("counting.form.selectLocations")}</Label>
                            <div className="max-h-64 space-y-2 overflow-y-auto rounded-md border border-input p-3">
                                {locations.map((location) => {
                                    const checked = selectedLocationIds.includes(location.id);
                                    return (
                                        <label key={location.id} className="flex cursor-pointer items-start gap-2 text-sm">
                                            <Checkbox
                                                checked={checked}
                                                onCheckedChange={(value) => toggleLocation(location.id, value === true)}
                                            />
                                            <span>{location.pathLabel}</span>
                                        </label>
                                    );
                                })}
                                {locations.length === 0 ? (
                                    <p className="text-sm text-muted-foreground">{t("inventory.lookups.noLocations")}</p>
                                ) : null}
                            </div>
                        </div>
                    </div>
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setIsCreateDialogOpen(false)}>{t("counting.cancel")}</Button>
                        <Button onClick={() => void handleOpenSession()} disabled={createSubmitting}>
                            {createSubmitting ? t("counting.form.saving") : t("counting.form.openSession")}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}
