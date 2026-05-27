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
    source: string;
    notes: string | null;
    restrictedHazardTypeIds: string[];
    restrictedHazardTypeCodes: string[];
}
