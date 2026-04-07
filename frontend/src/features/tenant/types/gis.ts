export interface HazardTypeSummary {
    id: string;
    code: string;
    displayName: string;
}

export interface ZoneTypeSummary {
    id: string;
    code: string;
    displayName: string;
}

export interface HazardBufferResult {
    id: string;
    name: string;
    source: string;
    notes: string | null;
    importBatchId: string | null;
    sourceFilename: string | null;
    importedAt: string | null;
    restrictedHazardTypes: HazardTypeSummary[];
    createdAt: string;
    updatedAt: string;
}

export interface HazardBufferFeatureProps {
    id: string;
    name: string;
    restrictedHazardTypeIds: string[];
}

// ── Heatmaps ─────────────────────────────────────────────────────────────────

export interface StaticHeatmapRecord {
    id: string;
    name: string;
    sourceFilename: string;
    geoserverLayerName: string;
    isDefault: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface DynamicHeatmapMetric {
    key: string;
    label: string;
    description: string;
    unit: string | null;
}

export interface DynamicHeatmapFeatureProps {
    locationId: string;
    label: string;
    positionPath: string;
    metricKey: string;
    weight: number;
    rawValue: number;
}
