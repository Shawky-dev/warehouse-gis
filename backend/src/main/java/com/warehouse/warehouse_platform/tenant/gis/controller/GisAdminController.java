package com.warehouse.warehouse_platform.tenant.gis.controller;

import com.warehouse.warehouse_platform.multi_tenancy.geoserver.GeoServerProperties;
import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.gis.service.GeoServerProvisioningService;
import com.warehouse.warehouse_platform.tenant.gis.service.LayoutToGisConversionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/{tenantSlug}/gis")
public class GisAdminController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final LayoutToGisConversionService layoutToGisConversionService;
    private final GeoServerProvisioningService geoServerProvisioningService;
    private final GeoServerProperties geoServerProperties;
    private final RestTemplate geoServerRestTemplate;

    public GisAdminController(
            TenantAccessPolicy tenantAccessPolicy,
            LayoutToGisConversionService layoutToGisConversionService,
            GeoServerProvisioningService geoServerProvisioningService,
            GeoServerProperties geoServerProperties,
            @Qualifier("geoServerRestTemplate") RestTemplate geoServerRestTemplate) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.layoutToGisConversionService = layoutToGisConversionService;
        this.geoServerProvisioningService = geoServerProvisioningService;
        this.geoServerProperties = geoServerProperties;
        this.geoServerRestTemplate = geoServerRestTemplate;
    }

    /**
     * Converts the active warehouse layout into GIS shadow data, then provisions
     * one GeoServer SQL View layer per distinct block template type found in the data.
     *
     * Response: {"layersConverted": {"Zone": 2, "Aisle": 4, "Bay": 8, "Shelf": 28}}
     */
    @PostMapping("/layout/regenerate")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_LAYOUT_REGENERATE)")
    public ResponseEntity<Map<String, Object>> regenerateLayout(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        Map<String, Integer> layersConverted = layoutToGisConversionService.convertActiveLayout();
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

    /**
     * Transparent proxy to GeoServer WMS/WFS for this tenant's workspace.
     * Strips /{tenantSlug}/gis/proxy/ prefix and forwards to:
     *   {geoserver.url}/wh_{tenantSlug}/{remainingPath}?{queryString}
     */
    @RequestMapping(value = "/proxy/**", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<byte[]> proxyToGeoServer(
            @PathVariable String tenantSlug,
            HttpServletRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);

        String uri = request.getRequestURI();
        String[] parts = uri.split("/gis/proxy/", 2);
        String remaining = parts.length > 1 ? parts[1] : "";

        String targetUrl = geoServerProperties.url() + "/wh_" + tenantSlug + "/" + remaining;
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            targetUrl += "?" + queryString;
        }

        byte[] body;
        try {
            body = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            body = new byte[0];
        }
        if (body.length == 0) body = null;

        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        ResponseEntity<byte[]> response = geoServerRestTemplate.exchange(
                targetUrl, method, new HttpEntity<>(body), byte[].class);

        org.springframework.http.HttpHeaders responseHeaders = new org.springframework.http.HttpHeaders();
        if (response.getHeaders().getContentType() != null) {
            responseHeaders.setContentType(response.getHeaders().getContentType());
        }
        return ResponseEntity.status(response.getStatusCode())
                .headers(responseHeaders)
                .body(response.getBody());
    }
}
