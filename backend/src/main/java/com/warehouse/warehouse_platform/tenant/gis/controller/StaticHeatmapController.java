package com.warehouse.warehouse_platform.tenant.gis.controller;

import com.warehouse.warehouse_platform.security.permissions.TenantPermissions;
import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.gis.model.StaticHeatmap;
import com.warehouse.warehouse_platform.tenant.gis.service.StaticHeatmapService;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/gis/heatmaps/static")
public class StaticHeatmapController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final StaticHeatmapService service;

    public StaticHeatmapController(TenantAccessPolicy tenantAccessPolicy, StaticHeatmapService service) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HEATMAPS_VIEW)")
    public ResponseEntity<List<StaticHeatmapResponse>> list(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        List<StaticHeatmapResponse> body = service.listActive().stream()
                .map(StaticHeatmapResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HEATMAPS_MANAGE)")
    public ResponseEntity<StaticHeatmapResponse> upload(
            @PathVariable String tenantSlug,
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        StaticHeatmap created = service.upload(tenantSlug, name, file, authentication.getName());
        return ResponseEntity.status(201).body(StaticHeatmapResponse.from(created));
    }

    @PutMapping("/{heatmapId}/default")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HEATMAPS_MANAGE)")
    public ResponseEntity<StaticHeatmapResponse> setDefault(
            @PathVariable String tenantSlug,
            @PathVariable UUID heatmapId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        StaticHeatmap updated = service.setDefault(heatmapId);
        return ResponseEntity.ok(StaticHeatmapResponse.from(updated));
    }

    @DeleteMapping("/{heatmapId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HEATMAPS_MANAGE)")
    public ResponseEntity<Void> delete(
            @PathVariable String tenantSlug,
            @PathVariable UUID heatmapId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        service.delete(tenantSlug, heatmapId);
        return ResponseEntity.noContent().build();
    }

    // ── Response DTO ──────────────────────────────────────────────────────────

    public record StaticHeatmapResponse(
            UUID id,
            String name,
            String sourceFilename,
            String geoserverLayerName,
            boolean isDefault,
            Instant createdAt,
            Instant updatedAt) {

        public static StaticHeatmapResponse from(StaticHeatmap h) {
            return new StaticHeatmapResponse(
                    h.getId(),
                    h.getName(),
                    h.getSourceFilename(),
                    h.getGeoserverLayerName(),
                    h.isDefault(),
                    h.getCreatedAt(),
                    h.getUpdatedAt());
        }
    }
}
