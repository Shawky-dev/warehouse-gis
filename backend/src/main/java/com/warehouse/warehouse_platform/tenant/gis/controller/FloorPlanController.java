package com.warehouse.warehouse_platform.tenant.gis.controller;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.gis.config.WarehouseGisProperties;
import com.warehouse.warehouse_platform.tenant.gis.service.FloorPlanStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/{tenantSlug}/gis/floorplan")
public class FloorPlanController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final FloorPlanStorageService storageService;
    private final WarehouseGisProperties gisProperties;

    public FloorPlanController(
            TenantAccessPolicy tenantAccessPolicy,
            FloorPlanStorageService storageService,
            WarehouseGisProperties gisProperties) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.storageService = storageService;
        this.gisProperties = gisProperties;
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_FLOOR_PLAN_VIEW)")
    public ResponseEntity<Map<String, Object>> getConfig(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        boolean hasFloorPlan = storageService.exists(tenantSlug);
        return ResponseEntity.ok(Map.of(
                "anchorLon", gisProperties.getAnchorLon(),
                "anchorLat", gisProperties.getAnchorLat(),
                "widthMeters", gisProperties.getWidthMeters(),
                "lengthMeters", gisProperties.getLengthMeters(),
                "hasFloorPlan", hasFloorPlan
        ));
    }

    @GetMapping("/svg")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_FLOOR_PLAN_VIEW)")
    public ResponseEntity<Resource> getSvg(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return storageService.load(tenantSlug)
                .map(resource -> ResponseEntity.ok()
                        .contentType(MediaType.valueOf("image/svg+xml"))
                        .body(resource))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_FLOOR_PLAN_MANAGE)")
    public ResponseEntity<Map<String, Object>> upload(
            @PathVariable String tenantSlug,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);

        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase()
                : "";
        if (!originalName.endsWith(".svg")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only SVG files are accepted."));
        }

        storageService.store(tenantSlug, file);
        return ResponseEntity.ok(Map.of("uploaded", true));
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_FLOOR_PLAN_MANAGE)")
    public ResponseEntity<Void> delete(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        storageService.delete(tenantSlug);
        return ResponseEntity.noContent().build();
    }
}
