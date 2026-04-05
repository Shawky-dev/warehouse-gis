import { api } from "@/lib/api";
import axios from "axios";

function tenantBasePath(slug: string) {
    return `/${slug}`;
}

function tenantHeaders(slug: string) {
    return { "X-TENANT-ID": slug };
}

// ── Zone interfaces ───────────────────────────────────────────────────────────

export interface CategoryRule {
    categoryId: string;
    ruleType: "ALLOWED" | "PROHIBITED";
}

export interface ZoneRecord {
    id: string;
    name: string;
    description: string | null;
    violationAction: "BLOCK" | "WARN";
    source: "MANUAL" | "ARCGIS_IMPORT";
    categoryRules: CategoryRule[];
    createdAt: string;
    updatedAt: string;
}

export interface ZoneRequest {
    name: string;
    description?: string | null;
    /** GeoJSON coordinates: [[[lon, lat], ...]] */
    coordinates?: number[][][];
    violationAction: "BLOCK" | "WARN";
    source?: string;
    categoryRules: CategoryRule[];
}

// ── GeoJSON feature property interfaces ───────────────────────────────────────

export interface ZoneFeatureProps {
    id: string;
    name: string;
    description: string | null;
    violationAction: "BLOCK" | "WARN";
    source: "MANUAL" | "ARCGIS_IMPORT";
    categoryRules: CategoryRule[];
}

export interface LocationFeatureProps {
    locationId: string;
    label: string;
    positionPath: string;
}

export interface GeoJsonFeatureCollection<P> {
    type: "FeatureCollection";
    features: Array<{
        type: "Feature";
        id?: string;
        geometry: { type: string; coordinates: unknown };
        properties: P;
    }>;
}

// ── Violation error ───────────────────────────────────────────────────────────

export interface ZoneViolationError {
    error: "ZONE_VIOLATION";
    message: string;
    violationAction: "BLOCK" | "WARN";
    violatedZone?: { id: string; name: string; violationAction: string };
    suggestedZones?: { id: string; name: string; violationAction: string }[];
}

export function extractZoneErrorMessage(error: unknown, fallback: string): string {
    if (axios.isAxiosError(error)) {
        const data = error.response?.data as { message?: string } | undefined;
        if (data?.message) return data.message;
    }
    return fallback;
}

export function isZoneViolationError(error: unknown): error is { response: { data: ZoneViolationError } } {
    if (!axios.isAxiosError(error)) return false;
    const code = (error.response?.data as ZoneViolationError | undefined)?.error;
    return code === "ZONE_VIOLATION";
}

// ── Zone CRUD ─────────────────────────────────────────────────────────────────

export async function listZones(tenantSlug: string): Promise<ZoneRecord[]> {
    const res = await api.get<ZoneRecord[]>(
        `${tenantBasePath(tenantSlug)}/gis/zones`,
        { headers: tenantHeaders(tenantSlug) }
    );
    return res.data;
}

export async function createZone(tenantSlug: string, payload: ZoneRequest): Promise<ZoneRecord> {
    const res = await api.post<ZoneRecord>(
        `${tenantBasePath(tenantSlug)}/gis/zones`,
        payload,
        { headers: tenantHeaders(tenantSlug) }
    );
    return res.data;
}

export async function updateZone(
    tenantSlug: string,
    zoneId: string,
    payload: ZoneRequest
): Promise<ZoneRecord> {
    const res = await api.put<ZoneRecord>(
        `${tenantBasePath(tenantSlug)}/gis/zones/${zoneId}`,
        payload,
        { headers: tenantHeaders(tenantSlug) }
    );
    return res.data;
}

export async function deleteZone(tenantSlug: string, zoneId: string): Promise<void> {
    await api.delete(
        `${tenantBasePath(tenantSlug)}/gis/zones/${zoneId}`,
        { headers: tenantHeaders(tenantSlug) }
    );
}

export async function importZones(
    tenantSlug: string,
    geojsonFeatures: unknown[]
): Promise<ZoneRecord[]> {
    const res = await api.post<ZoneRecord[]>(
        `${tenantBasePath(tenantSlug)}/gis/zones/import`,
        { features: geojsonFeatures },
        { headers: tenantHeaders(tenantSlug) }
    );
    return res.data;
}

// ── GeoJSON layers ────────────────────────────────────────────────────────────

export async function fetchZonesGeoJson(
    tenantSlug: string
): Promise<GeoJsonFeatureCollection<ZoneFeatureProps>> {
    const res = await api.get<GeoJsonFeatureCollection<ZoneFeatureProps>>(
        `${tenantBasePath(tenantSlug)}/gis/zones/geojson`,
        { headers: tenantHeaders(tenantSlug) }
    );
    return res.data;
}

export async function fetchLocationsGeoJson(
    tenantSlug: string
): Promise<GeoJsonFeatureCollection<LocationFeatureProps>> {
    const res = await api.get<GeoJsonFeatureCollection<LocationFeatureProps>>(
        `${tenantBasePath(tenantSlug)}/gis/locations/geojson`,
        { headers: tenantHeaders(tenantSlug) }
    );
    return res.data;
}

