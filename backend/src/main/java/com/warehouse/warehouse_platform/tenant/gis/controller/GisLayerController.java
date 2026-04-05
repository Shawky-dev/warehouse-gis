package com.warehouse.warehouse_platform.tenant.gis.controller;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.gis.model.GisBlock;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisBlockRepository;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/{tenantSlug}/gis")
public class GisLayerController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final GisBlockRepository gisBlockRepository;

    public GisLayerController(
            TenantAccessPolicy tenantAccessPolicy,
            GisBlockRepository gisBlockRepository) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.gisBlockRepository = gisBlockRepository;
    }

    // ── Leaf location GeoJSON ─────────────────────────────────────────────────

    /**
     * Returns a GeoJSON FeatureCollection of all drawn leaf-level location
     * polygons.
     * Properties: locationId (= layoutBlockId), label, positionPath
     */
    @GetMapping(value = "/locations/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_ZONES_VIEW)")
    @Transactional(readOnly = true)
    public ResponseEntity<String> getLocationsGeoJson(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        List<GisBlock> leaves = gisBlockRepository.findLeafGisBlocks();
        return ResponseEntity.ok(buildLocationFeatureCollection(leaves));
    }

    // ── GeoJSON serialization ─────────────────────────────────────────────────

    private String buildLocationFeatureCollection(List<GisBlock> leaves) {
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        for (int i = 0; i < leaves.size(); i++) {
            if (i > 0)
                sb.append(",");
            GisBlock b = leaves.get(i);
            sb.append("{\"type\":\"Feature\"");
            sb.append(",\"id\":\"").append(b.getId()).append("\"");
            sb.append(",\"geometry\":").append(polygonToGeoJson(b.getGeometry()));
            sb.append(",\"properties\":{");
            sb.append("\"locationId\":\"").append(b.getLayoutBlockId()).append("\"");
            sb.append(",\"label\":\"").append(jsonEscape(b.getLabel())).append("\"");
            sb.append(",\"positionPath\":\"").append(jsonEscape(b.getPositionPath())).append("\"");
            sb.append("}}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String polygonToGeoJson(org.locationtech.jts.geom.Polygon polygon) {
        if (polygon == null)
            return "null";
        StringBuilder sb = new StringBuilder("{\"type\":\"Polygon\",\"coordinates\":[[");
        Coordinate[] coords = polygon.getCoordinates();
        for (int i = 0; i < coords.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append("[").append(coords[i].x).append(",").append(coords[i].y).append("]");
        }
        sb.append("]]}");
        return sb.toString();
    }

    private static String jsonEscape(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
