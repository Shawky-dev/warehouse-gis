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

