package com.warehouse.warehouse_platform.tenant.gis.controller;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.gis.GisException;
import com.warehouse.warehouse_platform.tenant.gis.model.GisBlock;
import com.warehouse.warehouse_platform.tenant.gis.model.GisBufferZone;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisBlockRepository;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisBufferZoneRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/gis")
public class GisLayerController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final GisBlockRepository gisBlockRepository;
    private final GisBufferZoneRepository gisBufferZoneRepository;

    public GisLayerController(
            TenantAccessPolicy tenantAccessPolicy,
            GisBlockRepository gisBlockRepository,
            GisBufferZoneRepository gisBufferZoneRepository) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.gisBlockRepository = gisBlockRepository;
        this.gisBufferZoneRepository = gisBufferZoneRepository;
    }

    // ── Zone GeoJSON ──────────────────────────────────────────────────────────

    /**
     * Returns a GeoJSON FeatureCollection of all drawn Zone polygons.
     * Properties: gisBlockId, layoutBlockId, zoneType, label, allowedCategoryIds
     */
    @GetMapping(value = "/zones/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_ZONES_VIEW)")
    @Transactional(readOnly = true)
    public ResponseEntity<String> getZonesGeoJson(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        List<GisBlock> zones = gisBlockRepository.findAllByTemplateNameOrderByDepthAsc("Zone");
        return ResponseEntity.ok(buildZoneFeatureCollection(zones));
    }

    // ── Leaf location GeoJSON ─────────────────────────────────────────────────

    /**
     * Returns a GeoJSON FeatureCollection of all drawn leaf-level location polygons.
     * A leaf is a gis_blocks row whose layoutBlockId has no children in layout_blocks.
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

    // ── Buffer-zone GeoJSON ───────────────────────────────────────────────────

    /**
     * Returns a GeoJSON FeatureCollection of all buffer zone polygons.
     * Properties: id, label, materialType, bufferDistanceM
     */
    @GetMapping(value = "/buffer-zones/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_ZONES_VIEW)")
    @Transactional(readOnly = true)
    public ResponseEntity<String> getBufferZonesGeoJson(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        List<GisBufferZone> bufferZones = gisBufferZoneRepository.findAll();
        return ResponseEntity.ok(buildBufferZoneFeatureCollection(bufferZones));
    }

    // ── Zone attribute update ─────────────────────────────────────────────────

    /**
     * Updates zone_type and allowed_category_ids on a Zone gis_blocks row.
     * Body: { "zoneType": "REFRIGERATED", "allowedCategoryIds": ["uuid1", "uuid2"] }
     */
    @PatchMapping("/zones/{gisBlockId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_ZONES_MANAGE)")
    @Transactional
    public ResponseEntity<ZoneAttributeResponse> patchZone(
            @PathVariable String tenantSlug,
            @PathVariable UUID gisBlockId,
            @RequestBody PatchZoneRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);

        GisBlock zone = gisBlockRepository.findById(gisBlockId)
                .orElseThrow(() -> GisException.notFound("GIS block not found: " + gisBlockId));

        if (!"Zone".equals(zone.getTemplateName())) {
            throw GisException.badRequest("GIS block " + gisBlockId + " is not a Zone");
        }

        zone.setZoneType(request.zoneType());
        zone.setAllowedCategoryIds(
                request.allowedCategoryIds() != null
                        ? request.allowedCategoryIds().toArray(new UUID[0])
                        : new UUID[0]);

        GisBlock saved = gisBlockRepository.save(zone);
        return ResponseEntity.ok(new ZoneAttributeResponse(
                saved.getId(),
                saved.getLayoutBlockId(),
                saved.getLabel(),
                saved.getZoneType(),
                saved.getAllowedCategoryIds() != null
                        ? Arrays.asList(saved.getAllowedCategoryIds())
                        : List.of()));
    }

    // ── GeoJSON serialization helpers ─────────────────────────────────────────

    private String buildZoneFeatureCollection(List<GisBlock> zones) {
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        for (int i = 0; i < zones.size(); i++) {
            if (i > 0) sb.append(",");
            GisBlock z = zones.get(i);
            sb.append("{\"type\":\"Feature\"");
            sb.append(",\"id\":\"").append(z.getId()).append("\"");
            sb.append(",\"geometry\":").append(polygonToGeoJson(z.getGeometry()));
            sb.append(",\"properties\":{");
            sb.append("\"gisBlockId\":\"").append(z.getId()).append("\"");
            sb.append(",\"layoutBlockId\":\"").append(z.getLayoutBlockId()).append("\"");
            sb.append(",\"label\":\"").append(jsonEscape(z.getLabel())).append("\"");
            sb.append(",\"zoneType\":").append(z.getZoneType() != null
                    ? "\"" + jsonEscape(z.getZoneType()) + "\""
                    : "null");
            sb.append(",\"allowedCategoryIds\":").append(uuidArrayToJson(z.getAllowedCategoryIds()));
            sb.append("}}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildLocationFeatureCollection(List<GisBlock> leaves) {
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        for (int i = 0; i < leaves.size(); i++) {
            if (i > 0) sb.append(",");
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

    private String buildBufferZoneFeatureCollection(List<GisBufferZone> zones) {
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        for (int i = 0; i < zones.size(); i++) {
            if (i > 0) sb.append(",");
            GisBufferZone bz = zones.get(i);
            sb.append("{\"type\":\"Feature\"");
            sb.append(",\"id\":\"").append(bz.getId()).append("\"");
            sb.append(",\"geometry\":").append(polygonToGeoJson(bz.getGeometry()));
            sb.append(",\"properties\":{");
            sb.append("\"id\":\"").append(bz.getId()).append("\"");
            sb.append(",\"label\":\"").append(jsonEscape(bz.getLabel())).append("\"");
            sb.append(",\"materialType\":\"").append(jsonEscape(bz.getMaterialType())).append("\"");
            sb.append(",\"bufferDistanceM\":").append(
                    bz.getBufferDistanceM() != null ? bz.getBufferDistanceM().toPlainString() : "null");
            sb.append("}}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String polygonToGeoJson(Polygon polygon) {
        if (polygon == null) return "null";
        StringBuilder sb = new StringBuilder("{\"type\":\"Polygon\",\"coordinates\":[[");
        Coordinate[] coords = polygon.getCoordinates();
        for (int i = 0; i < coords.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("[").append(coords[i].x).append(",").append(coords[i].y).append("]");
        }
        sb.append("]]}");
        return sb.toString();
    }

    private static String uuidArrayToJson(UUID[] ids) {
        if (ids == null || ids.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(ids[i]).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // ── Request / Response types ──────────────────────────────────────────────

    public record PatchZoneRequest(String zoneType, List<UUID> allowedCategoryIds) {
    }

    public record ZoneAttributeResponse(
            UUID gisBlockId,
            UUID layoutBlockId,
            String label,
            String zoneType,
            List<UUID> allowedCategoryIds) {
    }
}
