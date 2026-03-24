export const TEMPLATE_COLORS: Record<string, { fill: string; stroke: string }> = {
  Zone:  { fill: "rgba(59, 130, 246, 0.15)",  stroke: "#3b82f6" },
  Aisle: { fill: "rgba(16, 185, 129, 0.15)",  stroke: "#10b981" },
  Bay:   { fill: "rgba(245, 158, 11, 0.15)",  stroke: "#f59e0b" },
  Shelf: { fill: "rgba(239, 68, 68, 0.15)",   stroke: "#ef4444" },
};

export const DEFAULT_TEMPLATE_COLOR = { fill: "rgba(139, 92, 246, 0.15)", stroke: "#8b5cf6" };

export function getTemplateColor(name: string) {
  return TEMPLATE_COLORS[name] ?? DEFAULT_TEMPLATE_COLOR;
}

export function getTemplateStroke(name: string) {
  return getTemplateColor(name).stroke;
}
