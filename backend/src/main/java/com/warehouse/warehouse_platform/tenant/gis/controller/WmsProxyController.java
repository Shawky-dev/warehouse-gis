package com.warehouse.warehouse_platform.tenant.gis.controller;

import com.warehouse.warehouse_platform.multi_tenancy.geoserver.GeoServerProperties;
import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.gis.GisException;
import com.warehouse.warehouse_platform.tenant.gis.model.PublishStatus;
import com.warehouse.warehouse_platform.tenant.gis.repository.StaticHeatmapRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/{tenantSlug}/gis/wms")
public class WmsProxyController {

    private static final Logger log = LoggerFactory.getLogger(WmsProxyController.class);

    private static final Set<String> ALLOWED_REQUESTS = Set.of("GETMAP", "GETCAPABILITIES", "GETLEGENDGRAPHIC");

    private final TenantAccessPolicy tenantAccessPolicy;
    private final StaticHeatmapRepository heatmapRepository;
    private final GeoServerProperties geoServerProperties;
    private final RestTemplate geoServerRestTemplate;

    public WmsProxyController(
            TenantAccessPolicy tenantAccessPolicy,
            StaticHeatmapRepository heatmapRepository,
            GeoServerProperties geoServerProperties,
            @Qualifier("geoServerRestTemplate") RestTemplate geoServerRestTemplate) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.heatmapRepository = heatmapRepository;
        this.geoServerProperties = geoServerProperties;
        this.geoServerRestTemplate = geoServerRestTemplate;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_HEATMAPS_VIEW)")
    public ResponseEntity<byte[]> proxy(
            @PathVariable String tenantSlug,
            @RequestParam Map<String, String> rawParams,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);

        // Normalize parameter keys to uppercase for case-insensitive access.
        Map<String, String> params = new HashMap<>();
        rawParams.forEach((k, v) -> params.put(k.toUpperCase(), v));

        // Validate SERVICE=WMS.
        String service = params.get("SERVICE");
        if (!"WMS".equalsIgnoreCase(service)) {
            throw GisException.badRequest("Only SERVICE=WMS is allowed through this proxy");
        }

        // Validate REQUEST type.
        String requestType = params.getOrDefault("REQUEST", "");
        if (!ALLOWED_REQUESTS.contains(requestType.toUpperCase())) {
            throw GisException.badRequest(
                    "REQUEST '%s' is not allowed. Permitted: GetMap, GetCapabilities, GetLegendGraphic"
                            .formatted(requestType));
        }

        String workspace = "wh_" + tenantSlug;

        // For GetMap, validate LAYERS and rewrite with workspace prefix.
        if ("GETMAP".equalsIgnoreCase(requestType)) {
            String layers = params.get("LAYERS");
            if (layers != null && !layers.isBlank()) {
                String rewritten = rewriteAndValidateLayers(layers, workspace);
                params.put("LAYERS", rewritten);
            }
        }

        // For GetLegendGraphic, validate single LAYER param.
        if ("GETLEGENDGRAPHIC".equalsIgnoreCase(requestType)) {
            String layer = params.get("LAYER");
            if (layer != null && !layer.isBlank()) {
                String rewritten = rewriteAndValidateSingleLayer(layer, workspace);
                params.put("LAYER", rewritten);
            }
        }

        // Build GeoServer WMS URL.
        String queryString = buildQueryString(params);
        String geoServerUrl = geoServerProperties.url() + "/" + workspace + "/wms?" + queryString;

        // Forward request to GeoServer.
        try {
            var response = geoServerRestTemplate.getForEntity(geoServerUrl, byte[].class);
            HttpHeaders responseHeaders = new HttpHeaders();
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType != null) {
                responseHeaders.setContentType(contentType);
            }
            return ResponseEntity.status(response.getStatusCode())
                    .headers(responseHeaders)
                    .body(response.getBody());
        } catch (RestClientResponseException e) {
            log.warn("GeoServer WMS proxy failed for tenant={} [{}]: {}", tenantSlug, e.getStatusCode(),
                    e.getMessage());
            throw GisException.badRequest("GeoServer WMS request failed: " + e.getMessage());
        }
    }

    // ─── Layer validation helpers ─────────────────────────────────────────────

    /**
     * Validates a comma-separated LAYERS parameter against ACTIVE heatmap records,
     * and rewrites each layer name to be workspace-qualified.
     */
    private String rewriteAndValidateLayers(String rawLayers, String workspace) {
        String[] layerTokens = rawLayers.split(",");
        StringBuilder rewritten = new StringBuilder();
        for (int i = 0; i < layerTokens.length; i++) {
            String layerName = extractBareLayerName(layerTokens[i].trim(), workspace);
            validateLayerIsActive(layerName);
            if (i > 0) {
                rewritten.append(",");
            }
            rewritten.append(workspace).append(":").append(layerName);
        }
        return rewritten.toString();
    }

    /**
     * Validates a single LAYER parameter against ACTIVE heatmap records,
     * and rewrites it to be workspace-qualified.
     */
    private String rewriteAndValidateSingleLayer(String rawLayer, String workspace) {
        String layerName = extractBareLayerName(rawLayer.trim(), workspace);
        validateLayerIsActive(layerName);
        return workspace + ":" + layerName;
    }

    /**
     * Strips workspace prefix from a layer token if present, returning the bare
     * layer name.
     * Accepts both "layerName" and "workspace:layerName" forms.
     */
    private String extractBareLayerName(String layerToken, String workspace) {
        if (layerToken.contains(":")) {
            String[] parts = layerToken.split(":", 2);
            if (!parts[0].equals(workspace)) {
                throw GisException.badRequest(
                        "Cross-tenant layer access is not allowed: '%s'".formatted(layerToken));
            }
            return parts[1];
        }
        return layerToken;
    }

    /**
     * Verifies that the bare layer name matches an ACTIVE row in
     * gis_static_heatmaps.
     * Throws 400 for unknown or orphaned layers.
     */
    private void validateLayerIsActive(String layerName) {
        boolean active = heatmapRepository
                .findByGeoserverLayerNameAndPublishStatus(layerName, PublishStatus.ACTIVE)
                .isPresent();
        if (!active) {
            throw GisException.badRequest(
                    "Layer '%s' is not a registered active heatmap layer".formatted(layerName));
        }
    }

    // ─── Query string builder ─────────────────────────────────────────────────

    private String buildQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (!sb.isEmpty()) {
                sb.append("&");
            }
            sb.append(encode(k)).append("=").append(encode(v));
        });
        return sb.toString();
    }

    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }
}
