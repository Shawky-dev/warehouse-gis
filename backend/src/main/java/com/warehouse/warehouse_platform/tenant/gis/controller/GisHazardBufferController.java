package com.warehouse.warehouse_platform.tenant.gis.controller;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.gis.service.HazardBufferService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/gis/hazard-buffers")
public class GisHazardBufferController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final HazardBufferService hazardBufferService;

    public GisHazardBufferController(
            TenantAccessPolicy tenantAccessPolicy,
            HazardBufferService hazardBufferService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.hazardBufferService = hazardBufferService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HAZARD_BUFFERS_VIEW)")
    public ResponseEntity<List<HazardBufferService.HazardBufferSummary>> listAll(
            @PathVariable String tenantSlug,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(hazardBufferService.listAll());
    }

    @GetMapping(value = "/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HAZARD_BUFFERS_VIEW)")
    public ResponseEntity<String> getGeoJson(
            @PathVariable String tenantSlug,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(hazardBufferService.buildGeoJsonExport());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HAZARD_BUFFERS_MANAGE)")
    public ResponseEntity<HazardBufferService.ImportResult> importGeoJson(
            @PathVariable String tenantSlug,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(hazardBufferService.importGeoJson(file, tenantSlug));
    }

    @DeleteMapping("/{bufferId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HAZARD_BUFFERS_MANAGE)")
    public ResponseEntity<Void> delete(
            @PathVariable String tenantSlug,
            @PathVariable UUID bufferId,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        hazardBufferService.delete(bufferId, tenantSlug);
        return ResponseEntity.noContent().build();
    }
}
