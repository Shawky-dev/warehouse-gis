import type { ReactNode } from "react";
import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Button } from "@/shared/components/ui/button";
import { Badge } from "@/shared/components/ui/badge";
import { cn } from "@/lib/utils";
import type { WarehouseAncestorState } from "@/features/tenant/types/f1";

export type FilterActive = "all" | "active" | "inactive";

export function toActiveParam(filter: FilterActive): boolean | undefined {
  if (filter === "active") return true;
  if (filter === "inactive") return false;
  return undefined;
}

export type WarehouseBreadcrumbItem = {
  label: string;
  to?: string;
};

type WarehousePageShellProps = {
  title: string;
  description: string;
  breadcrumbs?: WarehouseBreadcrumbItem[];
  filterTitle: string;
  filters: ReactNode;
  listTitle: string;
  listDescription?: string;
  children: ReactNode;
};

export function WarehousePageShell({
  title,
  description,
  breadcrumbs = [],
  filterTitle,
  filters,
  listTitle,
  listDescription,
  children,
}: WarehousePageShellProps) {
  return (
    <div className="space-y-4">
      <div className="space-y-2">
        {breadcrumbs.length > 0 ? <WarehouseBreadcrumbs items={breadcrumbs} /> : null}
        <div className="space-y-1">
          <h1 className="text-xl font-semibold">{title}</h1>
          <p className="text-sm text-muted-foreground">{description}</p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{filterTitle}</CardTitle>
        </CardHeader>
        <CardContent>{filters}</CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{listTitle}</CardTitle>
          {listDescription ? <CardDescription>{listDescription}</CardDescription> : null}
        </CardHeader>
        <CardContent>{children}</CardContent>
      </Card>
    </div>
  );
}

export function WarehouseBreadcrumbs({ items }: { items: WarehouseBreadcrumbItem[] }) {
  return (
    <nav className="flex flex-wrap items-center gap-1 text-xs text-muted-foreground" aria-label="Breadcrumb">
      {items.map((item, index) => (
        <div key={`${item.label}-${index}`} className="flex items-center gap-1">
          {item.to ? (
            <Link className="transition-colors hover:text-foreground" to={item.to}>
              {item.label}
            </Link>
          ) : (
            <span>{item.label}</span>
          )}
          {index + 1 < items.length ? <ChevronRight className="h-3 w-3" /> : null}
        </div>
      ))}
    </nav>
  );
}

export function WarehouseStatusBadge({ active, activeLabel, inactiveLabel }: {
  active: boolean;
  activeLabel: string;
  inactiveLabel: string;
}) {
  return (
    <Badge
      variant="outline"
      className={cn(
        "rounded-none",
        active ? "border-emerald-600/40 text-emerald-700" : "text-muted-foreground"
      )}
    >
      {active ? activeLabel : inactiveLabel}
    </Badge>
  );
}

export function WarehousePagination({
  page,
  totalPages,
  onPrevious,
  onNext,
  previousLabel,
  nextLabel,
  infoLabel,
}: {
  page: number;
  totalPages: number;
  onPrevious: () => void;
  onNext: () => void;
  previousLabel: string;
  nextLabel: string;
  infoLabel: string;
}) {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <div className="mt-4 flex items-center gap-2">
      <Button size="sm" variant="outline" disabled={page === 0} onClick={onPrevious}>
        {previousLabel}
      </Button>
      <span className="text-sm text-muted-foreground">{infoLabel}</span>
      <Button size="sm" variant="outline" disabled={page + 1 >= totalPages} onClick={onNext}>
        {nextLabel}
      </Button>
    </div>
  );
}

export function emptyValue(value: string | null | undefined): string {
  return value && value.trim() ? value : "—";
}

export function sideLabel(side: string | null | undefined): string {
  return side ? side.toUpperCase() : "—";
}

export function levelLabel(levelNum: number | null | undefined): string {
  return levelNum ? `L${levelNum}` : "Level";
}

export function buildAncestorState(
  base: WarehouseAncestorState | undefined,
  next: Partial<WarehouseAncestorState>
): WarehouseAncestorState {
  return {
    ...base,
    ...next,
  };
}
