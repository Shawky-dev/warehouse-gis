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

    /** On-hand stock per (location, product), excluding zeroed-out pairs. */
    @Query(value = """
            SELECT location_id   AS locationId,
                   product_id    AS productId,
                   qty_on_hand   AS qtyOnHand
            FROM   v_on_hand
            """, nativeQuery = true)
    List<OnHandEntry> findAllOnHand();

    /** On-hand stock for a single location. */
    @Query(value = """
            SELECT location_id   AS locationId,
                   product_id    AS productId,
                   qty_on_hand   AS qtyOnHand
            FROM   v_on_hand
            WHERE  location_id = :locationId
            """, nativeQuery = true)
    List<OnHandEntry> findOnHandByLocation(@Param("locationId") UUID locationId);

    /** On-hand stock for a single product across all locations. */
    @Query(value = """
            SELECT location_id   AS locationId,
                   product_id    AS productId,
                   qty_on_hand   AS qtyOnHand
            FROM   v_on_hand
            WHERE  product_id = :productId
            """, nativeQuery = true)
    List<OnHandEntry> findOnHandByProduct(@Param("productId") UUID productId);

    @Query(value = """
            SELECT qty_on_hand
            FROM   v_on_hand
            WHERE  location_id = :locationId
              AND  product_id = :productId
            """, nativeQuery = true)
    Optional<BigDecimal> findOnHandQtyByLocationAndProduct(
            @Param("locationId") UUID locationId,
            @Param("productId") UUID productId);

    /** Movement history for a location, newest first. */
    Page<StockMovement> findByLocationIdOrderByCreatedAtDesc(UUID locationId, Pageable pageable);

    /** Movement history for a product across all locations, newest first. */
    Page<StockMovement> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);

    /** All rows sharing a transfer reference (the two legs of a transfer). */
    List<StockMovement> findByReferenceId(UUID referenceId);
}
