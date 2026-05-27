import { useEffect, useMemo, useState } from "react";
import type { ChangeEvent } from "react";
import { Move, Trash2 } from "lucide-react";
import { useI18n } from "@/i18n";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { Textarea } from "@/shared/components/ui/textarea";
import { Checkbox } from "@/components/ui/checkbox";
import { Badge } from "@/shared/components/ui/badge";
import { listHazardTypes } from "@/features/tenant/api/hazardTypeApi";
import type { HazardTypeResult } from "@/features/tenant/types/f0";
import type { HazardBufferResult } from "@/features/tenant/types/gis";
import {
    createHazardBuffer,
    deleteHazardBuffer,
    extractHazardBufferErrorMessage,
    updateHazardBuffer,
} from "./hazardBuffersApi";

interface HazardBufferAttributePanelProps {
    slug: string;
    buffer: HazardBufferResult | null;
    pendingGeometry?: number[][][] | null;
    canManage: boolean;
    onSaveSuccess: (updated: HazardBufferResult) => void;
    onDeleteSuccess: (bufferId: string) => void;
    onCancelCreate?: () => void;
    onEditShape?: () => void;
}

export function HazardBufferAttributePanel({
    slug,
    buffer,
    pendingGeometry = null,
    canManage,
    onSaveSuccess,
    onDeleteSuccess,
    onCancelCreate,
    onEditShape,
}: HazardBufferAttributePanelProps) {
    const { t } = useI18n();
    const isCreateMode = pendingGeometry != null && buffer == null;

    const [hazardTypes, setHazardTypes] = useState<HazardTypeResult[]>([]);
    const [name, setName] = useState("");
    const [notes, setNotes] = useState("");
    const [restrictedTypeIds, setRestrictedTypeIds] = useState<string[]>([]);
    const [saving, setSaving] = useState(false);
    const [deleting, setDeleting] = useState(false);
    const [saveError, setSaveError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;
        void listHazardTypes(slug, { active: true }).then((result) => {
            if (!cancelled) setHazardTypes(result);
        });
        return () => { cancelled = true; };
    }, [slug]);

    useEffect(() => {
        if (!buffer) {
            setName("");
            setNotes("");
            setRestrictedTypeIds([]);
            setSaveError(null);
            return;
        }
        setName(buffer.name);
        setNotes(buffer.notes ?? "");
        setRestrictedTypeIds(buffer.restrictedHazardTypes.map((ht) => ht.id));
        setSaveError(null);
    }, [buffer]);

    const selectedTypeSet = useMemo(() => new Set(restrictedTypeIds), [restrictedTypeIds]);
    const isValid = name.trim().length > 0 && restrictedTypeIds.length > 0;

    const toggleHazardType = (hazardTypeId: string) => {
        if (!canManage) return;
        setRestrictedTypeIds((prev) => (
            prev.includes(hazardTypeId)
                ? prev.filter((id) => id !== hazardTypeId)
                : [...prev, hazardTypeId]
        ));
    };

    const handleSave = async () => {
        setSaving(true);
        setSaveError(null);
        try {
            const payload = {
                name,
                notes: notes || null,
                coordinates: isCreateMode ? pendingGeometry! : undefined,
                restrictedHazardTypeIds: restrictedTypeIds,
            };
            const result = isCreateMode
                ? await createHazardBuffer(slug, payload)
                : await updateHazardBuffer(slug, buffer!.id, payload);
            onSaveSuccess(result);
        } catch (err) {
            setSaveError(extractHazardBufferErrorMessage(err, t("gis.hazardBuffers.management.saveError")));
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!buffer) return;
        setDeleting(true);
        setSaveError(null);
        try {
            await deleteHazardBuffer(slug, buffer.id);
            onDeleteSuccess(buffer.id);
        } catch (err) {
            setSaveError(extractHazardBufferErrorMessage(err, t("gis.hazardBuffers.management.deleteError")));
        } finally {
            setDeleting(false);
        }
    };

    if (!buffer && !isCreateMode) {
        return (
            <div className="flex h-full items-center justify-center p-6 text-center">
                <p className="text-sm text-muted-foreground">{t("gis.hazardBuffers.management.selectBufferPrompt")}</p>
            </div>
        );
    }

    return (
        <div className="flex h-full flex-col gap-4 overflow-y-auto p-4">
            <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                    <h3 className="truncate text-sm font-semibold">
                        {isCreateMode ? t("gis.hazardBuffers.management.newBuffer") : buffer!.name}
                    </h3>
                    {!isCreateMode && (
                        <p className="mt-0.5 font-mono text-xs text-muted-foreground">
                            {buffer!.source === "ARCGIS_IMPORT" ? "ArcGIS Import" : "Manual"}
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
                                title={t("gis.hazardBuffers.management.editShape")}
                            >
                                <Move className="h-3.5 w-3.5" />
                            </Button>
                        )}
                        <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7 shrink-0 text-destructive hover:text-destructive"
                            onClick={() => void handleDelete()}
                            disabled={deleting}
                            title={t("gis.hazardBuffers.management.deleteBuffer")}
                        >
                            <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                    </div>
                ) : isCreateMode && onCancelCreate ? (
                    <Button variant="ghost" size="sm" className="h-7 text-xs" onClick={onCancelCreate}>
                        {t("gis.hazardBuffers.cancelAction")}
                    </Button>
                ) : null}
            </div>

            <div className="flex flex-col gap-1.5">
                <label className="text-xs font-medium">{t("gis.hazardBuffers.management.nameLabel")}</label>
                <Input
                    value={name}
                    onChange={(e: ChangeEvent<HTMLInputElement>) => setName(e.target.value)}
                    disabled={!canManage}
                    className="h-8 text-sm"
                />
            </div>

            <div className="flex flex-col gap-1.5">
                <label className="text-xs font-medium">{t("gis.hazardBuffers.management.notesLabel")}</label>
                <Textarea
                    value={notes}
                    onChange={(e: ChangeEvent<HTMLTextAreaElement>) => setNotes(e.target.value)}
                    disabled={!canManage}
                    rows={3}
                    className="resize-none text-sm"
                />
            </div>

            <div className="flex flex-col gap-1.5">
                <label className="text-xs font-medium">{t("gis.hazardBuffers.management.restrictedTypesLabel")}</label>
                <div className="flex flex-col divide-y rounded-md border">
                    {hazardTypes.map((hazardType) => (
                        <label
                            key={hazardType.id}
                            className="flex cursor-pointer items-center gap-2 px-2.5 py-2 text-xs"
                        >
                            <Checkbox
                                checked={selectedTypeSet.has(hazardType.id)}
                                disabled={!canManage}
                                onCheckedChange={() => toggleHazardType(hazardType.id)}
                            />
                            <span className="min-w-0 flex-1 truncate">{hazardType.displayName}</span>
                            <Badge variant="secondary" className="font-mono text-[10px]">
                                {hazardType.code}
                            </Badge>
                        </label>
                    ))}
                </div>
            </div>

            {saveError ? <p className="text-xs text-destructive">{saveError}</p> : null}

            {canManage ? (
                <Button size="sm" onClick={() => void handleSave()} disabled={saving || !isValid} className="mt-auto">
                    {saving ? t("gis.zones.saving") : t("gis.zones.saveZone")}
                </Button>
            ) : null}
        </div>
    );
}
