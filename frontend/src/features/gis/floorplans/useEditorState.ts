import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import axios from "axios";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { api } from "@/lib/api";

export interface EditorTemplate {
  id: string;
  name: string;
  iconName: string;
}

export interface ExistingPolygon {
  gisBlockId: string;
  templateName: string;
  label: string;
  rings: number[][][];
}

export interface AvailableBlock {
  id: string;
  fullCode: string;
  depth: number;
}

export interface PendingPolygon {
  rings: number[][][];
  templateName: string;
}

export function useEditorState() {
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");

  const [templates, setTemplates] = useState<EditorTemplate[]>([]);
  const [activeTemplateName, setActiveTemplateName] = useState<string | null>(null);
  const [existingPolygonsByTemplate, setExistingPolygonsByTemplate] = useState<
    Record<string, ExistingPolygon[]>
  >({});
  const [pendingPolygon, setPendingPolygon] = useState<PendingPolygon | null>(null);
  const [availableBlocks, setAvailableBlocks] = useState<AvailableBlock[]>([]);
  const [loadingBlocks, setLoadingBlocks] = useState(false);
  const [hasActiveLayout, setHasActiveLayout] = useState(true);

  // Fetch all templates once on mount
  useEffect(() => {
    async function loadTemplates() {
      try {
        const { data } = await api.get<{ content?: EditorTemplate[] } | EditorTemplate[]>(
          `/${slug}/block-templates?size=100`
        );
        const items = Array.isArray(data) ? data : (data as { content: EditorTemplate[] }).content ?? [];
        setTemplates(items);
        if (items.length > 0) {
          setActiveTemplateName(items[0].name);
        }
      } catch {
        // leave templates empty
      }
    }
    void loadTemplates();
  }, [slug]);

  // Fetch existing polygons for one template from the GeoJSON endpoint
  const fetchExistingPolygons = useCallback(
    async (templateName: string) => {
      try {
        const { data } = await api.get<{
          features?: Array<{
            id?: string;
            geometry?: { coordinates?: number[][][] };
            properties?: { templateName?: string; label?: string; id?: string };
          }>;
        }>(`/${slug}/gis/layout/geojson?layer=${encodeURIComponent(templateName)}`);

        const polygons: ExistingPolygon[] = (data.features ?? []).map((feature) => ({
          gisBlockId: feature.id ?? feature.properties?.id ?? "",
          templateName: feature.properties?.templateName ?? templateName,
          label: feature.properties?.label ?? "",
          rings: feature.geometry?.coordinates ?? [],
        }));

        setExistingPolygonsByTemplate((prev) => ({ ...prev, [templateName]: polygons }));
      } catch {
        // Silently ignore — user may not have GIS_LAYOUT_VIEW permission
      }
    },
    [slug]
  );

  // Fetch existing polygons for all templates whenever templates load
  useEffect(() => {
    if (templates.length === 0) return;
    for (const template of templates) {
      void fetchExistingPolygons(template.name);
    }
  }, [templates, fetchExistingPolygons]);

  // Open the block assignment dialog for a just-drawn polygon
  const openAssignmentDialog = useCallback(
    async (polygon: PendingPolygon) => {
      setPendingPolygon(polygon);
      setLoadingBlocks(true);
      setAvailableBlocks([]);
      try {
        const { data } = await api.get<AvailableBlock[]>(
          `/${slug}/gis/floorplan/blocks?templateName=${encodeURIComponent(polygon.templateName)}`
        );
        setAvailableBlocks(data);
        setHasActiveLayout(true);
      } catch (err) {
        if (axios.isAxiosError(err) && err.response?.status === 404) {
          setHasActiveLayout(false);
        }
        setAvailableBlocks([]);
      } finally {
        setLoadingBlocks(false);
      }
    },
    [slug]
  );

  // POST the assigned polygon to the backend then refresh GeoJSON for that template
  const savePolygon = useCallback(
    async (layoutBlockId: string, fullCode: string) => {
      if (!pendingPolygon) return;
      const depth = availableBlocks.find((b) => b.id === layoutBlockId)?.depth ?? 0;
      await api.post(`/${slug}/gis/blocks/manual`, {
        layoutBlockId,
        templateName: pendingPolygon.templateName,
        label: fullCode,
        positionPath: fullCode,
        depth,
        rings: pendingPolygon.rings,
      });
      const templateName = pendingPolygon.templateName;
      setPendingPolygon(null);
      await fetchExistingPolygons(templateName);
    },
    [slug, pendingPolygon, availableBlocks, fetchExistingPolygons]
  );

  // DELETE a polygon then refresh GeoJSON for that template
  const deletePolygon = useCallback(
    async (gisBlockId: string, templateName: string) => {
      await api.delete(`/${slug}/gis/blocks/manual/${gisBlockId}`);
      await fetchExistingPolygons(templateName);
    },
    [slug, fetchExistingPolygons]
  );

  const clearPendingPolygon = useCallback(() => {
    setPendingPolygon(null);
  }, []);

  // Count of saved polygons per template name
  const polygonCountByTemplate = useMemo<Record<string, number>>(() => {
    const counts: Record<string, number> = {};
    for (const [name, polys] of Object.entries(existingPolygonsByTemplate)) {
      counts[name] = polys.length;
    }
    return counts;
  }, [existingPolygonsByTemplate]);

  // Flat list of all existing polygons across all templates
  const existingPolygons = useMemo<ExistingPolygon[]>(
    () => Object.values(existingPolygonsByTemplate).flat(),
    [existingPolygonsByTemplate]
  );

  return {
    templates,
    activeTemplateName,
    setActiveTemplateName,
    existingPolygons,
    polygonCountByTemplate,
    pendingPolygon,
    availableBlocks,
    loadingBlocks,
    hasActiveLayout,
    openAssignmentDialog,
    savePolygon,
    deletePolygon,
    clearPendingPolygon,
  };
}
