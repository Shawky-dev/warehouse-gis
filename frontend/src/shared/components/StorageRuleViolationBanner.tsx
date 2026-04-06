import { useI18n } from "@/i18n";
import { Button } from "@/components/ui/button";
import type { StorageRuleViolation } from "@/features/tenant/types/inventory";

interface StorageRuleViolationBannerProps {
    violation: StorageRuleViolation | null;
    onOverride?: () => void;
    onViewOnMap?: () => void;
}

export function StorageRuleViolationBanner({
    violation,
    onOverride,
    onViewOnMap,
}: StorageRuleViolationBannerProps) {
    const { t } = useI18n();

    if (!violation) return null;

    if (violation.ruleType === "HAZARD_BUFFER") {
        return (
            <div className="rounded-none border border-destructive/50 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                <div className="flex items-start justify-between gap-4">
                    <div className="space-y-1">
                        <p className="font-medium">{t("gis.storageViolation.hazardBuffer.title")}</p>
                        <p>{violation.message}</p>
                        <p className="mt-0.5">
                            <span className="font-medium">{t("gis.storageViolation.hazardBuffer.areaLabel")}: </span>
                            {violation.violatedArea.name}
                        </p>
                        {violation.restrictedHazardTypes.length > 0 && (
                            <div className="flex flex-wrap items-center gap-1">
                                <span className="font-medium">{t("gis.storageViolation.hazardBuffer.restrictedTypes")}: </span>
                                {violation.restrictedHazardTypes.map((ht) => (
                                    <span
                                        key={ht.id}
                                        className="rounded bg-destructive/20 px-1.5 py-0.5 font-mono text-xs"
                                    >
                                        {ht.code}
                                    </span>
                                ))}
                            </div>
                        )}
                    </div>
                    {onViewOnMap && (
                        <Button
                            size="sm"
                            variant="outline"
                            className="shrink-0 border-destructive/50 text-destructive hover:bg-destructive/10"
                            onClick={onViewOnMap}
                        >
                            {t("gis.storageViolation.viewOnMap")}
                        </Button>
                    )}
                </div>
            </div>
        );
    }

    if (violation.ruleType === "ZONE") {
        const isBlock = violation.violationAction === "BLOCK";
        return (
            <div
                className={
                    isBlock
                        ? "rounded-none border border-destructive/50 bg-destructive/10 px-3 py-2 text-sm text-destructive"
                        : "rounded-none border border-amber-500/40 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:bg-amber-950 dark:text-amber-200"
                }
            >
                <div className="flex items-start justify-between gap-4">
                    <div className="space-y-1">
                        <p className="font-medium">
                            {isBlock
                                ? t("gis.storageViolation.zone.titleBlock")
                                : t("gis.storageViolation.zone.titleWarn")}
                        </p>
                        <p>{violation.message}</p>
                        <p className="mt-0.5">
                            <span className="font-medium">{t("gis.storageViolation.zone.areaLabel")}: </span>
                            {violation.violatedArea.name}
                        </p>
                        {violation.suggestedZones.length > 0 && (
                            <p className="mt-0.5">
                                <span className="font-medium">{t("gis.storageViolation.zone.suggestedZones")}: </span>
                                {violation.suggestedZones.map((z) => z.name).join(", ")}
                            </p>
                        )}
                    </div>
                    <div className="flex shrink-0 flex-col gap-1">
                        {onViewOnMap && (
                            <Button
                                size="sm"
                                variant="outline"
                                className={
                                    isBlock
                                        ? "border-destructive/50 text-destructive hover:bg-destructive/10"
                                        : "border-amber-500 text-amber-800 hover:bg-amber-100 dark:text-amber-200"
                                }
                                onClick={onViewOnMap}
                            >
                                {t("gis.storageViolation.viewOnMap")}
                            </Button>
                        )}
                        {!isBlock && onOverride && (
                            <Button
                                size="sm"
                                variant="outline"
                                className="border-amber-500 text-amber-800 hover:bg-amber-100 dark:text-amber-200"
                                onClick={onOverride}
                            >
                                {t("gis.storageViolation.proceedAnyway")}
                            </Button>
                        )}
                    </div>
                </div>
            </div>
        );
    }

    // REQUIRED_ZONE
    return (
        <div className="rounded-none border border-amber-500/40 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:bg-amber-950 dark:text-amber-200">
            <div className="flex items-start justify-between gap-4">
                <div className="space-y-1">
                    <p className="font-medium">{t("gis.storageViolation.requiredZone.title")}</p>
                    <p>{violation.message}</p>
                    <p className="mt-0.5">
                        <span className="font-medium">{t("gis.storageViolation.requiredZone.requiredType")}: </span>
                        {violation.requiredZoneType.displayName}
                    </p>
                    {violation.suggestedZones.length > 0 && (
                        <p className="mt-0.5">
                            <span className="font-medium">{t("gis.storageViolation.zone.suggestedZones")}: </span>
                            {violation.suggestedZones.map((z) => z.name).join(", ")}
                        </p>
                    )}
                </div>
                <div className="flex shrink-0 flex-col gap-1">
                    {onViewOnMap && (
                        <Button
                            size="sm"
                            variant="outline"
                            className="border-amber-500 text-amber-800 hover:bg-amber-100 dark:text-amber-200"
                            onClick={onViewOnMap}
                        >
                            {t("gis.storageViolation.viewOnMap")}
                        </Button>
                    )}
                    {onOverride && (
                        <Button
                            size="sm"
                            variant="outline"
                            className="border-amber-500 text-amber-800 hover:bg-amber-100 dark:text-amber-200"
                            onClick={onOverride}
                        >
                            {t("gis.storageViolation.proceedAnyway")}
                        </Button>
                    )}
                </div>
            </div>
        </div>
    );
}
