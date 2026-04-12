package com.warehouse.warehouse_platform.tenant.dashboard;

import com.warehouse.warehouse_platform.tenant.inventory.StockMovement;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface DashboardRepository extends Repository<StockMovement, UUID> {

    @Query(value = """
            WITH leaf_storage_locations AS (
                SELECT lb.id
                FROM layout_blocks lb
                JOIN warehouse_location_kinds wlk ON wlk.id = lb.location_kind_id
                WHERE LOWER(wlk.name) = 'storage'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM layout_blocks child
                      WHERE child.parent_id = lb.id
                  )
            ),
            occupied_storage_locations AS (
                SELECT DISTINCT vs.location_id
                FROM v_stock vs
                JOIN leaf_storage_locations lsl ON lsl.id = vs.location_id
            ),
            empty_zones AS (
                SELECT z.id
                FROM gis_zones z
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM gis_blocks gb
                    JOIN v_stock vs ON vs.location_id = gb.layout_block_id
                    WHERE ST_Contains(z.geometry, ST_PointOnSurface(gb.geometry))
                )
            )
            SELECT
                COALESCE((SELECT SUM(vs.qty_stock) FROM v_stock vs), 0) AS totalStockQty,
                (SELECT COUNT(*) FROM leaf_storage_locations) AS totalStorageLocations,
                (SELECT COUNT(*) FROM occupied_storage_locations) AS occupiedStorageLocations,
                (SELECT COUNT(*) FROM gis_zones) AS totalZones,
                (SELECT COUNT(*) FROM empty_zones) AS emptyZones
            """, nativeQuery = true)
    SpatialSummaryProjection fetchSpatialSummary();

    @Query(value = """
            SELECT
                z.id AS zoneId,
                z.name AS zoneName,
                COALESCE(SUM(vs.qty_stock), 0) AS qtyStock
            FROM gis_zones z
            LEFT JOIN gis_blocks gb
              ON ST_Contains(z.geometry, ST_PointOnSurface(gb.geometry))
            LEFT JOIN v_stock vs
              ON vs.location_id = gb.layout_block_id
            GROUP BY z.id, z.name
            HAVING COALESCE(SUM(vs.qty_stock), 0) > 0
            ORDER BY qtyStock DESC, z.name ASC
            LIMIT 5
            """, nativeQuery = true)
    List<TopZoneProjection> fetchTopZonesByStock();

    @Query(value = """
            SELECT
                (SELECT COUNT(*)
                 FROM receipt_documents
                 WHERE status = 'POSTED'
                   AND posted_at >= CURRENT_DATE) AS todayReceipts,
                (SELECT COUNT(*)
                 FROM dispatch_documents
                 WHERE status = 'POSTED'
                   AND posted_at >= CURRENT_DATE) AS todayDispatches,
                (SELECT COUNT(*)
                 FROM receipt_documents
                 WHERE status = 'DRAFT') AS draftReceipts,
                (SELECT COUNT(*)
                 FROM dispatch_documents
                 WHERE status = 'DRAFT') AS draftDispatches,
                (SELECT COUNT(*)
                 FROM count_sessions
                 WHERE status = 'OPEN') AS openCountSessions,
                (SELECT COUNT(*)
                 FROM count_sessions
                 WHERE status = 'OPEN'
                   AND created_at < NOW() - INTERVAL '2 days') AS staleCountSessions
            """, nativeQuery = true)
    InventoryOpsSummaryProjection fetchInventoryOpsSummary();

    @Query(value = """
            SELECT
                DATE(sm.created_at) AS bucketDate,
                sm.type AS movementType,
                COALESCE(SUM(ABS(sm.qty)), 0) AS movementQty
            FROM stock_movements sm
            WHERE sm.created_at >= :fromInclusive
            GROUP BY DATE(sm.created_at), sm.type
            ORDER BY DATE(sm.created_at) ASC, sm.type ASC
            """, nativeQuery = true)
    List<MovementVolumeProjection> fetchMovementVolume(@Param("fromInclusive") java.time.Instant fromInclusive);

    @Query(value = """
            SELECT
                p.id AS productId,
                p.name AS productName,
                COALESCE(SUM(ABS(sm.qty)), 0) AS movementQty
            FROM stock_movements sm
            JOIN products p ON p.id = sm.product_id
            WHERE sm.created_at >= :fromInclusive
            GROUP BY p.id, p.name
            ORDER BY movementQty DESC, p.name ASC
            LIMIT 10
            """, nativeQuery = true)
    List<TopProductProjection> fetchTopProductsByMovement(@Param("fromInclusive") java.time.Instant fromInclusive);

    @Query(value = """
            SELECT
                cs.id AS sessionId,
                cs.name AS sessionName,
                cs.created_at AS createdAt,
                DATE_PART('day', NOW() - cs.created_at) AS ageDays
            FROM count_sessions cs
            WHERE cs.status = 'OPEN'
            ORDER BY cs.created_at ASC
            LIMIT 5
            """, nativeQuery = true)
    List<OpenCountSessionProjection> fetchOpenCountSessions();

    @Query(value = """
             WITH live_lots AS (
                SELECT
                    vs.location_id,
                    vs.product_id,
                    vs.lot_number,
                    MIN(sm.expiry_date) AS expiry_date
                FROM v_stock vs
                JOIN stock_movements sm
                  ON sm.location_id = vs.location_id
                 AND sm.product_id = vs.product_id
                 AND ((sm.lot_number IS NULL AND vs.lot_number IS NULL) OR sm.lot_number = vs.lot_number)
                WHERE sm.expiry_date IS NOT NULL
                GROUP BY vs.location_id, vs.product_id, vs.lot_number
            )
            SELECT
                (
                    SELECT COUNT(DISTINCT CONCAT(vs.location_id::text, '|', vs.product_id::text, '|', COALESCE(vs.lot_number, '')))
                    FROM v_stock vs
                    JOIN products p ON p.id = vs.product_id
                    JOIN gis_blocks gb ON gb.layout_block_id = vs.location_id
                    JOIN gis_zones z ON ST_Contains(z.geometry, ST_PointOnSurface(gb.geometry))
                    WHERE EXISTS (
                            SELECT 1
                            FROM gis_zone_category_rules rule
                            WHERE rule.zone_id = z.id
                              AND rule.category_id = p.category_id
                              AND rule.rule_type = 'PROHIBITED'
                        )
                       OR (
                            EXISTS (
                                SELECT 1
                                FROM gis_zone_category_rules rule
                                WHERE rule.zone_id = z.id
                                  AND rule.rule_type = 'ALLOWED'
                            )
                            AND NOT EXISTS (
                                SELECT 1
                                FROM gis_zone_category_rules rule
                                WHERE rule.zone_id = z.id
                                  AND rule.rule_type = 'ALLOWED'
                                  AND rule.category_id = p.category_id
                            )
                       )
                ) AS zoneViolations,
                (
                    SELECT COUNT(DISTINCT CONCAT(vs.location_id::text, '|', vs.product_id::text, '|', COALESCE(vs.lot_number, '')))
                    FROM v_stock vs
                    JOIN products p ON p.id = vs.product_id
                    JOIN gis_blocks gb ON gb.layout_block_id = vs.location_id
                    JOIN gis_hazard_buffers hb ON ST_Intersects(hb.geometry, gb.geometry)
                    JOIN gis_hazard_buffer_restricted_hazard_types rht
                      ON rht.hazard_buffer_id = hb.id
                     AND rht.hazard_type_id = p.hazard_type_id
                ) AS hazardIntrusions,
                (
                    SELECT COUNT(*)
                    FROM live_lots ll
                    WHERE ll.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '14 days'
                ) AS expiringLots,
                (
                    SELECT COUNT(*)
                    FROM count_sessions cs
                    WHERE cs.status = 'OPEN'
                      AND cs.created_at < NOW() - INTERVAL '2 days'
                ) AS staleCountSessions,
                (
                    SELECT COUNT(DISTINCT p.id)
                    FROM products p
                    JOIN v_stock vs ON vs.product_id = p.id
                    WHERE p.active = FALSE
                ) AS inactiveProductsWithStock,
                (
                    SELECT COUNT(*)
                    FROM gis_zones z
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM gis_blocks gb
                        JOIN v_stock vs ON vs.location_id = gb.layout_block_id
                        WHERE ST_Contains(z.geometry, ST_PointOnSurface(gb.geometry))
                    )
                ) AS emptyZones
             """, nativeQuery = true)
    WarningSummaryProjection fetchWarningSummary();

    @Query(value = """
            SELECT
                vs.location_id AS locationId,
                COALESCE(lb.full_code, gb.label, vs.location_id::text) AS locationLabel,
                z.name AS zoneName,
                p.name AS productName,
                COALESCE(c.display_name, c.name) AS categoryName
            FROM v_stock vs
            JOIN products p ON p.id = vs.product_id
            JOIN product_categories c ON c.id = p.category_id
            JOIN gis_blocks gb ON gb.layout_block_id = vs.location_id
            LEFT JOIN layout_blocks lb ON lb.id = vs.location_id
            JOIN gis_zones z ON ST_Contains(z.geometry, ST_PointOnSurface(gb.geometry))
            WHERE EXISTS (
                    SELECT 1
                    FROM gis_zone_category_rules rule
                    WHERE rule.zone_id = z.id
                      AND rule.category_id = p.category_id
                      AND rule.rule_type = 'PROHIBITED'
                )
               OR (
                    EXISTS (
                        SELECT 1
                        FROM gis_zone_category_rules rule
                        WHERE rule.zone_id = z.id
                          AND rule.rule_type = 'ALLOWED'
                    )
                    AND NOT EXISTS (
                        SELECT 1
                        FROM gis_zone_category_rules rule
                        WHERE rule.zone_id = z.id
                          AND rule.rule_type = 'ALLOWED'
                          AND rule.category_id = p.category_id
                    )
               )
            ORDER BY z.name ASC, locationLabel ASC, productName ASC
            LIMIT 5
            """, nativeQuery = true)
    List<ZoneViolationDetailProjection> fetchZoneViolationDetails();

    @Query(value = """
            SELECT
                vs.location_id AS locationId,
                COALESCE(lb.full_code, gb.label, vs.location_id::text) AS locationLabel,
                hb.name AS bufferName,
                p.name AS productName,
                ht.display_name AS hazardName
            FROM v_stock vs
            JOIN products p ON p.id = vs.product_id
            JOIN hazard_types ht ON ht.id = p.hazard_type_id
            JOIN gis_blocks gb ON gb.layout_block_id = vs.location_id
            LEFT JOIN layout_blocks lb ON lb.id = vs.location_id
            JOIN gis_hazard_buffers hb ON ST_Intersects(hb.geometry, gb.geometry)
            JOIN gis_hazard_buffer_restricted_hazard_types rht
              ON rht.hazard_buffer_id = hb.id
             AND rht.hazard_type_id = p.hazard_type_id
            ORDER BY hb.name ASC, locationLabel ASC, productName ASC
            LIMIT 5
            """, nativeQuery = true)
    List<HazardIntrusionDetailProjection> fetchHazardIntrusionDetails();

    @Query(value = """
            WITH live_lots AS (
                SELECT
                    vs.location_id,
                    vs.product_id,
                    vs.lot_number,
                    MIN(sm.expiry_date) AS expiry_date
                FROM v_stock vs
                JOIN stock_movements sm
                  ON sm.location_id = vs.location_id
                 AND sm.product_id = vs.product_id
                 AND ((sm.lot_number IS NULL AND vs.lot_number IS NULL) OR sm.lot_number = vs.lot_number)
                WHERE sm.expiry_date IS NOT NULL
                GROUP BY vs.location_id, vs.product_id, vs.lot_number
            )
            SELECT
                ll.location_id AS locationId,
                COALESCE(lb.full_code, gb.label, ll.location_id::text) AS locationLabel,
                p.name AS productName,
                ll.lot_number AS lotNumber,
                ll.expiry_date AS expiryDate
            FROM live_lots ll
            JOIN products p ON p.id = ll.product_id
            LEFT JOIN layout_blocks lb ON lb.id = ll.location_id
            LEFT JOIN gis_blocks gb ON gb.layout_block_id = ll.location_id
            WHERE ll.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '14 days'
            ORDER BY ll.expiry_date ASC, productName ASC, locationLabel ASC
            LIMIT 5
            """, nativeQuery = true)
    List<ExpiringLotDetailProjection> fetchExpiringLotDetails();

    @Query(value = """
            SELECT
                cs.id AS sessionId,
                cs.name AS sessionName,
                cs.created_at AS createdAt,
                DATE_PART('day', NOW() - cs.created_at) AS ageDays
            FROM count_sessions cs
            WHERE cs.status = 'OPEN'
              AND cs.created_at < NOW() - INTERVAL '2 days'
            ORDER BY cs.created_at ASC
            LIMIT 5
            """, nativeQuery = true)
    List<StaleCountSessionDetailProjection> fetchStaleCountSessionDetails();

    @Query(value = """
            SELECT
                p.id AS productId,
                p.name AS productName,
                p.sku AS sku,
                COUNT(DISTINCT vs.location_id) AS locationCount,
                COALESCE(SUM(vs.qty_stock), 0) AS totalQty
            FROM products p
            JOIN v_stock vs ON vs.product_id = p.id
            WHERE p.active = FALSE
            GROUP BY p.id, p.name, p.sku
            ORDER BY totalQty DESC, productName ASC
            LIMIT 5
            """, nativeQuery = true)
    List<InactiveProductDetailProjection> fetchInactiveProductWithStockDetails();

    @Query(value = """
            SELECT
                z.id AS zoneId,
                z.name AS zoneName
            FROM gis_zones z
            WHERE NOT EXISTS (
                SELECT 1
                FROM gis_blocks gb
                JOIN v_stock vs ON vs.location_id = gb.layout_block_id
                WHERE ST_Contains(z.geometry, ST_PointOnSurface(gb.geometry))
            )
            ORDER BY z.name ASC
            LIMIT 5
            """, nativeQuery = true)
    List<EmptyZoneDetailProjection> fetchEmptyZoneDetails();

    @Query(value = """
             SELECT
                (SELECT COUNT(*) FROM products) AS totalProducts,
                (SELECT COUNT(*) FROM products WHERE active = TRUE) AS activeProducts,
                (SELECT COUNT(*) FROM products WHERE active = FALSE) AS inactiveProducts,
                (
                    SELECT COUNT(*)
                    FROM products p
                    LEFT JOIN product_suppliers ps ON ps.product_id = p.id
                    WHERE ps.product_id IS NULL
                ) AS productsWithoutSuppliers,
                (SELECT COUNT(*) FROM product_categories) AS totalCategories,
                (SELECT COUNT(*) FROM suppliers WHERE active = TRUE) AS activeSuppliers,
                (SELECT COUNT(*) FROM units_of_measure WHERE active = TRUE) AS activeUoms,
                (SELECT COUNT(DISTINCT base_uom_id) FROM products) AS usedUoms,
                (SELECT COUNT(*) FROM products WHERE track_lot = TRUE) AS lotTrackedProducts,
                (SELECT COUNT(*) FROM products WHERE track_expiry = TRUE) AS expiryTrackedProducts
            """, nativeQuery = true)
    MasterDataSummaryProjection fetchMasterDataSummary();

    @Query(value = """
            SELECT
                c.id AS categoryId,
                COALESCE(c.display_name, c.name) AS categoryName,
                COUNT(p.id) AS productCount
            FROM product_categories c
            LEFT JOIN products p ON p.category_id = c.id
            GROUP BY c.id, COALESCE(c.display_name, c.name)
            ORDER BY productCount DESC, categoryName ASC
            LIMIT 10
            """, nativeQuery = true)
    List<CategoryDistributionProjection> fetchCategoryDistribution();

    @Query(value = """
            SELECT
                p.id AS productId,
                p.name AS productName,
                p.sku AS sku
            FROM products p
            LEFT JOIN product_suppliers ps ON ps.product_id = p.id
            WHERE ps.product_id IS NULL
            ORDER BY p.name ASC, p.sku ASC
            LIMIT 10
            """, nativeQuery = true)
    List<ProductWithoutSupplierProjection> fetchProductsWithoutSuppliers();

    @Query(value = """
            SELECT
                u.id AS uomId,
                u.code AS code,
                u.name AS name
            FROM units_of_measure u
            LEFT JOIN products p ON p.base_uom_id = u.id
            WHERE p.id IS NULL
            ORDER BY u.name ASC, u.code ASC
            LIMIT 10
            """, nativeQuery = true)
    List<UnusedUomProjection> fetchUnusedUoms();

    @Query(value = """
            SELECT
                (SELECT COUNT(*) FROM audit_log WHERE occurred_at >= :dayAgo) AS events24h,
                (SELECT COUNT(*) FROM audit_log WHERE occurred_at >= :weekAgo) AS events7d,
                (SELECT COUNT(DISTINCT actor_email) FROM audit_log WHERE occurred_at >= :weekAgo) AS uniqueActors7d,
                (SELECT COUNT(DISTINCT entity_type) FROM audit_log WHERE occurred_at >= :weekAgo) AS entityTypes7d
            """, nativeQuery = true)
    ActivitySummaryProjection fetchActivitySummary(
            @Param("dayAgo") java.time.Instant dayAgo,
            @Param("weekAgo") java.time.Instant weekAgo);

    @Query(value = """
            SELECT
                action AS actionName,
                COUNT(*) AS eventCount
            FROM audit_log
            WHERE occurred_at >= :fromInclusive
            GROUP BY action
            ORDER BY eventCount DESC, actionName ASC
            LIMIT 10
            """, nativeQuery = true)
    List<ActionBreakdownProjection> fetchActionBreakdown(@Param("fromInclusive") java.time.Instant fromInclusive);

    @Query(value = """
            SELECT
                actor_email AS actorEmail,
                COUNT(*) AS eventCount
            FROM audit_log
            WHERE occurred_at >= :fromInclusive
            GROUP BY actor_email
            ORDER BY eventCount DESC, actorEmail ASC
            LIMIT 10
            """, nativeQuery = true)
    List<ActorActivityProjection> fetchTopActors(@Param("fromInclusive") java.time.Instant fromInclusive);

    @Query(value = """
            SELECT
                id AS eventId,
                occurred_at AS occurredAt,
                actor_email AS actorEmail,
                action AS actionName,
                entity_type AS entityType,
                entity_id AS entityId,
                request_method AS requestMethod,
                request_path AS requestPath
            FROM audit_log
            ORDER BY occurred_at DESC
            LIMIT 10
            """, nativeQuery = true)
    List<RecentAuditEventProjection> fetchRecentAuditEvents();

    interface SpatialSummaryProjection {
        BigDecimal getTotalStockQty();
        long getTotalStorageLocations();
        long getOccupiedStorageLocations();
        long getTotalZones();
        long getEmptyZones();
    }

    interface TopZoneProjection {
        UUID getZoneId();
        String getZoneName();
        BigDecimal getQtyStock();
    }

    interface InventoryOpsSummaryProjection {
        long getTodayReceipts();
        long getTodayDispatches();
        long getDraftReceipts();
        long getDraftDispatches();
        long getOpenCountSessions();
        long getStaleCountSessions();
    }

    interface MovementVolumeProjection {
        LocalDate getBucketDate();
        String getMovementType();
        BigDecimal getMovementQty();
    }

    interface TopProductProjection {
        UUID getProductId();
        String getProductName();
        BigDecimal getMovementQty();
    }

    interface OpenCountSessionProjection {
        UUID getSessionId();
        String getSessionName();
        java.time.Instant getCreatedAt();
        BigDecimal getAgeDays();
    }

    interface WarningSummaryProjection {
        long getZoneViolations();
        long getHazardIntrusions();
        long getExpiringLots();
        long getStaleCountSessions();
        long getInactiveProductsWithStock();
        long getEmptyZones();
    }

    interface ZoneViolationDetailProjection {
        UUID getLocationId();
        String getLocationLabel();
        String getZoneName();
        String getProductName();
        String getCategoryName();
    }

    interface HazardIntrusionDetailProjection {
        UUID getLocationId();
        String getLocationLabel();
        String getBufferName();
        String getProductName();
        String getHazardName();
    }

    interface ExpiringLotDetailProjection {
        UUID getLocationId();
        String getLocationLabel();
        String getProductName();
        String getLotNumber();
        LocalDate getExpiryDate();
    }

    interface StaleCountSessionDetailProjection {
        UUID getSessionId();
        String getSessionName();
        java.time.Instant getCreatedAt();
        BigDecimal getAgeDays();
    }

    interface InactiveProductDetailProjection {
        UUID getProductId();
        String getProductName();
        String getSku();
        long getLocationCount();
        BigDecimal getTotalQty();
    }

    interface EmptyZoneDetailProjection {
        UUID getZoneId();
        String getZoneName();
    }

    interface MasterDataSummaryProjection {
        long getTotalProducts();
        long getActiveProducts();
        long getInactiveProducts();
        long getProductsWithoutSuppliers();
        long getTotalCategories();
        long getActiveSuppliers();
        long getActiveUoms();
        long getUsedUoms();
        long getLotTrackedProducts();
        long getExpiryTrackedProducts();
    }

    interface CategoryDistributionProjection {
        UUID getCategoryId();
        String getCategoryName();
        long getProductCount();
    }

    interface ProductWithoutSupplierProjection {
        UUID getProductId();
        String getProductName();
        String getSku();
    }

    interface UnusedUomProjection {
        UUID getUomId();
        String getCode();
        String getName();
    }

    interface ActivitySummaryProjection {
        long getEvents24h();
        long getEvents7d();
        long getUniqueActors7d();
        long getEntityTypes7d();
    }

    interface ActionBreakdownProjection {
        String getActionName();
        long getEventCount();
    }

    interface ActorActivityProjection {
        String getActorEmail();
        long getEventCount();
    }

    interface RecentAuditEventProjection {
        UUID getEventId();
        java.time.Instant getOccurredAt();
        String getActorEmail();
        String getActionName();
        String getEntityType();
        String getEntityId();
        String getRequestMethod();
        String getRequestPath();
    }
}
