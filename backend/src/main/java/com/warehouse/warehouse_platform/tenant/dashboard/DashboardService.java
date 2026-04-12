package com.warehouse.warehouse_platform.tenant.dashboard;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final List<String> MOVEMENT_TYPES = List.of("RECEIVE", "PICK", "TRANSFER_IN", "TRANSFER_OUT", "ADJUST");

    private final DashboardRepository dashboardRepository;

    public DashboardService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    public DashboardSectionResponse getSpatialKpis() {
        DashboardRepository.SpatialSummaryProjection summary = dashboardRepository.fetchSpatialSummary();
        long totalStorageLocations = summary.getTotalStorageLocations();
        long occupiedStorageLocations = summary.getOccupiedStorageLocations();
        long emptyStorageLocations = Math.max(totalStorageLocations - occupiedStorageLocations, 0);
        long occupancyPercent = totalStorageLocations == 0
                ? 0
                : Math.round((occupiedStorageLocations * 100.0f) / totalStorageLocations);

        return new DashboardSectionResponse(
                "spatial-kpis",
                List.of(
                        new DashboardStat("total-stock", "Total stock on hand", formatDecimal(summary.getTotalStockQty()), null),
                        new DashboardStat("occupied-storage", "Occupied storage locations", formatCount(occupiedStorageLocations), null),
                        new DashboardStat("empty-storage", "Empty storage locations", formatCount(emptyStorageLocations), null),
                        new DashboardStat("zones", "Mapped zones", formatCount(summary.getTotalZones()), null)),
                List.of(
                        new DashboardHighlight(
                                "occupancy-summary",
                                "Storage occupancy uses filled locations",
                                occupancyPercent + "% of leaf storage locations currently hold stock."),
                        new DashboardHighlight(
                                "empty-zones-summary",
                                "Empty zones detected",
                                formatCount(summary.getEmptyZones()) + " zones currently contain no stock-bearing locations.")),
                List.of(
                        new DashboardWidget(
                                "metric-grid",
                                "overview-metrics",
                                "Overview",
                                "Tenant-scoped inventory and warehouse footprint from current stock and mapped storage locations.",
                                List.of(
                                        new DashboardMetric("storage-occupancy", "Storage occupancy", occupancyPercent + "%", formatCount(occupiedStorageLocations) + " of " + formatCount(totalStorageLocations) + " storage locations in use."),
                                        new DashboardMetric("total-stock-widget", "Total stock on hand", formatDecimal(summary.getTotalStockQty()), "Summed from current tenant stock positions."),
                                        new DashboardMetric("empty-storage-widget", "Free storage locations", formatCount(emptyStorageLocations), "Leaf storage locations with no stock rows."),
                                        new DashboardMetric("empty-zones-widget", "Empty zones", formatCount(summary.getEmptyZones()), "Zones with no contained stock-bearing locations.")),
                                null,
                                null),
                        new DashboardWidget(
                                "bar-list",
                                "top-zones",
                                "Top 5 storage pressure zones",
                                "Ranked by current stock quantity inside each mapped zone.",
                                null,
                                dashboardRepository.fetchTopZonesByStock().stream()
                                        .map(zone -> new DashboardValueItem(
                                                "zone-" + zone.getZoneId(),
                                                zone.getZoneName(),
                                                formatDecimal(zone.getQtyStock()),
                                                zone.getQtyStock(),
                                                null,
                                                null,
                                                null))
                                        .toList(),
                                null)),
                Instant.now());
    }

    public DashboardSectionResponse getInventoryOps() {
        DashboardRepository.InventoryOpsSummaryProjection summary = dashboardRepository.fetchInventoryOpsSummary();
        Instant fromInclusive = LocalDate.now().minusDays(6).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        List<DashboardRepository.MovementVolumeProjection> movementVolume = dashboardRepository.fetchMovementVolume(fromInclusive);
        List<DashboardTimelineBucket> timeline = buildMovementTimeline(movementVolume);

        return new DashboardSectionResponse(
                "inventory-ops",
                List.of(
                        new DashboardStat("today-receipts", "Today's receipts", formatCount(summary.getTodayReceipts()), null),
                        new DashboardStat("today-dispatches", "Today's dispatches", formatCount(summary.getTodayDispatches()), null),
                        new DashboardStat("draft-receipts", "Draft receipts", formatCount(summary.getDraftReceipts()), null),
                        new DashboardStat("draft-dispatches", "Draft dispatches", formatCount(summary.getDraftDispatches()), null)),
                List.of(
                        new DashboardHighlight(
                                "open-count-sessions",
                                "Open count sessions",
                                formatCount(summary.getOpenCountSessions()) + " sessions open, " + formatCount(summary.getStaleCountSessions()) + " already stale."),
                        new DashboardHighlight(
                                "movement-window",
                                "Movement window",
                                "Movement widgets summarize the last 7 days of tenant ledger activity.")),
                List.of(
                        new DashboardWidget(
                                "metric-grid",
                                "inventory-ops-metrics",
                                "Inventory operations",
                                "Operational document and count-session snapshot for the current tenant.",
                                List.of(
                                        new DashboardMetric("ops-receipts", "Today's receipts", formatCount(summary.getTodayReceipts()), "Posted receipt documents since local midnight."),
                                        new DashboardMetric("ops-dispatches", "Today's dispatches", formatCount(summary.getTodayDispatches()), "Posted dispatch documents since local midnight."),
                                        new DashboardMetric("ops-drafts", "Pending drafts", formatCount(summary.getDraftReceipts() + summary.getDraftDispatches()), formatCount(summary.getDraftReceipts()) + " receipt drafts and " + formatCount(summary.getDraftDispatches()) + " dispatch drafts."),
                                        new DashboardMetric("ops-counts", "Open count sessions", formatCount(summary.getOpenCountSessions()), formatCount(summary.getStaleCountSessions()) + " stale open sessions older than two days.")),
                                null,
                                null),
                        new DashboardWidget(
                                "timeline",
                                "movement-volume",
                                "Movement volume (7 day)",
                                "Absolute quantity moved per day across receive, pick, transfer, and adjustment operations.",
                                null,
                                null,
                                timeline),
                        new DashboardWidget(
                                "bar-list",
                                "top-products",
                                "Top 10 products by movement",
                                "Products ranked by total absolute quantity moved during the last 7 days.",
                                null,
                                dashboardRepository.fetchTopProductsByMovement(fromInclusive).stream()
                                        .map(product -> new DashboardValueItem(
                                                "product-" + product.getProductId(),
                                                product.getProductName(),
                                                formatDecimal(product.getMovementQty()),
                                                product.getMovementQty(),
                                                null,
                                                null,
                                                null))
                                        .toList(),
                                null),
                        new DashboardWidget(
                                "alert-list",
                                "count-session-alerts",
                                "Open count sessions",
                                "Oldest open sessions so the team can close or progress them quickly.",
                                null,
                                dashboardRepository.fetchOpenCountSessions().stream()
                                        .map(session -> new DashboardValueItem(
                                                "count-session-" + session.getSessionId(),
                                                session.getSessionName(),
                                                formatCount(session.getAgeDays().longValue()) + " days open",
                                                session.getAgeDays(),
                                                "medium",
                                                "count-session",
                                                formatInstant(session.getCreatedAt())))
                                        .toList(),
                                null)),
                Instant.now());
    }

    public DashboardSectionResponse getWarnings() {
        return emptySection("warnings");
    }

    public DashboardSectionResponse getMasterData() {
        DashboardRepository.MasterDataSummaryProjection summary = dashboardRepository.fetchMasterDataSummary();
        long unusedUoms = Math.max(summary.getActiveUoms() - summary.getUsedUoms(), 0);

        return new DashboardSectionResponse(
                "master-data",
                List.of(
                        new DashboardStat("total-products", "Total products", formatCount(summary.getTotalProducts()), null),
                        new DashboardStat("active-products", "Active products", formatCount(summary.getActiveProducts()), null),
                        new DashboardStat("products-without-suppliers", "Products without suppliers", formatCount(summary.getProductsWithoutSuppliers()), null),
                        new DashboardStat("used-uoms", "UOMs in use", formatCount(summary.getUsedUoms()), null)),
                List.of(
                        new DashboardHighlight(
                                "master-data-shape",
                                "Master data health",
                                "This tab focuses on catalog completeness and reuse of categories, suppliers, and UOMs."),
                        new DashboardHighlight(
                                "supplier-gaps",
                                "Supplier linkage",
                                formatCount(summary.getProductsWithoutSuppliers()) + " products still have no supplier relationship.")),
                List.of(
                        new DashboardWidget(
                                "metric-grid",
                                "master-data-metrics",
                                "Catalog overview",
                                "Headline master-data health across products, suppliers, categories, and units of measure.",
                                List.of(
                                        new DashboardMetric("catalog-products", "Active products", formatCount(summary.getActiveProducts()), formatCount(summary.getInactiveProducts()) + " inactive products remain in the catalog."),
                                        new DashboardMetric("catalog-categories", "Categories", formatCount(summary.getTotalCategories()), "Categories currently assigned across the product catalog."),
                                        new DashboardMetric("catalog-suppliers", "Active suppliers", formatCount(summary.getActiveSuppliers()), formatCount(summary.getProductsWithoutSuppliers()) + " products still need supplier links."),
                                        new DashboardMetric("catalog-uoms", "UOM coverage", formatCount(summary.getUsedUoms()) + " / " + formatCount(summary.getActiveUoms()), formatCount(unusedUoms) + " active UOMs are not currently used by any product.")),
                                null,
                                null),
                        new DashboardWidget(
                                "bar-list",
                                "category-distribution",
                                "Category distribution",
                                "Top categories ranked by number of assigned products.",
                                null,
                                dashboardRepository.fetchCategoryDistribution().stream()
                                        .map(category -> new DashboardValueItem(
                                                "category-" + category.getCategoryId(),
                                                category.getCategoryName(),
                                                formatCount(category.getProductCount()),
                                                BigDecimal.valueOf(category.getProductCount()),
                                                null,
                                                "category",
                                                null))
                                        .toList(),
                                null),
                        new DashboardWidget(
                                "alert-list",
                                "master-data-gaps",
                                "Master data gaps",
                                "Products without suppliers and active UOMs that are not used anywhere yet.",
                                null,
                                buildMasterDataGapItems(
                                        dashboardRepository.fetchProductsWithoutSuppliers(),
                                        dashboardRepository.fetchUnusedUoms()),
                                null)),
                Instant.now());
    }

    public DashboardSectionResponse getActivity() {
        Instant now = Instant.now();
        Instant dayAgo = now.minus(java.time.Duration.ofDays(1));
        Instant weekAgo = now.minus(java.time.Duration.ofDays(7));
        DashboardRepository.ActivitySummaryProjection summary = dashboardRepository.fetchActivitySummary(dayAgo, weekAgo);

        return new DashboardSectionResponse(
                "activity",
                List.of(
                        new DashboardStat("events-24h", "Events (24h)", formatCount(summary.getEvents24h()), null),
                        new DashboardStat("events-7d", "Events (7d)", formatCount(summary.getEvents7d()), null),
                        new DashboardStat("actors-7d", "Active actors (7d)", formatCount(summary.getUniqueActors7d()), null),
                        new DashboardStat("entity-types-7d", "Entity types (7d)", formatCount(summary.getEntityTypes7d()), null)),
                List.of(
                        new DashboardHighlight(
                                "activity-window",
                                "Activity uses audit-log events",
                                "This tab summarizes tenant audit records instead of raw request counts."),
                        new DashboardHighlight(
                                "activity-feed",
                                "Recent feed is intentionally concise",
                                "Recent rows emphasize actor, action, entity, and request metadata without dumping JSON state.")),
                List.of(
                        new DashboardWidget(
                                "metric-grid",
                                "activity-metrics",
                                "Activity overview",
                                "Recent audit-log activity for the tenant across users, actions, and entity types.",
                                List.of(
                                        new DashboardMetric("activity-24h", "Events (24h)", formatCount(summary.getEvents24h()), "Audit records created during the last 24 hours."),
                                        new DashboardMetric("activity-7d", "Events (7d)", formatCount(summary.getEvents7d()), "Audit records created during the last week."),
                                        new DashboardMetric("activity-actors", "Active actors", formatCount(summary.getUniqueActors7d()), "Distinct actor emails recorded during the last week."),
                                        new DashboardMetric("activity-entities", "Entity types", formatCount(summary.getEntityTypes7d()), "Different entity types touched during the last week.")),
                                null,
                                null),
                        new DashboardWidget(
                                "bar-list",
                                "activity-actions",
                                "Actions by type (7 day)",
                                "Top audit action types recorded during the last week.",
                                null,
                                dashboardRepository.fetchActionBreakdown(weekAgo).stream()
                                        .map(action -> new DashboardValueItem(
                                                "action-" + action.getActionName(),
                                                action.getActionName(),
                                                formatCount(action.getEventCount()),
                                                BigDecimal.valueOf(action.getEventCount()),
                                                null,
                                                "action",
                                                null))
                                        .toList(),
                                null),
                        new DashboardWidget(
                                "bar-list",
                                "activity-actors",
                                "Most active users (7 day)",
                                "Actors who generated the most audit events during the last week.",
                                null,
                                dashboardRepository.fetchTopActors(weekAgo).stream()
                                        .map(actor -> new DashboardValueItem(
                                                "actor-" + actor.getActorEmail(),
                                                actor.getActorEmail(),
                                                formatCount(actor.getEventCount()),
                                                BigDecimal.valueOf(actor.getEventCount()),
                                                null,
                                                "actor",
                                                null))
                                        .toList(),
                                null),
                        new DashboardWidget(
                                "alert-list",
                                "recent-activity",
                                "Recent activity feed",
                                "Latest audit records in reverse chronological order.",
                                null,
                                dashboardRepository.fetchRecentAuditEvents().stream()
                                        .map(event -> new DashboardValueItem(
                                                "event-" + event.getEventId(),
                                                event.getActionName() + " " + event.getEntityType(),
                                                event.getActorEmail(),
                                                null,
                                                null,
                                                event.getEntityType().toLowerCase(),
                                                buildActivityHint(event)))
                                        .toList(),
                                null)),
                Instant.now());
    }

    private DashboardSectionResponse emptySection(String section) {
        return new DashboardSectionResponse(
                section,
                List.of(),
                List.of(),
                List.of(),
                Instant.now());
    }

    private List<DashboardTimelineBucket> buildMovementTimeline(List<DashboardRepository.MovementVolumeProjection> rows) {
        Map<LocalDate, Map<String, BigDecimal>> byDay = new java.util.LinkedHashMap<>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate day = LocalDate.now().minusDays(offset);
            byDay.put(day, new java.util.LinkedHashMap<>());
        }

        for (DashboardRepository.MovementVolumeProjection row : rows) {
            byDay.computeIfAbsent(row.getBucketDate(), ignored -> new java.util.LinkedHashMap<>())
                    .put(row.getMovementType(), safeDecimal(row.getMovementQty()));
        }

        return byDay.entrySet().stream()
                .map(entry -> new DashboardTimelineBucket(
                        entry.getKey().toString(),
                        MOVEMENT_TYPES.stream()
                                .map(type -> new DashboardSeriesValue(type, safeDecimal(entry.getValue().get(type))))
                                .toList()))
                .toList();
    }

    private List<DashboardValueItem> buildMasterDataGapItems(
            List<DashboardRepository.ProductWithoutSupplierProjection> productsWithoutSuppliers,
            List<DashboardRepository.UnusedUomProjection> unusedUoms) {
        List<DashboardValueItem> items = new java.util.ArrayList<>();
        for (DashboardRepository.ProductWithoutSupplierProjection product : productsWithoutSuppliers) {
            items.add(new DashboardValueItem(
                    "product-gap-" + product.getProductId(),
                    product.getProductName(),
                    product.getSku(),
                    null,
                    "medium",
                    "supplier-link",
                    "Product has no supplier relationship."));
        }
        for (DashboardRepository.UnusedUomProjection uom : unusedUoms) {
            items.add(new DashboardValueItem(
                    "uom-gap-" + uom.getUomId(),
                    uom.getName(),
                    uom.getCode(),
                    null,
                    "low",
                    "uom",
                    "Active unit of measure not used by any product."));
        }
        return items;
    }

    private String buildActivityHint(DashboardRepository.RecentAuditEventProjection event) {
        String method = event.getRequestMethod() == null || event.getRequestMethod().isBlank()
                ? "unknown"
                : event.getRequestMethod();
        String path = event.getRequestPath() == null || event.getRequestPath().isBlank()
                ? ""
                : " at " + event.getRequestPath();
        return formatInstant(event.getOccurredAt()) + " - " + method + path + " - entity " + event.getEntityId();
    }

    private String formatCount(long value) {
        return Long.toString(value);
    }

    private String formatDecimal(BigDecimal value) {
        return safeDecimal(value).stripTrailingZeros().toPlainString();
    }

    private String formatInstant(Instant value) {
        return value == null ? null : value.toString();
    }

    private BigDecimal safeDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record DashboardSectionResponse(
            String section,
            List<DashboardStat> stats,
            List<DashboardHighlight> highlights,
            List<DashboardWidget> widgets,
            Instant generatedAt) {
    }

    public record DashboardStat(
            String id,
            String label,
            String value,
            String hint) {
    }

    public record DashboardHighlight(
            String id,
            String title,
            String description) {
    }

    public record DashboardWidget(
            String type,
            String id,
            String title,
            String description,
            List<DashboardMetric> metrics,
            List<DashboardValueItem> items,
            List<DashboardTimelineBucket> timeline) {
    }

    public record DashboardMetric(
            String id,
            String label,
            String value,
            String hint) {
    }

    public record DashboardValueItem(
            String id,
            String label,
            String value,
            BigDecimal numericValue,
            String severity,
            String category,
            String hint) {
    }

    public record DashboardTimelineBucket(
            String label,
            List<DashboardSeriesValue> series) {
    }

    public record DashboardSeriesValue(
            String key,
            BigDecimal value) {
    }
}
