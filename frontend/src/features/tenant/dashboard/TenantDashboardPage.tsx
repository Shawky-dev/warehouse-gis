import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  XAxis,
  YAxis,
} from "recharts";
import { Link, useParams } from "react-router-dom";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { useAuth } from "@/features/auth/context/AuthContext";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { extractDashboardErrorMessage, getDashboardSection } from "@/features/tenant/dashboard/dashboardApi";
import { DASHBOARD_PERMISSION_MATRIX } from "@/features/tenant/dashboard/permissionMatrix";
import type {
  DashboardPermissionMatrixEntry,
  DashboardSectionId,
  DashboardSectionResponse,
  DashboardValueItem,
  DashboardWidget,
} from "@/features/tenant/dashboard/types";
import { useI18n } from "@/i18n";
import { Badge } from "@/shared/components/ui/badge";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/shared/components/ui/tabs";

interface DashboardTabState {
  data: DashboardSectionResponse | null;
  error: string | null;
  isLoading: boolean;
}

type NumericDashboardItem = DashboardValueItem & { numericValue: number };

const DASHBOARD_REFRESH_INTERVAL_MS = 60_000;
const DASHBOARD_CHART_COLORS = [
  "var(--chart-1)",
  "var(--chart-2)",
  "var(--chart-3)",
  "var(--chart-4)",
  "var(--chart-5)",
] as const;

const EMPTY_TAB_STATE: DashboardTabState = {
  data: null,
  error: null,
  isLoading: false,
};

function DashboardStateCard({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <Card className="border-dashed">
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      {action ? <CardFooter>{action}</CardFooter> : null}
    </Card>
  );
}

function severityVariant(value?: string | null): "default" | "secondary" | "destructive" | "outline" {
  switch (value) {
    case "critical":
      return "destructive";
    case "high":
      return "default";
    case "medium":
      return "secondary";
    default:
      return "outline";
  }
}

function humanizeToken(value?: string | null) {
  if (!value) {
    return "";
  }

  return value
    .split(/[-_]/g)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(" ");
}

function getSeverityLabel(t: ReturnType<typeof useI18n>["t"], severity?: string | null) {
  switch (severity) {
    case "critical":
      return t("tenant.dashboard.severity.critical");
    case "high":
      return t("tenant.dashboard.severity.high");
    case "medium":
      return t("tenant.dashboard.severity.medium");
    case "low":
      return t("tenant.dashboard.severity.low");
    default:
      return humanizeToken(severity);
  }
}

function getChartColor(index: number) {
  return DASHBOARD_CHART_COLORS[index % DASHBOARD_CHART_COLORS.length];
}

function getSeriesColor(key: string, index: number) {
  switch (key) {
    case "RECEIVE":
      return "var(--chart-1)";
    case "PICK":
      return "var(--chart-2)";
    case "TRANSFER_IN":
      return "var(--chart-3)";
    case "TRANSFER_OUT":
      return "var(--chart-4)";
    case "ADJUST":
      return "var(--chart-5)";
    default:
      return getChartColor(index);
  }
}

function formatShortDateLabel(value: string, locale: string) {
  try {
    return new Date(value).toLocaleDateString(locale, {
      month: "short",
      day: "numeric",
    });
  } catch {
    return value;
  }
}

function truncateLabel(value: string, maxLength = 24) {
  if (value.length <= maxLength) {
    return value;
  }

  return `${value.slice(0, maxLength - 1)}...`;
}

function getNumericItems(items: DashboardValueItem[]) {
  return items.filter((item): item is NumericDashboardItem => typeof item.numericValue === "number");
}

function MetricGridWidget({ widget }: { widget: DashboardWidget }) {
  if (!widget.metrics?.length) {
    return null;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{widget.title}</CardTitle>
        {widget.description ? <CardDescription>{widget.description}</CardDescription> : null}
      </CardHeader>
      <CardContent className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {widget.metrics.map((metric) => (
          <div key={metric.id} className="rounded-md border p-4">
            <p className="text-sm text-muted-foreground">{metric.label}</p>
            <p className="mt-2 text-2xl font-semibold">{metric.value}</p>
            {metric.hint ? <p className="mt-2 text-xs text-muted-foreground">{metric.hint}</p> : null}
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function BarListWidget({ widget }: { widget: DashboardWidget }) {
  const items = widget.items ?? [];
  const numericItems = getNumericItems(items);

  if (!items.length) {
    return null;
  }

  if (!numericItems.length) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>{widget.title}</CardTitle>
          {widget.description ? <CardDescription>{widget.description}</CardDescription> : null}
        </CardHeader>
        <CardContent className="space-y-3">
          {items.map((item) => (
            <div key={item.id} className="flex items-start justify-between gap-4 rounded-md border p-3">
              <div className="space-y-1">
                <p className="text-sm font-medium">{item.label}</p>
                {item.hint ? <p className="text-xs text-muted-foreground">{item.hint}</p> : null}
              </div>
              <p className="text-sm font-semibold">{item.value}</p>
            </div>
          ))}
        </CardContent>
      </Card>
    );
  }

  const shouldRenderPie = widget.id === "category-distribution" && numericItems.length <= 8;

  if (shouldRenderPie) {
    const chartData = numericItems.map((item, index) => ({
      segment: item.id,
      label: item.label,
      value: item.numericValue,
      formattedValue: item.value,
      fill: getChartColor(index),
    }));
    const chartConfig: ChartConfig = Object.fromEntries(
      chartData.map((item) => [
        item.segment,
        {
          label: item.label,
          color: item.fill,
        },
      ])
    );

    return (
      <Card>
        <CardHeader>
          <CardTitle>{widget.title}</CardTitle>
          {widget.description ? <CardDescription>{widget.description}</CardDescription> : null}
        </CardHeader>
        <CardContent className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_220px]">
          <ChartContainer config={chartConfig} className="mx-auto min-h-[280px] w-full max-w-[420px]">
            <PieChart accessibilityLayer>
              <ChartTooltip content={<ChartTooltipContent nameKey="segment" hideLabel />} />
              <Pie
                data={chartData}
                dataKey="value"
                nameKey="segment"
                innerRadius={70}
                outerRadius={110}
                paddingAngle={3}
                strokeWidth={4}
              >
                {chartData.map((item) => (
                  <Cell key={item.segment} fill={item.fill} />
                ))}
              </Pie>
              <ChartLegend content={<ChartLegendContent nameKey="segment" className="flex-wrap" />} />
            </PieChart>
          </ChartContainer>

          <div className="space-y-3">
            {chartData.map((item) => (
              <div key={`${item.segment}-summary`} className="flex items-center justify-between gap-3 rounded-md border p-3">
                <div className="flex min-w-0 items-center gap-2">
                  <span
                    className="h-2.5 w-2.5 shrink-0 rounded-[2px]"
                    style={{ backgroundColor: item.fill }}
                    aria-hidden="true"
                  />
                  <p className="truncate text-sm font-medium">{item.label}</p>
                </div>
                <p className="shrink-0 text-sm font-semibold">{item.formattedValue}</p>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  const chartData = numericItems.map((item, index) => ({
    label: item.label,
    shortLabel: truncateLabel(item.label, 22),
    value: item.numericValue,
    formattedValue: item.value,
    fill: getChartColor(index),
  }));
  const chartConfig: ChartConfig = {
    value: {
      label: widget.title,
      color: "var(--chart-1)",
    },
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{widget.title}</CardTitle>
        {widget.description ? <CardDescription>{widget.description}</CardDescription> : null}
      </CardHeader>
      <CardContent className="space-y-4">
        <ChartContainer config={chartConfig} className="min-h-[280px] w-full">
          <BarChart accessibilityLayer data={chartData} layout="vertical" margin={{ left: 12, right: 12 }}>
            <CartesianGrid horizontal={false} />
            <YAxis
              dataKey="shortLabel"
              type="category"
              tickLine={false}
              axisLine={false}
              width={145}
              interval={0}
            />
            <XAxis dataKey="value" type="number" hide />
            <ChartTooltip
              cursor={false}
              content={
                <ChartTooltipContent
                  formatter={(value, _name, item) => (
                    <div className="flex w-full items-center justify-between gap-3">
                      <span className="text-muted-foreground">{item.payload.label as string}</span>
                      <span className="font-mono font-medium text-foreground">
                        {typeof value === "number" ? value.toLocaleString() : String(value)}
                      </span>
                    </div>
                  )}
                />
              }
            />
            <Bar dataKey="value" radius={6} fill="var(--color-value)" />
          </BarChart>
        </ChartContainer>

        <div className="grid gap-2 md:grid-cols-2">
          {chartData.map((item) => (
            <div key={`${item.label}-detail`} className="flex items-center justify-between gap-3 rounded-md border p-3">
              <p className="min-w-0 truncate text-sm text-muted-foreground">{item.label}</p>
              <p className="shrink-0 text-sm font-semibold">{item.formattedValue}</p>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

function TimelineWidget({ widget }: { widget: DashboardWidget }) {
  const { locale } = useI18n();

  if (!widget.timeline?.length) {
    return null;
  }

  const seriesKeys = Array.from(
    new Set(widget.timeline.flatMap((bucket) => bucket.series.map((series) => series.key)))
  );
  const chartConfig: ChartConfig = Object.fromEntries(
    seriesKeys.map((key, index) => [
      key,
      {
        label: humanizeToken(key),
        color: getSeriesColor(key, index),
      },
    ])
  );
  const chartData = widget.timeline.map((bucket) => ({
    label: bucket.label,
    shortLabel: formatShortDateLabel(bucket.label, locale),
    ...Object.fromEntries(bucket.series.map((series) => [series.key, series.value])),
  }));

  return (
    <Card>
      <CardHeader>
        <CardTitle>{widget.title}</CardTitle>
        {widget.description ? <CardDescription>{widget.description}</CardDescription> : null}
      </CardHeader>
      <CardContent className="space-y-4">
        <ChartContainer config={chartConfig} className="min-h-[300px] w-full">
          <AreaChart accessibilityLayer data={chartData} margin={{ left: 12, right: 12 }}>
            <CartesianGrid vertical={false} />
            <XAxis
              dataKey="shortLabel"
              tickLine={false}
              axisLine={false}
              tickMargin={8}
              minTickGap={20}
            />
            <ChartTooltip content={<ChartTooltipContent indicator="line" />} />
            {seriesKeys.length > 1 ? <ChartLegend content={<ChartLegendContent className="flex-wrap" />} /> : null}
            {seriesKeys.map((key) => (
              <Area
                key={key}
                dataKey={key}
                type="monotone"
                stroke={`var(--color-${key})`}
                fill={`var(--color-${key})`}
                fillOpacity={0.18}
                strokeWidth={2}
                stackId={seriesKeys.length > 1 ? "dashboard-series" : undefined}
              />
            ))}
          </AreaChart>
        </ChartContainer>

        <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-4">
          {seriesKeys.map((key, index) => (
            <div key={`${widget.id}-${key}`} className="flex items-center gap-2 rounded-md border p-3 text-sm">
              <span
                className="h-2.5 w-2.5 shrink-0 rounded-[2px]"
                style={{ backgroundColor: getSeriesColor(key, index) }}
                aria-hidden="true"
              />
              <span className="text-muted-foreground">{humanizeToken(key)}</span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

function AlertListWidget({ widget }: { widget: DashboardWidget }) {
  const { t } = useI18n();
  const [severityFilter, setSeverityFilter] = useState<string>("all");
  const [categoryFilter, setCategoryFilter] = useState<string>("all");
  const items = widget.items ?? [];
  const severities = Array.from(new Set(items.map((item) => item.severity).filter(Boolean))) as string[];
  const categories = Array.from(new Set(items.map((item) => item.category).filter(Boolean))) as string[];
  const visibleItems = items.filter(
    (item) =>
      (severityFilter === "all" || item.severity === severityFilter) &&
      (categoryFilter === "all" || item.category === categoryFilter)
  );

  if (!items.length) {
    return null;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{widget.title}</CardTitle>
        {widget.description ? <CardDescription>{widget.description}</CardDescription> : null}
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant={severityFilter === "all" ? "default" : "outline"} onClick={() => setSeverityFilter("all")}>
            {t("tenant.dashboard.filters.allSeverities")}
          </Button>
          {severities.map((severity) => (
            <Button
              key={severity}
              size="sm"
              variant={severityFilter === severity ? "default" : "outline"}
              onClick={() => setSeverityFilter(severity)}
            >
              {getSeverityLabel(t, severity)}
            </Button>
          ))}
        </div>
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant={categoryFilter === "all" ? "default" : "outline"} onClick={() => setCategoryFilter("all")}>
            {t("tenant.dashboard.filters.allCategories")}
          </Button>
          {categories.map((category) => (
            <Button
              key={category}
              size="sm"
              variant={categoryFilter === category ? "default" : "outline"}
              onClick={() => setCategoryFilter(category)}
            >
              {humanizeToken(category)}
            </Button>
          ))}
        </div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Alert</TableHead>
              <TableHead>Severity</TableHead>
              <TableHead>Category</TableHead>
              <TableHead align="right">Value</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {visibleItems.map((item: DashboardValueItem) => (
              <TableRow key={item.id}>
                <TableCell className="whitespace-normal">
                  <div className="space-y-1">
                    <p className="font-medium">{item.label}</p>
                    {item.hint ? <p className="text-xs text-muted-foreground">{item.hint}</p> : null}
                  </div>
                </TableCell>
                <TableCell>
                  {item.severity ? (
                    <Badge variant={severityVariant(item.severity)}>
                      {getSeverityLabel(t, item.severity)}
                    </Badge>
                  ) : null}
                </TableCell>
                <TableCell>{item.category ? humanizeToken(item.category) : "-"}</TableCell>
                <TableCell align="right">{item.value}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function DashboardWidgets({ data }: { data: DashboardSectionResponse }) {
  const widgets = data.widgets ?? [];

  if (widgets.length === 0) {
    return null;
  }

  return (
    <div className="space-y-4">
      {widgets.map((widget) => {
        if (widget.type === "metric-grid") {
          return <MetricGridWidget key={widget.id} widget={widget} />;
        }
        if (widget.type === "bar-list") {
          return <BarListWidget key={widget.id} widget={widget} />;
        }
        if (widget.type === "timeline") {
          return <TimelineWidget key={widget.id} widget={widget} />;
        }
        if (widget.type === "alert-list") {
          return <AlertListWidget key={widget.id} widget={widget} />;
        }
        return null;
      })}
    </div>
  );
}

const TenantDashboardPage = () => {
  const { t, locale } = useI18n();
  const { hasPermission, status } = useAuth();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const normalizedSlug = normalizeTenantSlug(tenantSlug ?? "");
  const [activeSection, setActiveSection] = useState<DashboardSectionId | null>(null);
  const [sectionStates, setSectionStates] = useState<Partial<Record<DashboardSectionId, DashboardTabState>>>({});

  const visibleSections = useMemo(
    () =>
      DASHBOARD_PERMISSION_MATRIX.filter((section) =>
        section.permissions.some((permission) => hasPermission(permission))
      ),
    [hasPermission]
  );

  useEffect(() => {
    if (visibleSections.length === 0) {
      setActiveSection(null);
      return;
    }

    setActiveSection((current) =>
      current && visibleSections.some((section) => section.id === current)
        ? current
        : visibleSections[0].id
    );
  }, [visibleSections]);

  const loadSection = useCallback(
    async (section: DashboardPermissionMatrixEntry) => {
      setSectionStates((current) => ({
        ...current,
        [section.id]: {
          data: current[section.id]?.data ?? null,
          error: null,
          isLoading: true,
        },
      }));

      try {
        const data = await getDashboardSection(normalizedSlug, section.endpoint);
        setSectionStates((current) => ({
          ...current,
          [section.id]: {
            data,
            error: null,
            isLoading: false,
          },
        }));
      } catch (error) {
        setSectionStates((current) => ({
          ...current,
          [section.id]: {
            data: current[section.id]?.data ?? null,
            error: extractDashboardErrorMessage(error, t("tenant.dashboard.loadFailed")),
            isLoading: false,
          },
        }));
      }
    },
    [normalizedSlug, t]
  );

  useEffect(() => {
    if (!activeSection) {
      return;
    }

    const section = visibleSections.find((entry) => entry.id === activeSection);
    const state = sectionStates[activeSection];

    if (!section || state?.data || state?.error || state?.isLoading) {
      return;
    }

    void loadSection(section);
  }, [activeSection, loadSection, sectionStates, visibleSections]);

  useEffect(() => {
    if (!activeSection) {
      return;
    }

    const section = visibleSections.find((entry) => entry.id === activeSection);
    const state = sectionStates[activeSection];

    if (!section || !state?.data) {
      return;
    }

    const refreshTimer = window.setInterval(() => {
      void loadSection(section);
    }, DASHBOARD_REFRESH_INTERVAL_MS);

    return () => window.clearInterval(refreshTimer);
  }, [activeSection, loadSection, sectionStates, visibleSections]);

  const formatGeneratedAt = (value: string) => {
    try {
      return new Date(value).toLocaleString(locale);
    } catch {
      return value;
    }
  };

  const resolveWorkspacePath = useCallback(
    (section: DashboardPermissionMatrixEntry) =>
      section.workspaces.find((workspace) =>
        workspace.permissions.some((permission) => hasPermission(permission))
      )?.path(normalizedSlug) ?? null,
    [hasPermission, normalizedSlug]
  );

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h1 className="text-xl font-semibold">{t("tenant.dashboardTitle")}</h1>
        <p className="text-sm text-muted-foreground">
          {t("tenant.activeTenant", { tenant: normalizedSlug })}
        </p>
      </div>

      {status === "idle" || status === "loading" ? (
        <DashboardStateCard
          title={t("tenant.dashboard.loadingTitle")}
          description={t("tenant.dashboard.loadingDescription")}
        />
      ) : visibleSections.length === 0 ? (
        <DashboardStateCard
          title={t("authStatus.accessDeniedTitle")}
          description={t("tenant.dashboard.noAccessDescription")}
        />
      ) : (
        <Tabs value={activeSection ?? undefined} onValueChange={(value) => setActiveSection(value as DashboardSectionId)}>
          <TabsList>
            {visibleSections.map((section) => (
              <TabsTrigger key={section.id} value={section.id}>
                {t(section.titleKey)}
              </TabsTrigger>
            ))}
          </TabsList>

          {visibleSections.map((section) => {
            const state = sectionStates[section.id] ?? EMPTY_TAB_STATE;
            const shouldShowLoading = section.id === activeSection && !state.data && !state.error;
            const hasWidgets = !!state.data?.widgets?.length;
            const isEmpty = !!state.data && !hasWidgets && state.data.stats.length === 0 && state.data.highlights.length === 0;
            const workspacePath = resolveWorkspacePath(section);

            return (
              <TabsContent key={section.id} value={section.id}>
                {state.isLoading || shouldShowLoading ? (
                  <DashboardStateCard
                    title={t("tenant.dashboard.sectionLoadingTitle")}
                    description={t("tenant.dashboard.sectionLoadingDescription")}
                  />
                ) : state.error ? (
                  <DashboardStateCard
                    title={t("tenant.dashboard.loadFailed")}
                    description={state.error}
                    action={(
                      <Button size="sm" onClick={() => void loadSection(section)}>
                        {t("tenant.dashboard.retry")}
                      </Button>
                    )}
                  />
                ) : isEmpty ? (
                  <DashboardStateCard
                    title={t("tenant.dashboard.emptyTitle")}
                    description={t("tenant.dashboard.emptyDescription")}
                    action={workspacePath ? (
                      <Button size="sm" variant="outline" asChild>
                        <Link to={workspacePath}>{t("tenant.dashboard.openWorkspace")}</Link>
                      </Button>
                    ) : undefined}
                  />
                ) : state.data ? (
                  <div className="space-y-4">
                    <DashboardWidgets data={state.data} />

                    {!hasWidgets && state.data.stats.length > 0 ? (
                      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                        {state.data.stats.map((stat) => (
                          <Card key={stat.id}>
                            <CardHeader>
                              <CardDescription>{stat.label}</CardDescription>
                              <CardTitle>{stat.value}</CardTitle>
                            </CardHeader>
                            {stat.hint ? (
                              <CardContent className="pt-0 text-muted-foreground">{stat.hint}</CardContent>
                            ) : null}
                          </Card>
                        ))}
                      </div>
                    ) : null}

                    {state.data.highlights.length > 0 ? (
                      <Card>
                        <CardHeader>
                          <CardTitle>{t("tenant.dashboard.highlightsTitle")}</CardTitle>
                        </CardHeader>
                        <CardContent>
                          <ul className="space-y-3 text-sm">
                            {state.data.highlights.map((highlight) => (
                              <li key={highlight.id} className="space-y-1">
                                <p className="font-medium">{highlight.title}</p>
                                <p className="text-muted-foreground">{highlight.description}</p>
                              </li>
                            ))}
                          </ul>
                        </CardContent>
                      </Card>
                    ) : null}

                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <p className="text-xs text-muted-foreground">
                        {t("tenant.dashboard.generatedAt", {
                          value: formatGeneratedAt(state.data.generatedAt),
                        })}
                      </p>
                      {workspacePath ? (
                        <Button size="sm" variant="outline" asChild>
                          <Link to={workspacePath}>{t("tenant.dashboard.openWorkspace")}</Link>
                        </Button>
                      ) : null}
                    </div>
                  </div>
                ) : null}
              </TabsContent>
            );
          })}
        </Tabs>
      )}
    </div>
  );
};

export default TenantDashboardPage;
