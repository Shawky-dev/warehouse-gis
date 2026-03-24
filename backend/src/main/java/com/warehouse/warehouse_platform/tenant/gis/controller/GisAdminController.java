package com.warehouse.warehouse_platform.tenant.gis.controller;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.gis.service.GeoServerProvisioningService;
import com.warehouse.warehouse_platform.tenant.gis.service.LayoutToGisConversionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/{tenantSlug}/gis")
public class GisAdminController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final LayoutToGisConversionService layoutToGisConversionService;
    private final GeoServerProvisioningService geoServerProvisioningService;

    public GisAdminController(
            TenantAccessPolicy tenantAccessPolicy,
            LayoutToGisConversionService layoutToGisConversionService,
            GeoServerProvisioningService geoServerProvisioningService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.layoutToGisConversionService = layoutToGisConversionService;
        this.geoServerProvisioningService = geoServerProvisioningService;
    }

    /**
     * Generates GIS shadow data from the active warehouse layout.
     *
     * <p>
     * Fails with conflict if GIS layout data already exists.
     *
     * Response: {"layersConverted": {"Zone": 2, "Aisle": 4, "Bay": 8, "Shelf": 28}}
     */
    @PostMapping("/layout/generate")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_LAYOUT_REGENERATE)")
    public ResponseEntity<Map<String, Object>> generateLayout(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        Map<String, Integer> layersConverted = layoutToGisConversionService.generateFromActiveLayout();
        geoServerProvisioningService.provisionTenantWorkspace(tenantSlug);
        return ResponseEntity.ok(Map.of("layersConverted", layersConverted));
    }

    /**
     * Overwrites existing GIS shadow data and GeoServer resources from the active
     * layout.
     *
     * <p>
     * Clears existing tenant GeoServer workspace resources first, then rebuilds
     * GIS data and publishes fresh layers/styles for the current active layout.
     */
    @PostMapping("/layout/update")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_LAYOUT_REGENERATE)")
    public ResponseEntity<Map<String, Object>> updateLayout(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        geoServerProvisioningService.clearTenantWorkspace(tenantSlug);
        Map<String, Integer> layersConverted = layoutToGisConversionService.updateFromActiveLayout();
        geoServerProvisioningService.provisionTenantWorkspace(tenantSlug);
        return ResponseEntity.ok(Map.of("layersConverted", layersConverted));
    }

    /**
     * Exports GeoJSON for blocks belonging to a specific template layer.
     * The layer parameter is a block template name (e.g. "Zone", "Aisle", "Shelf").
     * Returns an empty FeatureCollection if no data exists for that template name.
     */
    @GetMapping("/layout/geojson")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_LAYOUT_VIEW)")
    public ResponseEntity<String> exportGeoJson(
            @PathVariable String tenantSlug,
            @RequestParam String layer,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        String geoJson = layoutToGisConversionService.buildGeoJsonFeatureCollection(layer);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/geo+json"))
                .body(geoJson);
    }

}
