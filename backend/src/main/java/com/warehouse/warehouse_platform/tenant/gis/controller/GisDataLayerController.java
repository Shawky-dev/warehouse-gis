package com.warehouse.warehouse_platform.tenant.gis.controller;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.gis.service.GisDataLayerService;
import com.warehouse.warehouse_platform.tenant.gis.service.GisDataLayerService.ImageData;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/gis/data-layers")
public class GisDataLayerController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final GisDataLayerService dataLayerService;

    public GisDataLayerController(
            TenantAccessPolicy tenantAccessPolicy,
            GisDataLayerService dataLayerService) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.dataLayerService = dataLayerService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_DATA_LAYERS_VIEW)")
    public ResponseEntity<List<GisDataLayerService.DataLayerSummary>> listAll(
            @PathVariable String tenantSlug,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(dataLayerService.listAll());
    }

    @GetMapping("/{id}/image")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_DATA_LAYERS_VIEW)")
    public ResponseEntity<byte[]> getImage(
            @PathVariable String tenantSlug,
            @PathVariable UUID id,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        ImageData imageData = dataLayerService.serveImage(id, tenantSlug);
        return ResponseEntity.ok()
                .contentType(imageData.mediaType())
                .body(imageData.bytes());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_DATA_LAYERS_MANAGE)")
    public ResponseEntity<GisDataLayerService.DataLayerSummary> upload(
            @PathVariable String tenantSlug,
            @RequestParam("name") String name,
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(dataLayerService.upload(tenantSlug, name, file));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_DATA_LAYERS_MANAGE)")
    public ResponseEntity<GisDataLayerService.DataLayerSummary> rename(
            @PathVariable String tenantSlug,
            @PathVariable UUID id,
            @RequestParam("name") String name,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        return ResponseEntity.ok(dataLayerService.rename(id, name));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_DATA_LAYERS_MANAGE)")
    public ResponseEntity<Void> delete(
            @PathVariable String tenantSlug,
            @PathVariable UUID id,
            Authentication auth) {
        tenantAccessPolicy.assertTenantAccess(auth, tenantSlug);
        dataLayerService.delete(id, tenantSlug);
        return ResponseEntity.noContent().build();
    }
}
