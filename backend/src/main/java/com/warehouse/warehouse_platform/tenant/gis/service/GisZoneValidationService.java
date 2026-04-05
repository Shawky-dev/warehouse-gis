package com.warehouse.warehouse_platform.tenant.gis.service;

import com.warehouse.warehouse_platform.tenant.gis.GisZoneViolationException;
import com.warehouse.warehouse_platform.tenant.gis.model.GisBlock;
import com.warehouse.warehouse_platform.tenant.gis.model.GisBufferZone;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisBlockRepository;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisBufferZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GisZoneValidationService {

    private final GisBlockRepository gisBlockRepository;
    private final GisBufferZoneRepository gisBufferZoneRepository;

    public GisZoneValidationService(
            GisBlockRepository gisBlockRepository,
            GisBufferZoneRepository gisBufferZoneRepository) {
        this.gisBlockRepository = gisBlockRepository;
        this.gisBufferZoneRepository = gisBufferZoneRepository;
    }

    /**
     * Validates that the given location's zone allows the given product category.
     * <p>
     * Degrades gracefully: if no GisBlock exists for the location, or if the
     * location is outside all drawn zones, the check is silently skipped.
     *
     * @throws GisZoneViolationException (HTTP 409) if the category is not allowed
     */
    @Transactional(readOnly = true)
    public void assertLocationAllowsProduct(UUID locationId, UUID categoryId) {
        if (categoryId == null) {
            return;
        }
        Optional<GisBlock> leafBlock = gisBlockRepository.findByLayoutBlockId(locationId);
        if (leafBlock.isEmpty()) {
            return;
        }

        Optional<GisBlock> containingZone = gisBlockRepository.findContainingZone(locationId);
        if (containingZone.isEmpty()) {
            return;
        }

        GisBlock zone = containingZone.get();
        UUID[] allowed = zone.getAllowedCategoryIds();
        if (allowed == null || allowed.length == 0) {
            return;
        }

        boolean isAllowed = Arrays.asList(allowed).contains(categoryId);
        if (!isAllowed) {
            List<GisBlock> suggested = gisBlockRepository.findZonesAllowingCategory(categoryId);
            throw GisZoneViolationException.categoryNotAllowed(zone, suggested);
        }
    }

    /**
     * Validates that the given location is not inside a restricted buffer zone.
     * <p>
     * Degrades gracefully: if no GisBlock exists for the location, the check is
     * silently skipped.
     *
     * @throws GisZoneViolationException (HTTP 403) if the location intersects a
     *                                   buffer zone
     */
    @Transactional(readOnly = true)
    public void assertNotInBufferZone(UUID locationId) {
        Optional<GisBlock> leafBlock = gisBlockRepository.findByLayoutBlockId(locationId);
        if (leafBlock.isEmpty()) {
            return;
        }

        List<GisBufferZone> hits = gisBufferZoneRepository.findIntersectingBufferZones(locationId);
        if (!hits.isEmpty()) {
            GisBufferZone first = hits.get(0);
            throw GisZoneViolationException.bufferZoneViolation(first.getLabel(), first.getMaterialType());
        }
    }
}
