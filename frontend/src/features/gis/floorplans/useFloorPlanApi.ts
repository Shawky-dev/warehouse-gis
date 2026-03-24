import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";

export interface FloorPlanConfig {
  anchorLon: number;
  anchorLat: number;
  widthMeters: number;
  lengthMeters: number;
  hasFloorPlan: boolean;
}

export function useFloorPlanApi() {
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");

  const [config, setConfig] = useState<FloorPlanConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [svgContent, setSvgContent] = useState<string | null>(null);

  const fetchConfig = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await api.get<FloorPlanConfig>(`/${slug}/gis/floorplan/config`);
      setConfig(data);

      if (!data.hasFloorPlan) {
        setSvgContent(null);
        return;
      }

      const svgResponse = await api.get<string>(`/${slug}/gis/floorplan/svg`, {
        responseType: "text",
      });
      setSvgContent(svgResponse.data);
    } catch {
      setSvgContent(null);
      setError("Failed to load floor plan configuration.");
    } finally {
      setLoading(false);
    }
  }, [slug]);

  useEffect(() => {
    void fetchConfig();
  }, [fetchConfig]);

  const upload = useCallback(
    async (file: File): Promise<void> => {
      const formData = new FormData();
      formData.append("file", file);
      await api.post(`/${slug}/gis/floorplan/upload`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      await fetchConfig();
    },
    [fetchConfig, slug]
  );

  const remove = useCallback(async (): Promise<void> => {
    await api.delete(`/${slug}/gis/floorplan`);
    await fetchConfig();
  }, [fetchConfig, slug]);

  return { config, loading, error, svgContent, upload, remove, refetch: fetchConfig };
}
