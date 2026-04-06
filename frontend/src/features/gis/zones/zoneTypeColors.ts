const PALETTE: Array<{ fill: string; stroke: string }> = [
    { fill: "rgba(14, 165, 233, 0.15)", stroke: "#0ea5e9" },   // sky
    { fill: "rgba(34, 197, 94, 0.15)", stroke: "#22c55e" },    // green
    { fill: "rgba(245, 158, 11, 0.15)", stroke: "#f59e0b" },   // amber
    { fill: "rgba(239, 68, 68, 0.15)", stroke: "#ef4444" },    // red
    { fill: "rgba(99, 102, 241, 0.15)", stroke: "#6366f1" },   // indigo
    { fill: "rgba(236, 72, 153, 0.15)", stroke: "#ec4899" },   // pink
    { fill: "rgba(20, 184, 166, 0.15)", stroke: "#14b8a6" },   // teal
    { fill: "rgba(249, 115, 22, 0.15)", stroke: "#f97316" },   // orange
];

export const DEFAULT_ZONE_COLOR = { fill: "rgba(107, 114, 128, 0.15)", stroke: "#6b7280" };

function hashString(s: string): number {
    let h = 0;
    for (let i = 0; i < s.length; i++) {
        h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
    }
    return Math.abs(h);
}

export function getZoneColor(name: string | null | undefined): { fill: string; stroke: string } {
    if (!name) return DEFAULT_ZONE_COLOR;
    return PALETTE[hashString(name) % PALETTE.length];
}

/** @deprecated kept for backward compat — use getZoneColor instead */
export function getZoneTypeColor(zoneType: string | null | undefined): { fill: string; stroke: string } {
    return getZoneColor(zoneType);
}

/**
 * Resolves the ArcGIS symbol colors for a zone.
 * Prefers the zone's own `displayColor` hex value; falls back to name-based hashing.
 */
export function resolveZoneDisplayColor(
    displayColor: string | null | undefined,
    name: string | null | undefined
): { fill: [number, number, number, number]; stroke: [number, number, number] } {
    if (displayColor && /^#[0-9a-fA-F]{6}$/.test(displayColor)) {
        const r = parseInt(displayColor.slice(1, 3), 16);
        const g = parseInt(displayColor.slice(3, 5), 16);
        const b = parseInt(displayColor.slice(5, 7), 16);
        return { fill: [r, g, b, 0.15], stroke: [r, g, b] };
    }
    const { fill, stroke } = getZoneColor(name);
    // parse rgba(r,g,b,a) fill string
    const fillMatch = fill.match(/rgba\(([^)]+)\)/);
    if (fillMatch) {
        const [fr, fg, fb] = fillMatch[1].split(",").map(Number);
        const strokeMatch = stroke.match(/#([0-9a-fA-F]{2})([0-9a-fA-F]{2})([0-9a-fA-F]{2})/);
        if (strokeMatch) {
            const [, sr, sg, sb] = strokeMatch.map((v, i) => i === 0 ? v : parseInt(v, 16));
            return { fill: [fr, fg, fb, 0.15], stroke: [sr as number, sg as number, sb as number] };
        }
    }
    return { fill: [107, 114, 128, 0.15], stroke: [107, 114, 128] };
}

