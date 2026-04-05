import { useState } from "react";
import { useI18n } from "@/i18n";
import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import type { ZoneViolationError } from "@/features/gis/zones/zonesApi";

interface ZoneViolationBannerProps {
    error: ZoneViolationError | null;
    onOverride?: () => void;
}

export function ZoneViolationBanner({ error, onOverride }: ZoneViolationBannerProps) {
    const { t } = useI18n();
    const [confirmOpen, setConfirmOpen] = useState(false);

    if (!error) return null;

    const { violationAction, message, violatedZone, suggestedZones } = error;

    if (violationAction === "BLOCK") {
        return (
            <div className="rounded-none border border-destructive/50 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                <p className="font-medium">{t("gis.violations.title")}</p>
                <p>{message}</p>
                {violatedZone ? (
                    <p className="mt-0.5">
                        <span className="font-medium">{t("gis.violations.violatedZone")}: </span>
                        {violatedZone.name}
                    </p>
                ) : null}
                {suggestedZones && suggestedZones.length > 0 ? (
                    <p className="mt-0.5">
                        <span className="font-medium">{t("gis.violations.suggestedZones")}: </span>
                        {suggestedZones.map((z) => z.name).join(", ")}
                    </p>
                ) : null}
            </div>
        );
    }

    if (violationAction === "WARN") {
        return (
            <div className="rounded-none border border-amber-500/40 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:bg-amber-950 dark:text-amber-200">
                <div className="flex items-start justify-between gap-4">
                    <div>
                        <p className="font-medium">{t("gis.violations.warnTitle")}</p>
                        <p>{message}</p>
                        {violatedZone ? (
                            <p className="mt-0.5">
                                <span className="font-medium">{t("gis.violations.violatedZone")}: </span>
                                {violatedZone.name}
                            </p>
                        ) : null}
                    </div>
                    {onOverride ? (
                        <Button
                            size="sm"
                            variant="outline"
                            className="shrink-0 border-amber-500 text-amber-800 hover:bg-amber-100 dark:text-amber-200"
                            onClick={onOverride}
                        >
                            {t("gis.violations.proceedAnyway")}
                        </Button>
                    ) : null}
                </div>
            </div>
        );
    }

    // CONFIRM — show modal
    return (
        <>
            <div className="rounded-none border border-amber-500/40 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:bg-amber-950 dark:text-amber-200">
                <div className="flex items-start justify-between gap-4">
                    <div>
                        <p className="font-medium">{t("gis.violations.confirmTitle")}</p>
                        <p>{message}</p>
                        {violatedZone ? (
                            <p className="mt-0.5">
                                <span className="font-medium">{t("gis.violations.violatedZone")}: </span>
                                {violatedZone.name}
                            </p>
                        ) : null}
                    </div>
                    {onOverride ? (
                        <Button
                            size="sm"
                            variant="outline"
                            className="shrink-0 border-amber-500 text-amber-800 hover:bg-amber-100 dark:text-amber-200"
                            onClick={() => setConfirmOpen(true)}
                        >
                            {t("gis.violations.reviewAndConfirm")}
                        </Button>
                    ) : null}
                </div>
            </div>

            <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
                <DialogContent>
                    <DialogHeader>
                        <DialogTitle>{t("gis.violations.confirmDialogTitle")}</DialogTitle>
                        <DialogDescription>{message}</DialogDescription>
                    </DialogHeader>
                    {violatedZone ? (
                        <div className="rounded-md border p-3 text-sm">
                            <p className="font-medium">{violatedZone.name}</p>
                        </div>
                    ) : null}
                    {suggestedZones && suggestedZones.length > 0 ? (
                        <p className="text-sm text-muted-foreground">
                            <span className="font-medium">{t("gis.violations.suggestedZones")}: </span>
                            {suggestedZones.map((z) => z.name).join(", ")}
                        </p>
                    ) : null}
                    <DialogFooter>
                        <Button variant="outline" onClick={() => setConfirmOpen(false)}>
                            {t("common.cancel")}
                        </Button>
                        <Button
                            variant="destructive"
                            onClick={() => {
                                setConfirmOpen(false);
                                onOverride?.();
                            }}
                        >
                            {t("gis.violations.confirmAnyway")}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </>
    );
}
