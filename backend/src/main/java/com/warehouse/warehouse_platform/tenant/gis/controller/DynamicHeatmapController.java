package com.warehouse.warehouse_platform.tenant.gis.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.gis.GisException;
import com.warehouse.warehouse_platform.tenant.gis.heatmap.HeatmapMetricRegistry;
import com.warehouse.warehouse_platform.tenant.gis.heatmap.HeatmapMetricStrategy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/{tenantSlug}/gis/heatmaps/dynamic")
public class DynamicHeatmapController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final HeatmapMetricRegistry registry;

    public DynamicHeatmapController(TenantAccessPolicy tenantAccessPolicy, HeatmapMetricRegistry registry) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.registry = registry;
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HEATMAPS_VIEW)")
    public ResponseEntity<List<MetricMetadataResponse>> listMetrics(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        List<MetricMetadataResponse> metrics = registry.list().stream()
                .map(MetricMetadataResponse::from)
                .toList();
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/points")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HEATMAPS_VIEW)")
    public ResponseEntity<Map<String, Object>> getPoints(
            @PathVariable String tenantSlug,
            @RequestParam("metric") String metricKey,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        HeatmapMetricStrategy strategy = registry.get(metricKey)
                .orElseThrow(() -> GisException.badRequest("Unknown metric key: '%s'".formatted(metricKey)));
        return ResponseEntity.ok(strategy.execute());
    }

    // ── Response DTO ──────────────────────────────────────────────────────────

    /**
     * Metric metadata response. The {@code unit} field is always present in the
     * JSON output
     * (serialized as {@code null} when not applicable) to give clients a stable
     * contract.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MetricMetadataResponse(
            String key,
            String label,
            String description,
            String unit) {

        public static MetricMetadataResponse from(HeatmapMetricStrategy s) {
            return new MetricMetadataResponse(s.key(), s.label(), s.description(), s.unit());
        }
    }
}
