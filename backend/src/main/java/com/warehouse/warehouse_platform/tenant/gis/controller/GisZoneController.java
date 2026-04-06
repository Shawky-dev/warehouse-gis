package com.warehouse.warehouse_platform.tenant.gis.controller;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.gis.GisException;
import com.warehouse.warehouse_platform.tenant.gis.model.GisZone;
import com.warehouse.warehouse_platform.tenant.gis.model.GisZoneCategoryRule;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisZoneCategoryRuleRepository;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisZoneRepository;
import com.warehouse.warehouse_platform.tenant.zonetype.ZoneType;
import com.warehouse.warehouse_platform.tenant.zonetype.ZoneTypeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/{tenantSlug}/gis/zones")
public class GisZoneController {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    private final TenantAccessPolicy tenantAccessPolicy;
    private final GisZoneRepository gisZoneRepository;
    private final GisZoneCategoryRuleRepository gisZoneCategoryRuleRepository;
    private final ZoneTypeRepository zoneTypeRepository;

    public GisZoneController(
            TenantAccessPolicy tenantAccessPolicy,
            GisZoneRepository gisZoneRepository,
            GisZoneCategoryRuleRepository gisZoneCategoryRuleRepository,
            ZoneTypeRepository zoneTypeRepository) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.gisZoneRepository = gisZoneRepository;
        this.gisZoneCategoryRuleRepository = gisZoneCategoryRuleRepository;
        this.zoneTypeRepository = zoneTypeRepository;
    }

    // ── List ─────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_ZONES_VIEW)")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ZoneResponse>> listZones(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        List<ZoneResponse> zones = gisZoneRepository.findAllByOrderByCreatedAtAsc()
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(zones);
    }

    // ── GeoJSON ───────────────────────────────────────────────────────────────

    @GetMapping(value = "/geojson", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_ZONES_VIEW)")
    @Transactional(readOnly = true)
    public ResponseEntity<String> getZonesGeoJson(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        List<GisZone> zones = gisZoneRepository.findAllByOrderByCreatedAtAsc();
        return ResponseEntity.ok(buildZoneFeatureCollection(zones));
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_ZONES_MANAGE)")
    @Transactional
    public ResponseEntity<ZoneResponse> createZone(
            @PathVariable String tenantSlug,
            @Valid @RequestBody ZoneRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);

        GisZone zone = GisZone.builder()
                .name(request.name())
                .description(request.description())
                .geometry(ringsToPolygon(request.coordinates()))
                .violationAction(request.violationAction())
                .source(request.source() != null ? request.source() : "MANUAL")
                .zoneType(resolveZoneType(request.zoneTypeId()))
                .displayColor(normalizeDisplayColor(request.displayColor()))
                .build();

        GisZone saved = gisZoneRepository.save(zone);
        saved = applyRules(saved, request.categoryRules());
        return ResponseEntity.ok(toResponse(saved));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @PutMapping("/{zoneId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_ZONES_MANAGE)")
    @Transactional
    public ResponseEntity<ZoneResponse> updateZone(
            @PathVariable String tenantSlug,
            @PathVariable UUID zoneId,
            @Valid @RequestBody ZoneRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);

        GisZone zone = gisZoneRepository.findById(zoneId)
                .orElseThrow(() -> GisException.notFound("Zone not found: " + zoneId));

        zone.setName(request.name());
        zone.setDescription(request.description());
        if (request.coordinates() != null && !request.coordinates().isEmpty()) {
            zone.setGeometry(ringsToPolygon(request.coordinates()));
        }
        zone.setViolationAction(request.violationAction());
        zone.setZoneType(resolveZoneType(request.zoneTypeId()));
        zone.setDisplayColor(normalizeDisplayColor(request.displayColor()));

        GisZone saved = gisZoneRepository.save(zone);
        saved = applyRules(saved, request.categoryRules());
        return ResponseEntity.ok(toResponse(saved));
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @DeleteMapping("/{zoneId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_ZONES_MANAGE)")
    @Transactional
    public ResponseEntity<Void> deleteZone(
            @PathVariable String tenantSlug,
            @PathVariable UUID zoneId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);

        if (!gisZoneRepository.existsById(zoneId)) {
            throw GisException.notFound("Zone not found: " + zoneId);
        }
        gisZoneRepository.deleteById(zoneId);
        return ResponseEntity.noContent().build();
    }

    // ── Import from ArcGIS Pro GeoJSON ────────────────────────────────────────

    @PostMapping("/import")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_ZONES_MANAGE)")
    @Transactional
    public ResponseEntity<List<ZoneResponse>> importZones(
            @PathVariable String tenantSlug,
            @RequestBody GeoJsonImportRequest importRequest,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);

        if (importRequest.features() == null || importRequest.features().isEmpty()) {
            throw GisException.badRequest("Import payload must contain at least one feature");
        }

        List<ZoneResponse> created = importRequest.features().stream()
                .map(feature -> {
                    String name = feature.properties() != null && feature.properties().name() != null
                            ? feature.properties().name()
                            : "Imported Zone";
                    GisZone zone = GisZone.builder()
                            .name(name)
                            .description(feature.properties() != null ? feature.properties().description() : null)
                            .geometry(ringsToPolygon(extractRings(feature.geometry())))
                            .violationAction(feature.properties() != null
                                    && feature.properties().violationAction() != null
                                            ? feature.properties().violationAction()
                                            : "BLOCK")
                            .source("ARCGIS_IMPORT")
                            .build();
                    return toResponse(gisZoneRepository.save(zone));
                })
                .toList();

        return ResponseEntity.ok(created);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GisZone applyRules(GisZone zone, List<CategoryRuleRequest> rules) {
        // Clear existing rules with a direct SQL batch DELETE to avoid Hibernate
        // flush-ordering issues (inserts are queued before deletes, causing uq
        // violations).
        List<GisZoneCategoryRule> existing = gisZoneCategoryRuleRepository.findByZoneId(zone.getId());
        gisZoneCategoryRuleRepository.deleteAllInBatch(existing);

        if (rules != null) {
            List<GisZoneCategoryRule> newRules = rules.stream()
                    .map(r -> GisZoneCategoryRule.builder()
                            .zone(zone)
                            .categoryId(r.categoryId())
                            .ruleType(r.ruleType())
                            .build())
                    .toList();
            gisZoneCategoryRuleRepository.saveAll(newRules);
        }
        return gisZoneRepository.findById(zone.getId()).orElse(zone);
    }

    private ZoneType resolveZoneType(UUID zoneTypeId) {
        if (zoneTypeId == null) return null;
        return zoneTypeRepository.findById(zoneTypeId)
                .orElseThrow(() -> GisException.badRequest("Zone type not found: " + zoneTypeId));
    }

    private static String normalizeDisplayColor(String color) {
        if (color == null || color.isBlank()) return null;
        String trimmed = color.trim();
        if (!trimmed.matches("#[0-9A-Fa-f]{6}")) {
            throw GisException.badRequest("displayColor must be in #RRGGBB format: " + trimmed);
        }
        return trimmed.toUpperCase(java.util.Locale.ROOT);
    }

    private ZoneResponse toResponse(GisZone zone) {
        List<CategoryRuleResponse> rules = gisZoneCategoryRuleRepository.findByZoneId(zone.getId())
                .stream()
                .map(r -> new CategoryRuleResponse(r.getCategoryId(), r.getRuleType()))
                .toList();
        ZoneType zt = zone.getZoneType();
        return new ZoneResponse(
                zone.getId(),
                zone.getName(),
                zone.getDescription(),
                zone.getViolationAction(),
                zone.getSource(),
                zt != null ? zt.getId() : null,
                zt != null ? zt.getCode() : null,
                zone.getDisplayColor(),
                rules,
                zone.getCreatedAt(),
                zone.getUpdatedAt());
    }

    private org.locationtech.jts.geom.Polygon ringsToPolygon(List<List<List<Double>>> coordinates) {
        List<List<Double>> ring = coordinates.get(0);
        Coordinate[] coords = new Coordinate[ring.size()];
        for (int i = 0; i < ring.size(); i++) {
            List<Double> point = ring.get(i);
            coords[i] = new Coordinate(point.get(0), point.get(1));
        }
        return GEOMETRY_FACTORY.createPolygon(coords);
    }

    @SuppressWarnings("unchecked")
    private List<List<List<Double>>> extractRings(Object geometry) {
        if (geometry instanceof java.util.Map<?, ?> geoMap) {
            Object coords = geoMap.get("coordinates");
            return (List<List<List<Double>>>) coords;
        }
        throw GisException.badRequest("Cannot parse geometry from import feature");
    }

    private String buildZoneFeatureCollection(List<GisZone> zones) {
        StringBuilder sb = new StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[");
        for (int i = 0; i < zones.size(); i++) {
            if (i > 0)
                sb.append(",");
            GisZone z = zones.get(i);
            List<GisZoneCategoryRule> rules = gisZoneCategoryRuleRepository.findByZoneId(z.getId());
            sb.append("{\"type\":\"Feature\"");
            sb.append(",\"id\":\"").append(z.getId()).append("\"");
            sb.append(",\"geometry\":").append(polygonToGeoJson(z.getGeometry()));
            sb.append(",\"properties\":{");
            sb.append("\"id\":\"").append(z.getId()).append("\"");
            sb.append(",\"name\":\"").append(jsonEscape(z.getName())).append("\"");
            sb.append(",\"description\":").append(z.getDescription() != null
                    ? "\"" + jsonEscape(z.getDescription()) + "\""
                    : "null");
            sb.append(",\"violationAction\":\"").append(z.getViolationAction()).append("\"");
            sb.append(",\"source\":\"").append(z.getSource()).append("\"");
            ZoneType zt = z.getZoneType();
            sb.append(",\"zoneTypeId\":").append(zt != null ? "\"" + zt.getId() + "\"" : "null");
            sb.append(",\"zoneTypeCode\":").append(zt != null ? "\"" + jsonEscape(zt.getCode()) + "\"" : "null");
            sb.append(",\"displayColor\":").append(z.getDisplayColor() != null
                    ? "\"" + jsonEscape(z.getDisplayColor()) + "\""
                    : "null");
            sb.append(",\"categoryRules\":").append(rulesToJson(rules));
            sb.append("}}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String rulesToJson(List<GisZoneCategoryRule> rules) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rules.size(); i++) {
            if (i > 0)
                sb.append(",");
            GisZoneCategoryRule r = rules.get(i);
            sb.append("{\"categoryId\":\"").append(r.getCategoryId())
                    .append("\",\"ruleType\":\"").append(r.getRuleType()).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String polygonToGeoJson(org.locationtech.jts.geom.Polygon polygon) {
        if (polygon == null)
            return "null";
        StringBuilder sb = new StringBuilder("{\"type\":\"Polygon\",\"coordinates\":[[");
        org.locationtech.jts.geom.Coordinate[] coords = polygon.getCoordinates();
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

    // ── Request / Response records ────────────────────────────────────────────

    public record CategoryRuleRequest(
            @NotNull UUID categoryId,
            @NotBlank @Pattern(regexp = "ALLOWED|PROHIBITED") String ruleType) {
    }

    public record ZoneRequest(
            @NotBlank @Size(max = 200) String name,
            String description,
            List<List<List<Double>>> coordinates,
            @NotBlank @Pattern(regexp = "BLOCK|WARN") String violationAction,
            String source,
            UUID zoneTypeId,
            @Pattern(regexp = "#[0-9A-Fa-f]{6}") String displayColor,
            List<CategoryRuleRequest> categoryRules) {
    }

    public record CategoryRuleResponse(UUID categoryId, String ruleType) {
    }

    public record ZoneResponse(
            UUID id,
            String name,
            String description,
            String violationAction,
            String source,
            UUID zoneTypeId,
            String zoneTypeCode,
            String displayColor,
            List<CategoryRuleResponse> categoryRules,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {
    }

    // ── Import records ────────────────────────────────────────────────────────

    public record ImportFeatureProperties(
            String name,
            String description,
            String violationAction) {
    }

    public record ImportFeature(
            Object geometry,
            ImportFeatureProperties properties) {
    }

    public record GeoJsonImportRequest(
            List<ImportFeature> features) {
    }
}
