import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { normalizeTenantSlug } from "@/features/auth/shared/scope";
import { useI18n } from "@/i18n";
import { extractF0ErrorMessage, listAuditLogs } from "@/features/tenant/api/f0Api";
import type { AuditLogItem } from "@/features/tenant/types/f0";
import { Button } from "@/shared/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/shared/components/ui/card";
import { Input } from "@/shared/components/ui/input";
import { Label } from "@/shared/components/ui/label";

export default function TenantAuditLogsPage() {
  const { t } = useI18n();
  const { tenantSlug } = useParams<{ tenantSlug: string }>();
  const slug = normalizeTenantSlug(tenantSlug ?? "");

  const [logs, setLogs] = useState<AuditLogItem[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);

  const [pendingActorEmail, setPendingActorEmail] = useState("");
  const [pendingAction, setPendingAction] = useState("");
  const [pendingEntityType, setPendingEntityType] = useState("");
  const [pendingEntityId, setPendingEntityId] = useState("");
  const [pendingFromDate, setPendingFromDate] = useState("");
  const [pendingToDate, setPendingToDate] = useState("");

  const [actorEmail, setActorEmail] = useState("");
  const [action, setAction] = useState("");
  const [entityType, setEntityType] = useState("");
  const [entityId, setEntityId] = useState("");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");

  const loadData = useCallback(
    async (
      pg: number,
      ae: string,
      act: string,
      et: string,
      eid: string,
      fd: string,
      td: string
    ) => {
      setIsLoading(true);
      setPageError(null);
      try {
        const result = await listAuditLogs(slug, {
          page: pg,
          size: 20,
          actorEmail: ae || undefined,
          action: act || undefined,
          entityType: et || undefined,
          entityId: eid || undefined,
          fromDate: fd || undefined,
          toDate: td || undefined,
        });
        setLogs(result.content);
        setTotalElements(result.totalElements);
        setTotalPages(result.totalPages);
        setPage(result.page);
      } catch (error) {
        setPageError(extractF0ErrorMessage(error) ?? t("audit.loadFailed"));
      } finally {
        setIsLoading(false);
      }
    },
    [slug, t]
  );

  useEffect(() => {
    void loadData(0, actorEmail, action, entityType, entityId, fromDate, toDate);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadData]);

  const applyFilters = () => {
    setActorEmail(pendingActorEmail);
    setAction(pendingAction);
    setEntityType(pendingEntityType);
    setEntityId(pendingEntityId);
    setFromDate(pendingFromDate);
    setToDate(pendingToDate);
    void loadData(
      0,
      pendingActorEmail,
      pendingAction,
      pendingEntityType,
      pendingEntityId,
      pendingFromDate,
      pendingToDate
    );
  };

  const clearFilters = () => {
    setPendingActorEmail("");
    setPendingAction("");
    setPendingEntityType("");
    setPendingEntityId("");
    setPendingFromDate("");
    setPendingToDate("");
    setActorEmail("");
    setAction("");
    setEntityType("");
    setEntityId("");
    setFromDate("");
    setToDate("");
    void loadData(0, "", "", "", "", "", "");
  };

  const formatDate = (iso: string) => {
    try {
      return new Date(iso).toLocaleString();
    } catch {
      return iso;
    }
  };

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold">{t("audit.pageTitle")}</h1>
        <p className="text-sm text-muted-foreground">{t("audit.pageDescription")}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("audit.filtersTitle")}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <div className="space-y-1">
              <Label htmlFor="audit-actor">{t("audit.actorEmailLabel")}</Label>
              <Input
                id="audit-actor"
                placeholder={t("audit.actorEmailPlaceholder")}
                value={pendingActorEmail}
                onChange={(e) => setPendingActorEmail(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="audit-action">{t("audit.actionLabel")}</Label>
              <Input
                id="audit-action"
                placeholder={t("audit.actionPlaceholder")}
                value={pendingAction}
                onChange={(e) => setPendingAction(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="audit-entity-type">{t("audit.entityTypeLabel")}</Label>
              <Input
                id="audit-entity-type"
                placeholder={t("audit.entityTypePlaceholder")}
                value={pendingEntityType}
                onChange={(e) => setPendingEntityType(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="audit-entity-id">{t("audit.entityIdLabel")}</Label>
              <Input
                id="audit-entity-id"
                placeholder={t("audit.entityIdPlaceholder")}
                value={pendingEntityId}
                onChange={(e) => setPendingEntityId(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="audit-from-date">{t("audit.fromDateLabel")}</Label>
              <Input
                id="audit-from-date"
                type="date"
                value={pendingFromDate}
                onChange={(e) => setPendingFromDate(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="audit-to-date">{t("audit.toDateLabel")}</Label>
              <Input
                id="audit-to-date"
                type="date"
                value={pendingToDate}
                onChange={(e) => setPendingToDate(e.target.value)}
              />
            </div>
          </div>
          <div className="mt-3 flex gap-2">
            <Button variant="outline" onClick={applyFilters}>
              {t("audit.applyFilters")}
            </Button>
            <Button variant="ghost" onClick={clearFilters}>
              {t("audit.clearFilters")}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("audit.listTitle")}</CardTitle>
          <CardDescription>
            {t("audit.listCount", { count: String(totalElements) })}
          </CardDescription>
        </CardHeader>
        <CardContent>
          {pageError ? <p className="mb-2 text-xs text-destructive">{pageError}</p> : null}
          {isLoading ? (
            <p className="text-sm text-muted-foreground">{t("audit.loading")}</p>
          ) : logs.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t("audit.empty")}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b">
                    <th className="py-2 pe-4 text-start font-medium">{t("audit.tableOccurredAt")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("audit.tableActor")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("audit.tableAction")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("audit.tableEntityType")}</th>
                    <th className="py-2 pe-4 text-start font-medium">{t("audit.tableEntityId")}</th>
                    <th className="py-2 text-start font-medium">{t("audit.tableMethod")}</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log) => (
                    <tr key={log.id} className="border-b last:border-0">
                      <td className="py-2 pe-4 text-muted-foreground">
                        {formatDate(log.occurredAt)}
                      </td>
                      <td className="py-2 pe-4">{log.actorEmail}</td>
                      <td className="py-2 pe-4 font-medium">{log.action}</td>
                      <td className="py-2 pe-4">{log.entityType}</td>
                      <td className="py-2 pe-4 font-mono text-xs text-muted-foreground">
                        {log.entityId}
                      </td>
                      <td className="py-2 text-muted-foreground">
                        {log.requestMethod ?? "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {totalPages > 1 && (
            <div className="mt-4 flex items-center gap-2">
              <Button
                size="sm"
                variant="outline"
                disabled={page === 0}
                onClick={() => {
                  const prev = page - 1;
                  setPage(prev);
                  void loadData(prev, actorEmail, action, entityType, entityId, fromDate, toDate);
                }}
              >
                {t("audit.paginationPrevious")}
              </Button>
              <span className="text-sm text-muted-foreground">
                {t("audit.paginationInfo", {
                  page: String(page + 1),
                  totalPages: String(totalPages),
                })}
              </span>
              <Button
                size="sm"
                variant="outline"
                disabled={page + 1 >= totalPages}
                onClick={() => {
                  const next = page + 1;
                  setPage(next);
                  void loadData(next, actorEmail, action, entityType, entityId, fromDate, toDate);
                }}
              >
                {t("audit.paginationNext")}
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
