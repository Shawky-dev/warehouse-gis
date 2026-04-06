package com.warehouse.warehouse_platform.tenant.gis.service;

import com.warehouse.warehouse_platform.tenant.category.ProductCategory;
import com.warehouse.warehouse_platform.tenant.gis.StorageRuleViolationException;
import com.warehouse.warehouse_platform.tenant.gis.model.GisHazardBuffer;
import com.warehouse.warehouse_platform.tenant.gis.model.GisZone;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisBlockRepository;
import com.warehouse.warehouse_platform.tenant.hazardtype.HazardType;
import com.warehouse.warehouse_platform.tenant.product.Product;
import com.warehouse.warehouse_platform.tenant.zonetype.ZoneType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates all storage-rule validation in order:
 * <ol>
 * <li>Hazard-buffer check (BLOCK, never overrideable)</li>
 * <li>Zone category-rule check (delegates to
 * {@link GisZoneValidationService})</li>
 * <li>Required-zone check (WARN, only runs when steps 1–2 produce no
 * violation)</li>
 * </ol>
 */
@Service
public class InventoryValidationService {

    private final GisBlockRepository gisBlockRepository;
    private final GisZoneValidationService gisZoneValidationService;
    private final GeometryService geometryService;

    public InventoryValidationService(
            GisBlockRepository gisBlockRepository,
            GisZoneValidationService gisZoneValidationService,
            GeometryService geometryService) {
        this.gisBlockRepository = gisBlockRepository;
        this.gisZoneValidationService = gisZoneValidationService;
        this.geometryService = geometryService;
    }

    /**
     * Validates that the given product may be stored at {@code locationId}.
     *
     * @param locationId   the layout-block UUID of the target location
     * @param product      the product being stored (must have category and
     *                     hazardType loaded)
     * @param zoneOverride {@code true} when the caller has supplied
     *                     {@code X-Zone-Override: true}
     * @throws StorageRuleViolationException on any rule violation
     */
    @Transactional(readOnly = true)
    public void assertLocationAllowsProduct(UUID locationId, Product product, boolean zoneOverride) {
        // No GIS block → no spatial context, skip all checks (graceful degrade).
        if (gisBlockRepository.findByLayoutBlockId(locationId).isEmpty())
            return;

        // 1. Hazard-buffer check.
        assertNoHazardBufferViolation(locationId, product);

        // 2. Zone category-rule check (may throw GisZoneViolationException or
        // StorageRuleViolationException from within GisZoneValidationService).
        UUID categoryId = product.getCategory() != null ? product.getCategory().getId() : null;
        gisZoneValidationService.assertLocationAllowsProduct(locationId, categoryId, zoneOverride);

        // 3. Required-zone check (only reached if no prior violation).
        assertRequiredZoneSatisfied(locationId, product);
    }

    // ── Step 1: hazard-buffer ─────────────────────────────────────────────

    private void assertNoHazardBufferViolation(UUID locationId, Product product) {
        HazardType hazardType = product.getHazardType();
        // NONE hazard type is never restricted by any buffer.
        if (hazardType == null || "NONE".equals(hazardType.getCode()))
            return;

        List<GisHazardBuffer> matchingBuffers = geometryService.findMatchingHazardBuffers(locationId,
                hazardType.getId());

        if (!matchingBuffers.isEmpty()) {
            GisHazardBuffer first = matchingBuffers.get(0);
            // Provide the restricted hazard types from the buffer's join collection.
            List<HazardType> restricted = new java.util.ArrayList<>(first.getRestrictedHazardTypes());
            throw StorageRuleViolationException.hazardBufferBlock(first, restricted);
        }
    }

    // ── Step 3: required-zone ─────────────────────────────────────────────

    private void assertRequiredZoneSatisfied(UUID locationId, Product product) {
        ProductCategory category = product.getCategory();
        if (category == null)
            return;

        ZoneType required = category.getRequiredZoneType();
        if (required == null)
            return;

        boolean satisfied = geometryService.isLocationWithinZoneType(locationId, required.getId());
        if (!satisfied) {
            List<GisZone> suggested = geometryService.findZonesByZoneType(required.getId());
            throw StorageRuleViolationException.requiredZoneWarn(required, suggested);
        }
    }
}
