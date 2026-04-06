import { useState, useEffect, useCallback } from "react";
import { useI18n } from "@/i18n";
import { Button } from "@/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { Textarea } from "@/shared/components/ui/textarea";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/shared/components/ui/select";
import { Badge } from "@/shared/components/ui/badge";
import { listCategories } from "@/features/tenant/api/f0Api";
import { listZoneTypes } from "@/features/tenant/api/zoneTypeApi";
import type { CategoryResult, ZoneTypeResult } from "@/features/tenant/types/f0";
import { createZone, updateZone, deleteZone, extractZoneErrorMessage } from "./zonesApi";
import type { ZoneRecord, CategoryRule } from "./zonesApi";
import { Move, Trash2 } from "lucide-react";

type RuleType = "ALLOWED" | "PROHIBITED" | "NONE";

interface ZoneAttributePanelProps {
    slug: string;
    zone: ZoneRecord | null;
    /** When set (from Sketch draw), panel is in "create" mode */
    pendingGeometry?: number[][][] | null;
    canManage: boolean;
    onSaveSuccess: (updated: ZoneRecord) => void;
    onDeleteSuccess: (zoneId: string) => void;
    onCancelCreate?: () => void;
    onEditShape?: () => void;
}

export function ZoneAttributePanel({
    slug,
    zone,
    pendingGeometry = null,
    canManage,
    onSaveSuccess,
    onDeleteSuccess,
    onCancelCreate,
    onEditShape,
}: ZoneAttributePanelProps) {
    const { t } = useI18n();
    const isCreateMode = pendingGeometry != null && zone == null;

    const [allCategories, setAllCategories] = useState<CategoryResult[]>([]);
    const [allZoneTypes, setAllZoneTypes] = useState<ZoneTypeResult[]>([]);
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [violationAction, setViolationAction] = useState<"BLOCK" | "WARN">("BLOCK");
    const [zoneTypeId, setZoneTypeId] = useState("");
    const [displayColor, setDisplayColor] = useState("#6B7280");
    const [categoryRules, setCategoryRules] = useState<Record<string, RuleType>>({});
    const [saving, setSaving] = useState(false);
    const [deleting, setDeleting] = useState(false);
    const [saveError, setSaveError] = useState<string | null>(null);

    // Load categories once
    useEffect(() => {
        let cancelled = false;
        void listCategories(slug, { size: 100, active: true }).then((result) => {
            if (!cancelled) setAllCategories(result.content);
        });
        void listZoneTypes(slug, { active: true }).then((result) => {
            if (!cancelled) setAllZoneTypes(result);
        });
        return () => { cancelled = true; };
    }, [slug]);

    // Sync form when zone changes
    useEffect(() => {
        if (!zone) {
            setName("");
            setDescription("");
            setViolationAction("BLOCK");
            setCategoryRules({});
            setSaveError(null);
            return;
        }
        setName(zone.name);
        setDescription(zone.description ?? "");
        setViolationAction(zone.violationAction);
        setZoneTypeId(zone.zoneTypeId ?? "");
        setDisplayColor(zone.displayColor ?? "#6B7280");
        const rules: Record<string, RuleType> = {};
        for (const r of zone.categoryRules) {
            rules[r.categoryId] = r.ruleType as RuleType;
        }
        setCategoryRules(rules);
        setSaveError(null);
    }, [zone]);

    const cycleRule = useCallback((categoryId: string) => {
        if (!canManage) return;
        setCategoryRules((prev) => {
            const current: RuleType = prev[categoryId] ?? "NONE";
            const next: RuleType =
                current === "NONE" ? "ALLOWED" :
                    current === "ALLOWED" ? "PROHIBITED" : "NONE";
            const updated = { ...prev };
            if (next === "NONE") {
                delete updated[categoryId];
            } else {
                updated[categoryId] = next;
            }
            return updated;
        });
    }, [canManage]);

    const handleSave = async () => {
        setSaving(true);
        setSaveError(null);
        try {
            const rules: CategoryRule[] = Object.entries(categoryRules).map(
                ([categoryId, ruleType]) => ({ categoryId, ruleType: ruleType as "ALLOWED" | "PROHIBITED" })
            );
            let result: ZoneRecord;
            if (isCreateMode) {
                result = await createZone(slug, {
                    name,
                    description: description || null,
                    violationAction,
                    coordinates: pendingGeometry!,
                    categoryRules: rules,
                    zoneTypeId: zoneTypeId || null,
                    displayColor,
                });
            } else {
                result = await updateZone(slug, zone!.id, {
                    name,
                    description: description || null,
                    violationAction,
                    categoryRules: rules,
                    zoneTypeId: zoneTypeId || null,
                    displayColor,
                });
            }
            onSaveSuccess(result);
        } catch (err) {
            setSaveError(extractZoneErrorMessage(err, t("gis.zones.saveError")));
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!zone) return; setDeleting(true);
        try {
            await deleteZone(slug, zone.id);
            onDeleteSuccess(zone.id);
        } catch (err) {
            setSaveError(extractZoneErrorMessage(err, t("gis.zones.deleteError")));
        } finally {
            setDeleting(false);
        }
    };

    if (!zone && !isCreateMode) {
        return (
            <div className="flex h-full items-center justify-center p-6 text-center">
                <p className="text-sm text-muted-foreground">{t("gis.zones.selectZonePrompt")}</p>
            </div>
        );
    }

    return (
        <div className="flex h-full flex-col gap-4 overflow-y-auto p-4">
            {/* Zone header */}
            <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                    <h3 className="truncate text-sm font-semibold">
                        {isCreateMode ? t("gis.zones.newZoneTitle") : zone!.name}
                    </h3>
                    {!isCreateMode && (
                        <p className="mt-0.5 font-mono text-xs text-muted-foreground">
                            {zone!.source === "ARCGIS_IMPORT" ? "ArcGIS Import" : "Manual"}
                        </p>
                    )}
                </div>
                {!isCreateMode && canManage ? (
                    <div className="flex items-center gap-1">
                        {onEditShape && (
                            <Button
                                variant="ghost"
                                size="icon"
                                className="h-7 w-7 shrink-0"
                                onClick={onEditShape}
                                title={t("gis.zones.editShape")}
                            >
                                <Move className="h-3.5 w-3.5" />
                            </Button>
                        )}
                        <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7 shrink-0 text-destructive hover:text-destructive"
                            onClick={handleDelete}
                            disabled={deleting}
                            title={t("gis.zones.deleteZone")}
                        >
                            <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                    </div>
                ) : isCreateMode && onCancelCreate ? (
                    <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={onCancelCreate}>
                        {t("gis.zones.cancelCreate")}
                    </Button>
                ) : null}
            </div>

            {/* Name */}
            <div className="flex flex-col gap-1.5">
                <label className="text-xs font-medium">{t("gis.zones.nameLabel")}</label>
                <Input
                    value={name}
                    onChange={(e: React.ChangeEvent<HTMLInputElement>) => setName(e.target.value)}
                    disabled={!canManage}
                    className="h-8 text-sm"
                />
            </div>

            {/* Description */}
            <div className="flex flex-col gap-1.5">
                <label className="text-xs font-medium">{t("gis.zones.descriptionLabel")}</label>
                <Textarea
                    value={description}
                    onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => setDescription(e.target.value)}
                    disabled={!canManage}
                    rows={2}
                    className="resize-none text-sm"
                />
            </div>

            {/* Violation action */}
            <div className="flex flex-col gap-1.5">
                <label className="text-xs font-medium">{t("gis.zones.violationAction")}</label>
                <Select
                    value={violationAction}
                    onValueChange={(v) => setViolationAction(v as "BLOCK" | "WARN")}
                    disabled={!canManage}
                >
                    <SelectTrigger className="h-8 w-full text-xs">
                        <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                        <SelectItem value="BLOCK">{t("gis.zones.action.BLOCK")}</SelectItem>
                        <SelectItem value="WARN">{t("gis.zones.action.WARN")}</SelectItem>
                    </SelectContent>
                </Select>
                <p className="text-[10px] text-muted-foreground">
                    {t(`gis.zones.action.${violationAction}.hint` as Parameters<typeof t>[0])}
                </p>
            </div>

            {/* Zone type */}
            {allZoneTypes.length > 0 && (
                <div className="flex flex-col gap-1.5">
                    <label className="text-xs font-medium">{t("gis.zones.zoneTypeLabel")}</label>
                    <select
                        className="w-full rounded-md border border-input bg-background px-2 py-1.5 text-sm"
                        value={zoneTypeId}
                        disabled={!canManage}
                        onChange={(e) => setZoneTypeId(e.target.value)}
                    >
                        <option value="">{t("gis.zones.zoneTypePlaceholder")}</option>
                        {allZoneTypes.map((zt) => (
                            <option key={zt.id} value={zt.id}>{zt.code} — {zt.displayName}</option>
                        ))}
                    </select>
                </div>
            )}

            {/* Display color */}
            <div className="flex flex-col gap-1.5">
                <label className="text-xs font-medium">{t("gis.zones.displayColorLabel")}</label>
                <div className="flex items-center gap-2">
                    <input
                        type="color"
                        value={displayColor}
                        disabled={!canManage}
                        onChange={(e) => setDisplayColor(e.target.value)}
                        className="h-8 w-10 cursor-pointer rounded border border-input bg-background p-0.5"
                    />
                    <span className="font-mono text-xs text-muted-foreground">{displayColor}</span>
                    {canManage && (
                        <button
                            type="button"
                            className="text-xs text-muted-foreground underline hover:text-foreground"
                            onClick={() => setDisplayColor("#6B7280")}
                        >
                            {t("gis.zones.displayColorReset")}
                        </button>
                    )}
                </div>
            </div>

            {/* Category rules */}
            {allCategories.length > 0 && (
                <div className="flex flex-col gap-1.5">
                    <label className="text-xs font-medium">{t("gis.zones.categoryRules")}</label>
                    <p className="text-[10px] text-muted-foreground">{t("gis.zones.categoryRulesHint")}</p>
                    <div className="flex flex-col divide-y rounded-md border">
                        {allCategories.map((cat) => {
                            const rule: RuleType = categoryRules[cat.id] ?? "NONE";
                            return (
                                <div key={cat.id} className="flex items-center justify-between px-2.5 py-1.5">
                                    <span className="truncate text-xs">{cat.name}</span>
                                    <button
                                        type="button"
                                        disabled={!canManage}
                                        onClick={() => cycleRule(cat.id)}
                                        className="ml-2 shrink-0"
                                    >
                                        <Badge
                                            variant={
                                                rule === "ALLOWED"
                                                    ? "default"
                                                    : rule === "PROHIBITED"
                                                        ? "destructive"
                                                        : "outline"
                                            }
                                            className="cursor-pointer select-none text-[10px]"
                                        >
                                            {rule === "NONE"
                                                ? t("gis.zones.rule.NONE")
                                                : rule === "ALLOWED"
                                                    ? t("gis.zones.rule.ALLOWED")
                                                    : t("gis.zones.rule.PROHIBITED")}
                                        </Badge>
                                    </button>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}

            {/* Error */}
            {saveError ? (
                <p className="text-xs text-destructive">{saveError}</p>
            ) : null}

            {/* Save */}
            {canManage ? (
                <Button size="sm" onClick={handleSave} disabled={saving || !name.trim()} className="mt-auto">
                    {saving
                        ? t("gis.zones.saving")
                        : isCreateMode
                            ? t("gis.zones.createZone")
                            : t("gis.zones.saveZone")}
                </Button>
            ) : null}
        </div>
    );
}
