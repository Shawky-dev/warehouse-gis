import { useRef, useState, type ChangeEvent } from "react";
import { Map, Trash2, Upload } from "lucide-react";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { useAuth } from "@/features/auth/context/AuthContext";
import { TENANT_PERMISSIONS } from "@/features/auth/shared/permissions";
import { useI18n } from "@/i18n";
import { useFloorPlanApi } from "./useFloorPlanApi";
import { WarehouseMapView } from "./WarehouseMapView";

type FeedbackState =
  | { tone: "success"; message: string }
  | { tone: "error"; message: string }
  | null;

export default function FloorPlansPage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const canManage = hasPermission(TENANT_PERMISSIONS.GIS_FLOOR_PLAN_MANAGE);
  const { config, loading, error, svgContent, upload, remove } = useFloorPlanApi();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [feedback, setFeedback] = useState<FeedbackState>(null);

  async function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    setUploading(true);
    setFeedback(null);

    try {
      await upload(file);
      setFeedback({ tone: "success", message: t("gis.floorPlans.uploadSuccess") });
    } catch {
      setFeedback({ tone: "error", message: t("gis.floorPlans.uploadError") });
    } finally {
      setUploading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  }

  async function handleDelete() {
    setDeleting(true);
    setFeedback(null);

    try {
      await remove();
      setFeedback({ tone: "success", message: t("gis.floorPlans.deleteSuccess") });
    } catch {
      setFeedback({ tone: "error", message: t("gis.floorPlans.uploadError") });
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">{t("gis.floorPlans.pageTitle")}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t("gis.floorPlans.pageDescription")}</p>
      </div>

      {canManage ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t("gis.floorPlans.uploadLabel")}</CardTitle>
            <CardDescription>{t("gis.floorPlans.uploadHint")}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <input
              ref={fileInputRef}
              type="file"
              accept=".svg,image/svg+xml"
              className="hidden"
              onChange={handleFileChange}
            />

            <div className="flex flex-wrap items-center gap-3">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={uploading || deleting}
                onClick={() => fileInputRef.current?.click()}
              >
                <Upload className="mr-2 h-4 w-4" />
                {uploading ? "Uploading..." : t("gis.floorPlans.uploadButton")}
              </Button>

              {config?.hasFloorPlan ? (
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="text-destructive hover:text-destructive"
                  disabled={uploading || deleting}
                  onClick={() => void handleDelete()}
                >
                  <Trash2 className="mr-2 h-4 w-4" />
                  {deleting ? "Removing..." : t("gis.floorPlans.deleteButton")}
                </Button>
              ) : null}
            </div>

            {feedback ? (
              <p
                className={
                  feedback.tone === "success"
                    ? "text-sm text-emerald-600"
                    : "text-sm text-destructive"
                }
              >
                {feedback.message}
              </p>
            ) : null}
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Map className="h-4 w-4" />
            {t("gis.floorPlans.mapTitle")}
          </CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="flex h-96 items-center justify-center text-sm text-muted-foreground">
              Loading...
            </div>
          ) : null}

          {!loading && error ? (
            <div className="flex h-96 items-center justify-center text-sm text-destructive">{error}</div>
          ) : null}

          {!loading && !error && !config?.hasFloorPlan ? (
            <div className="flex h-96 items-center justify-center text-sm text-muted-foreground">
              {t("gis.floorPlans.noFloorPlan")}
            </div>
          ) : null}

          {!loading && !error && config?.hasFloorPlan && svgContent ? (
            <WarehouseMapView
              svgContent={svgContent}
              anchorLon={config.anchorLon}
              anchorLat={config.anchorLat}
              widthMeters={config.widthMeters}
              lengthMeters={config.lengthMeters}
            />
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
