package com.warehouse.warehouse_platform.tenant.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    /** Stock per (location, product), excluding zeroed-out pairs. */
    @Query(value = """
            SELECT location_id   AS locationId,
                   product_id    AS productId,
                     lot_number    AS lotNumber,
                     qty_stock     AS qtyStock
            FROM   v_stock
            """, nativeQuery = true)
    List<StockEntry> findAllStock();

    /** Stock for a single location. */
    @Query(value = """
            SELECT location_id   AS locationId,
                   product_id    AS productId,
                                                                         lot_number    AS lotNumber,
                                                                         qty_stock     AS qtyStock
            FROM   v_stock
            WHERE  location_id = :locationId
            """, nativeQuery = true)
    List<StockEntry> findStockByLocation(@Param("locationId") UUID locationId);

    /** Stock for a single product across all locations. */
    @Query(value = """
            SELECT location_id   AS locationId,
                   product_id    AS productId,
                                                                         lot_number    AS lotNumber,
                                                                         qty_stock     AS qtyStock
            FROM   v_stock
            WHERE  product_id = :productId
            """, nativeQuery = true)
    List<StockEntry> findStockByProduct(@Param("productId") UUID productId);

    @Query(value = """
            SELECT location_id   AS locationId,
                   product_id    AS productId,
                                                                         lot_number    AS lotNumber,
                                                                         qty_stock     AS qtyStock
            FROM   v_stock
            WHERE  location_id = :locationId
              AND  product_id = :productId
            """, nativeQuery = true)
    List<StockEntry> findStockByLocationAndProduct(
            @Param("locationId") UUID locationId,
            @Param("productId") UUID productId);

    @Query(value = """
            SELECT COALESCE(SUM(qty_stock), 0)
            FROM   v_stock
            WHERE  location_id = :locationId
              AND  product_id = :productId
            """, nativeQuery = true)
    Optional<BigDecimal> findStockQtyByLocationAndProduct(
            @Param("locationId") UUID locationId,
            @Param("productId") UUID productId);

    @Query(value = """
            SELECT qty_stock
            FROM   v_stock
            WHERE  location_id = :locationId
              AND  product_id = :productId
              AND  ((:lotNumber IS NULL AND lot_number IS NULL)
                    OR lot_number = :lotNumber)
            """, nativeQuery = true)
    Optional<BigDecimal> findStockQtyByLocationProductAndLot(
            @Param("locationId") UUID locationId,
            @Param("productId") UUID productId,
            @Param("lotNumber") String lotNumber);

    /** Movement history for a location, newest first. */
    Page<StockMovement> findByLocationIdOrderByCreatedAtDesc(UUID locationId, Pageable pageable);

    /** Movement history for a product across all locations, newest first. */
    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    /** Movement history for a location/product pair, newest first. */
    Page<StockMovement> findByLocationIdAndProductIdOrderByCreatedAtDesc(
            UUID locationId,
            UUID productId,
            Pageable pageable);

    /** All movements, newest first. */
    Page<StockMovement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** All rows sharing a transfer reference (the two legs of a transfer). */
    List<StockMovement> findByReferenceId(UUID referenceId);

    List<StockMovement> findByReferenceIdIn(List<UUID> referenceIds);
}
