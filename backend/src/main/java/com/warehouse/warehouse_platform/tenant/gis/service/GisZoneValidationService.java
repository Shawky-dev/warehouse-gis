package com.warehouse.warehouse_platform.tenant.gis.service;

import com.warehouse.warehouse_platform.tenant.gis.GisZoneViolationException;
import com.warehouse.warehouse_platform.tenant.gis.model.GisZone;
import com.warehouse.warehouse_platform.tenant.gis.model.GisZoneCategoryRule;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisBlockRepository;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisZoneCategoryRuleRepository;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GisZoneValidationService {

    private final GisBlockRepository gisBlockRepository;
    private final GisZoneRepository gisZoneRepository;
    private final GisZoneCategoryRuleRepository gisZoneCategoryRuleRepository;

    public GisZoneValidationService(
            GisBlockRepository gisBlockRepository,
            GisZoneRepository gisZoneRepository,
            GisZoneCategoryRuleRepository gisZoneCategoryRuleRepository) {
        this.gisBlockRepository = gisBlockRepository;
        this.gisZoneRepository = gisZoneRepository;
        this.gisZoneCategoryRuleRepository = gisZoneCategoryRuleRepository;
    }

    /**
     * Validates that the given location is allowed to hold the given product
     * category, based on the gis_zones that spatially contain the location.
     * <p>
     * Rules:
     * <ul>
     * <li>If no GIS block exists for the location → pass (graceful degrade)</li>
     * <li>If location is not inside any zone → pass (open area)</li>
     * <li>For each containing zone, if the category has a PROHIBITED rule →
     * violation</li>
     * <li>Violation action BLOCK → always throws</li>
     * <li>Violation action WARN|CONFIRM → throws unless the current HTTP request
     * carries the {@code X-Zone-Override: true} header</li>
     * </ul>
     *
     * @throws GisZoneViolationException (HTTP 409) on unoverridable violation
     */
    @Transactional(readOnly = true)
    public void assertLocationAllowsProduct(UUID locationId, UUID categoryId) {
        if (categoryId == null)
            return;

        if (gisBlockRepository.findByLayoutBlockId(locationId).isEmpty())
            return;

        List<GisZone> zones = gisZoneRepository.findZonesContainingLocation(locationId);
        if (zones.isEmpty())
            return;

        for (GisZone zone : zones) {
            Optional<GisZoneCategoryRule> rule = gisZoneCategoryRuleRepository.findByZoneIdAndCategoryId(zone.getId(),
                    categoryId);
            if (rule.isPresent() && "PROHIBITED".equals(rule.get().getRuleType())) {
                String action = zone.getViolationAction();
                if ("BLOCK".equals(action) || !isZoneOverrideRequested()) {
                    List<GisZone> suggested = findSuggestedZones(categoryId, zones.stream()
                            .map(GisZone::getId).toList());
                    throw GisZoneViolationException.categoryProhibited(zone, suggested);
                }
                // WARN or CONFIRM with override header present — allow through
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<GisZone> findSuggestedZones(UUID categoryId, List<UUID> excludeZoneIds) {
        return gisZoneCategoryRuleRepository
                .findByCategoryIdAndRuleType(categoryId, "ALLOWED")
                .stream()
                .map(GisZoneCategoryRule::getZone)
                .filter(z -> !excludeZoneIds.contains(z.getId()))
                .distinct()
                .limit(3)
                .toList();
    }

    private boolean isZoneOverrideRequested() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null)
                return false;
            String value = attrs.getRequest().getHeader("X-Zone-Override");
            return "true".equalsIgnoreCase(value);
        } catch (Exception e) {
            return false;
        }
    }
}
