package com.warehouse.warehouse_platform.tenant.gis.service;

import com.warehouse.warehouse_platform.tenant.gis.model.GisHazardBuffer;
import com.warehouse.warehouse_platform.tenant.gis.model.GisZone;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisHazardBufferRepository;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Spatial query facade. All geometry-based lookups flow through here so that
 * callers never depend on individual repository methods directly.
 */
@Service
@Transactional(readOnly = true)
public class GeometryService {

    private final GisHazardBufferRepository hazardBufferRepository;
    private final GisZoneRepository zoneRepository;

    public GeometryService(GisHazardBufferRepository hazardBufferRepository,
            GisZoneRepository zoneRepository) {
        this.hazardBufferRepository = hazardBufferRepository;
        this.zoneRepository = zoneRepository;
    }

    /**
     * Returns hazard buffers that intersect the location of {@code layoutBlockId}
     * AND restrict the given hazard type.
     */
    public List<GisHazardBuffer> findMatchingHazardBuffers(UUID layoutBlockId, UUID hazardTypeId) {
        return hazardBufferRepository.findMatchingBuffersForLocation(layoutBlockId, hazardTypeId);
    }

    /**
     * Returns all zones whose geometry spatially contains the centroid of the
     * given layout block.
     */
    public List<GisZone> findContainingZones(UUID layoutBlockId) {
        return zoneRepository.findZonesContainingLocation(layoutBlockId);
    }

    /**
     * Returns {@code true} when at least one active zone of the given type
     * spatially contains the layout block.
     */
    public boolean isLocationWithinZoneType(UUID layoutBlockId, UUID zoneTypeId) {
        return zoneRepository.existsZoneOfTypeContainingLocation(layoutBlockId, zoneTypeId);
    }

    /**
     * Returns all zones that have the given zone type.
     */
    public List<GisZone> findZonesByZoneType(UUID zoneTypeId) {
        return zoneRepository.findByZoneType_Id(zoneTypeId);
    }
}
