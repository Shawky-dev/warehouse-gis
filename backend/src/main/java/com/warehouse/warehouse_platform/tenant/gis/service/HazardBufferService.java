package com.warehouse.warehouse_platform.tenant.gis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.warehouse_platform.tenant.gis.GisException;
import com.warehouse.warehouse_platform.tenant.gis.model.GisHazardBuffer;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisHazardBufferRepository;
import com.warehouse.warehouse_platform.tenant.hazardtype.HazardType;
import com.warehouse.warehouse_platform.tenant.hazardtype.HazardTypeRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class HazardBufferService {

    private static final Logger log = LoggerFactory.getLogger(HazardBufferService.class);
    private static final String CODE_NONE = "NONE";
    private static final GeometryFactory GEOM = new GeometryFactory(new PrecisionModel(), 4326);

    private final GisHazardBufferRepository hazardBufferRepository;
    private final HazardTypeRepository hazardTypeRepository;
    private final GeoServerProvisioningService geoServerProvisioningService;
    private final ObjectMapper objectMapper;

    public HazardBufferService(
            GisHazardBufferRepository hazardBufferRepository,
            HazardTypeRepository hazardTypeRepository,
            GeoServerProvisioningService geoServerProvisioningService,
            ObjectMapper objectMapper) {
        this.hazardBufferRepository = hazardBufferRepository;
        this.hazardTypeRepository = hazardTypeRepository;
        this.geoServerProvisioningService = geoServerProvisioningService;
        this.objectMapper = objectMapper;
    }

    // ── read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<HazardBufferSummary> listAll() {
        return hazardBufferRepository.findAllByOrderByNameAscIdAsc()
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public GisHazardBuffer getById(UUID bufferId) {
        return hazardBufferRepository.findById(bufferId)
                .orElseThrow(() -> GisException.notFound("Hazard buffer not found: " + bufferId));
    }

    // ── write ─────────────────────────────────────────────────────────────────

    @Transactional
    public ImportResult importGeoJson(MultipartFile file, String tenantSlug) {
        JsonNode root;
        try {
            root = objectMapper.readTree(file.getInputStream());
        } catch (IOException e) {
            throw GisException.badRequest("Could not parse uploaded file as JSON: " + e.getMessage());
        }

        if (!"FeatureCollection".equals(root.path("type").asText())) {
            throw GisException.badRequest("Expected a GeoJSON FeatureCollection");
        }

        JsonNode features = root.path("features");
        if (!features.isArray() || features.isEmpty()) {
            throw GisException.badRequest("FeatureCollection must contain at least one feature");
        }

        UUID batchId = UUID.randomUUID();
        String filename = file.getOriginalFilename();
        Instant now = Instant.now();

        List<GisHazardBuffer> persisted = new ArrayList<>();
        int index = 0;
        for (JsonNode feature : features) {
            index++;
            GisHazardBuffer buffer = parseFeature(feature, index, batchId, filename, now);
            persisted.add(hazardBufferRepository.save(buffer));
        }

        try {
            geoServerProvisioningService.ensureHazardBufferLayerExists(tenantSlug);
        } catch (Exception e) {
            log.warn("GeoServer hazard-buffer sync failed after import (non-fatal): {}", e.getMessage());
        }

        return new ImportResult(persisted.size(), batchId);
    }

    @Transactional
    public HazardBufferSummary createHazardBuffer(
            String tenantSlug,
            String name,
            List<List<List<Double>>> coordinates,
            String notes,
            List<UUID> restrictedHazardTypeIds) {
        List<HazardType> restrictedTypes = resolveHazardTypesById(restrictedHazardTypeIds);
        if (restrictedTypes.isEmpty()) {
            throw GisException.badRequest("At least one restricted hazard type is required");
        }

        GisHazardBuffer saved = hazardBufferRepository.save(GisHazardBuffer.builder()
                .name(requireName(name))
                .source("MANUAL")
                .geometry(ringsToPolygon(coordinates))
                .notes(normalizeNotes(notes))
                .restrictedHazardTypes(new ArrayList<>(restrictedTypes))
                .build());

        refreshGeoServer(tenantSlug, "create");
        return toSummary(saved);
    }

    @Transactional
    public HazardBufferSummary updateHazardBuffer(
            String tenantSlug,
            UUID bufferId,
            String name,
            List<List<List<Double>>> coordinates,
            String notes,
            List<UUID> restrictedHazardTypeIds) {
        GisHazardBuffer buffer = hazardBufferRepository.findById(bufferId)
                .orElseThrow(() -> GisException.notFound("Hazard buffer not found: " + bufferId));
        List<HazardType> restrictedTypes = resolveHazardTypesById(restrictedHazardTypeIds);
        if (restrictedTypes.isEmpty()) {
            throw GisException.badRequest("At least one restricted hazard type is required");
        }

        buffer.setName(requireName(name));
        buffer.setNotes(normalizeNotes(notes));
        if (coordinates != null && !coordinates.isEmpty()) {
            buffer.setGeometry(ringsToPolygon(coordinates));
        }
        buffer.setRestrictedHazardTypes(new ArrayList<>(restrictedTypes));

        GisHazardBuffer saved = hazardBufferRepository.save(buffer);
        refreshGeoServer(tenantSlug, "update");
        return toSummary(saved);
    }

    @Transactional
    public void delete(UUID bufferId, String tenantSlug) {
        GisHazardBuffer buffer = hazardBufferRepository.findById(bufferId)
                .orElseThrow(() -> GisException.notFound("Hazard buffer not found: " + bufferId));
        hazardBufferRepository.delete(buffer);
        try {
            geoServerProvisioningService.refreshLayerGroup(tenantSlug);
        } catch (Exception e) {
            log.warn("GeoServer refresh failed after hazard-buffer delete (non-fatal): {}", e.getMessage());
        }
    }

    // ── GeoJSON export ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public String buildGeoJsonExport() {
        List<GisHazardBuffer> buffers = hazardBufferRepository.findAllByOrderByNameAscIdAsc();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[");
        boolean first = true;
        for (GisHazardBuffer b : buffers) {
            if (!first)
                sb.append(",");
            first = false;
            sb.append(buildFeatureJson(b));
        }
        sb.append("]}");
        return sb.toString();
    }

    // ── private ───────────────────────────────────────────────────────────────

    private GisHazardBuffer parseFeature(JsonNode feature, int index, UUID batchId, String filename, Instant now) {
        JsonNode props = feature.path("properties");
        JsonNode geomNode = feature.path("geometry");

        String name = props.path("name").asText("").trim();
        if (name.isBlank()) {
            throw GisException.badRequest("Feature #" + index + ": missing 'name' property");
        }

        Polygon polygon = parsePolygon(geomNode, index);

        List<HazardType> restrictedTypes = resolveHazardTypes(props, index);
        if (restrictedTypes.isEmpty()) {
            throw GisException.badRequest("Feature #" + index + ": at least one restricted hazard type is required");
        }

        String notes = props.has("notes") ? props.path("notes").asText(null) : null;

        return GisHazardBuffer.builder()
                .name(name)
                .source("ARCGIS_IMPORT")
                .geometry(polygon)
                .notes(notes != null && notes.isBlank() ? null : notes)
                .importBatchId(batchId)
                .sourceFilename(filename)
                .importedAt(now)
                .restrictedHazardTypes(new ArrayList<>(restrictedTypes))
                .build();
    }

    private Polygon parsePolygon(JsonNode geomNode, int index) {
        String geomType = geomNode.path("type").asText("");
        if (!"Polygon".equals(geomType)) {
            throw GisException.badRequest(
                    "Feature #" + index + ": geometry must be a Polygon, got '" + geomType + "'");
        }
        JsonNode rings = geomNode.path("coordinates");
        if (!rings.isArray() || rings.isEmpty()) {
            throw GisException.badRequest("Feature #" + index + ": polygon coordinates are empty");
        }
        try {
            JsonNode exteriorRingNode = rings.get(0);
            Coordinate[] coords = new Coordinate[exteriorRingNode.size()];
            for (int i = 0; i < exteriorRingNode.size(); i++) {
                JsonNode point = exteriorRingNode.get(i);
                coords[i] = new Coordinate(point.get(0).asDouble(), point.get(1).asDouble());
            }
            if (coords.length < 4) {
                throw GisException.badRequest(
                        "Feature #" + index + ": exterior ring must have at least 4 coordinates");
            }
            LinearRing shell = GEOM.createLinearRing(coords);
            return GEOM.createPolygon(shell, new LinearRing[0]);
        } catch (GisException e) {
            throw e;
        } catch (Exception e) {
            throw GisException.badRequest("Feature #" + index + ": invalid polygon geometry: " + e.getMessage());
        }
    }

    private List<HazardType> resolveHazardTypes(JsonNode props, int index) {
        JsonNode typesNode;
        if (props.has("restrictedHazardTypes")) {
            typesNode = props.path("restrictedHazardTypes");
        } else if (props.has("restrictedHazardTypeCodes")) {
            typesNode = props.path("restrictedHazardTypeCodes");
        } else {
            typesNode = props.path("hazardType");
        }

        List<String> codes = new ArrayList<>();
        if (typesNode.isArray()) {
            for (JsonNode n : typesNode)
                codes.add(n.asText("").trim().toUpperCase(Locale.ROOT));
        } else if (typesNode.isTextual()) {
            codes.add(typesNode.asText("").trim().toUpperCase(Locale.ROOT));
        } else {
            throw GisException.badRequest("Feature #" + index + ": missing or invalid hazard type metadata");
        }

        List<HazardType> result = new ArrayList<>();
        for (String code : codes) {
            if (code.isBlank())
                continue;
            if (CODE_NONE.equals(code)) {
                throw GisException.badRequest(
                        "Feature #" + index + ": hazard type 'NONE' is not allowed as a buffer restriction");
            }
            HazardType ht = hazardTypeRepository.findByCodeIgnoreCase(code)
                    .orElseThrow(() -> GisException.badRequest(
                            "Feature #" + index + ": unknown hazard type code '" + code + "'"));
            if (!Boolean.TRUE.equals(ht.getIsActive())) {
                throw GisException.badRequest(
                        "Feature #" + index + ": hazard type '" + code + "' is inactive");
            }
            result.add(ht);
        }
        return result;
    }

    private List<HazardType> resolveHazardTypesById(List<UUID> hazardTypeIds) {
        if (hazardTypeIds == null || hazardTypeIds.isEmpty()) {
            return List.of();
        }
        List<HazardType> result = new ArrayList<>();
        for (UUID id : hazardTypeIds) {
            HazardType ht = hazardTypeRepository.findById(id)
                    .orElseThrow(() -> GisException.badRequest("Hazard type not found: " + id));
            if (!Boolean.TRUE.equals(ht.getIsActive())) {
                throw GisException.badRequest("Hazard type '" + ht.getCode() + "' is inactive");
            }
            result.add(ht);
        }
        return result;
    }

    public Polygon ringsToPolygon(List<List<List<Double>>> coordinateRings) {
        if (coordinateRings == null || coordinateRings.isEmpty()) {
            throw GisException.badRequest("Polygon coordinates are required");
        }
        try {
            LinearRing shell = GEOM.createLinearRing(toClosedCoordinates(coordinateRings.get(0), "exterior"));
            LinearRing[] holes = new LinearRing[Math.max(0, coordinateRings.size() - 1)];
            for (int i = 1; i < coordinateRings.size(); i++) {
                holes[i - 1] = GEOM.createLinearRing(toClosedCoordinates(coordinateRings.get(i), "interior"));
            }
            return GEOM.createPolygon(shell, holes);
        } catch (GisException e) {
            throw e;
        } catch (Exception e) {
            throw GisException.badRequest("Invalid polygon geometry: " + e.getMessage());
        }
    }

    private Coordinate[] toClosedCoordinates(List<List<Double>> ring, String ringName) {
        if (ring == null || ring.size() < 3) {
            throw GisException.badRequest("Polygon " + ringName + " ring must contain at least 3 points");
        }
        List<Coordinate> coords = new ArrayList<>();
        for (List<Double> point : ring) {
            if (point == null || point.size() < 2 || point.get(0) == null || point.get(1) == null) {
                throw GisException.badRequest("Polygon " + ringName + " ring contains an invalid coordinate");
            }
            coords.add(new Coordinate(point.get(0), point.get(1)));
        }
        Coordinate first = coords.get(0);
        Coordinate last = coords.get(coords.size() - 1);
        if (!first.equals2D(last)) {
            coords.add(new Coordinate(first));
        }
        if (coords.size() < 4) {
            throw GisException.badRequest("Polygon " + ringName + " ring must have at least 4 coordinates");
        }
        return coords.toArray(Coordinate[]::new);
    }

    private String requireName(String name) {
        if (name == null || name.trim().isBlank()) {
            throw GisException.badRequest("Hazard buffer name is required");
        }
        return name.trim();
    }

    private String normalizeNotes(String notes) {
        return notes == null || notes.isBlank() ? null : notes;
    }

    private void refreshGeoServer(String tenantSlug, String operation) {
        try {
            geoServerProvisioningService.ensureHazardBufferLayerExists(tenantSlug);
            geoServerProvisioningService.refreshLayerGroup(tenantSlug);
        } catch (Exception e) {
            log.warn("GeoServer refresh failed after hazard-buffer {} (non-fatal): {}", operation, e.getMessage());
        }
    }

    public HazardBufferSummary toSummary(GisHazardBuffer b) {
        List<HazardTypeSummary> types = b.getRestrictedHazardTypes().stream()
                .map(ht -> new HazardTypeSummary(ht.getId(), ht.getCode(), ht.getDisplayName()))
                .toList();
        return new HazardBufferSummary(b.getId(), b.getName(), b.getSource(), b.getNotes(),
                types, b.getImportBatchId(), b.getSourceFilename(), b.getImportedAt(),
                b.getCreatedAt(), b.getUpdatedAt());
    }

    private String buildFeatureJson(GisHazardBuffer b) {
        String codesJson = b.getRestrictedHazardTypes().stream()
                .map(ht -> "\"" + ht.getCode() + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String idsJson = b.getRestrictedHazardTypes().stream()
                .map(ht -> "\"" + ht.getId() + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        StringBuilder coords = new StringBuilder();
        Polygon poly = b.getGeometry();
        if (poly != null) {
            coords.append("[");
            appendRing(coords, poly.getExteriorRing().getCoordinates());
            for (int i = 0; i < poly.getNumInteriorRing(); i++) {
                coords.append(",");
                appendRing(coords, poly.getInteriorRingN(i).getCoordinates());
            }
            coords.append("]");
        } else {
            coords.append("[]");
        }
        return "{\"type\":\"Feature\",\"properties\":{\"id\":\"" + b.getId()
                + "\",\"name\":\"" + escapeJson(b.getName())
                + "\",\"source\":\"" + b.getSource()
                + "\",\"notes\":" + (b.getNotes() != null ? "\"" + escapeJson(b.getNotes()) + "\"" : "null")
                + ",\"restrictedHazardTypeIds\":" + idsJson
                + ",\"restrictedHazardTypeCodes\":" + codesJson
                + "},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":" + coords + "}}";
    }

    private void appendRing(StringBuilder coords, Coordinate[] ring) {
        coords.append("[");
        for (Coordinate c : ring) {
            coords.append("[").append(c.x).append(",").append(c.y).append("],");
        }
        if (coords.charAt(coords.length() - 1) == ',')
            coords.deleteCharAt(coords.length() - 1);
        coords.append("]");
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ── result types ──────────────────────────────────────────────────────────

    public record ImportResult(int imported, UUID batchId) {
    }

    public record HazardTypeSummary(UUID id, String code, String displayName) {
    }

    public record HazardBufferSummary(
            UUID id,
            String name,
            String source,
            String notes,
            List<HazardTypeSummary> restrictedHazardTypes,
            UUID importBatchId,
            String sourceFilename,
            Instant importedAt,
            Instant createdAt,
            Instant updatedAt) {
    }
}
