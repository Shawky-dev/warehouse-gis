import type { TranslationKey } from "@/i18n";

export type DashboardEndpoint =
  | "spatial-kpis"
  | "inventory-ops"
  | "warnings"
  | "master-data"
  | "stocktake"
  | "activity";

export type DashboardSectionId =
  | "overview"
  | "inventoryOps"
  | "warnings"
  | "masterData"
  | "stocktake"
  | "activity";

export interface DashboardStat {
  id: string;
  label: string;
  value: string;
  hint?: string | null;
}

export interface DashboardHighlight {
  id: string;
  title: string;
  description: string;
}

export interface DashboardMetric {
  id: string;
  label: string;
  value: string;
  hint?: string | null;
}

export interface DashboardValueItem {
  id: string;
  label: string;
  value: string;
  numericValue?: number | null;
  severity?: string | null;
  category?: string | null;
  hint?: string | null;
}

export interface DashboardSeriesValue {
  key: string;
  value: number;
}

export interface DashboardTimelineBucket {
  label: string;
  series: DashboardSeriesValue[];
}

export interface DashboardWidget {
  type: "metric-grid" | "bar-list" | "timeline" | "alert-list";
  id: string;
  title: string;
  description?: string | null;
  metrics?: DashboardMetric[] | null;
  items?: DashboardValueItem[] | null;
  timeline?: DashboardTimelineBucket[] | null;
}

export interface DashboardSectionResponse {
  section: DashboardEndpoint;
  stats: DashboardStat[];
  highlights: DashboardHighlight[];
  widgets?: DashboardWidget[];
  generatedAt: string;
}

export interface DashboardPermissionMatrixEntry {
  id: DashboardSectionId;
  endpoint: DashboardEndpoint;
  titleKey: TranslationKey;
  descriptionKey: TranslationKey;
  permissions: readonly string[];
  workspaces: readonly {
    permissions: readonly string[];
    path: (tenantSlug: string) => string;
  }[];
}
