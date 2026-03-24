package com.warehouse.warehouse_platform.tenant.gis.controller;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.gis.config.WarehouseGisProperties;
import com.warehouse.warehouse_platform.tenant.gis.model.GisBlock;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisBlockRepository;
import com.warehouse.warehouse_platform.tenant.gis.service.FloorPlanStorageService;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplate;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplateRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlock;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayout;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayoutRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/{tenantSlug}/gis")
public class FloorPlanController {

    private final TenantAccessPolicy tenantAccessPolicy;
    private final FloorPlanStorageService storageService;
    private final WarehouseGisProperties gisProperties;
    private final WarehouseLayoutRepository warehouseLayoutRepository;
    private final LayoutBlockRepository layoutBlockRepository;
    private final BlockTemplateRepository blockTemplateRepository;
    private final GisBlockRepository gisBlockRepository;

    public FloorPlanController(
            TenantAccessPolicy tenantAccessPolicy,
            FloorPlanStorageService storageService,
            WarehouseGisProperties gisProperties,
            WarehouseLayoutRepository warehouseLayoutRepository,
            LayoutBlockRepository layoutBlockRepository,
            BlockTemplateRepository blockTemplateRepository,
            GisBlockRepository gisBlockRepository) {
        this.tenantAccessPolicy = tenantAccessPolicy;
        this.storageService = storageService;
        this.gisProperties = gisProperties;
        this.warehouseLayoutRepository = warehouseLayoutRepository;
        this.layoutBlockRepository = layoutBlockRepository;
        this.blockTemplateRepository = blockTemplateRepository;
        this.gisBlockRepository = gisBlockRepository;
    }

    // ── Existing floor plan endpoints ─────────────────────────────────────────

    @GetMapping("/floorplan/config")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_FLOOR_PLAN_VIEW)")
    public ResponseEntity<Map<String, Object>> getConfig(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        boolean hasFloorPlan = storageService.exists(tenantSlug);
        return ResponseEntity.ok(Map.of(
                "anchorLon", gisProperties.getAnchorLon(),
                "anchorLat", gisProperties.getAnchorLat(),
                "widthMeters", gisProperties.getWidthMeters(),
                "lengthMeters", gisProperties.getLengthMeters(),
                "hasFloorPlan", hasFloorPlan
        ));
    }

    @GetMapping("/floorplan/svg")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_FLOOR_PLAN_VIEW)")
    public ResponseEntity<Resource> getSvg(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        return storageService.load(tenantSlug)
                .map(resource -> ResponseEntity.ok()
                        .contentType(MediaType.valueOf("image/svg+xml"))
                        .body(resource))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/floorplan/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_FLOOR_PLAN_MANAGE)")
    public ResponseEntity<Map<String, Object>> upload(
            @PathVariable String tenantSlug,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase()
                : "";
        if (!originalName.endsWith(".svg")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Only SVG files are accepted."));
        }
        storageService.store(tenantSlug, file);
        return ResponseEntity.ok(Map.of("uploaded", true));
    }

    @DeleteMapping("/floorplan")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_FLOOR_PLAN_MANAGE)")
    public ResponseEntity<Void> delete(
            @PathVariable String tenantSlug,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        storageService.delete(tenantSlug);
        return ResponseEntity.noContent().build();
    }

    // ── New endpoints ─────────────────────────────────────────────────────────

    /**
     * Returns a flat list of layout blocks for the active layout filtered by template name.
     * Each entry includes id, fullCode, templateName, and depth.
     */
    @GetMapping("/floorplan/blocks")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_FLOOR_PLAN_VIEW)")
    public ResponseEntity<?> getBlocksForTemplate(
            @PathVariable String tenantSlug,
            @RequestParam String templateName,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);

        Optional<WarehouseLayout> activeLayout = warehouseLayoutRepository.findByIsActiveTrue();
        if (activeLayout.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "No active layout found."));
        }

        UUID layoutId = activeLayout.get().getId();
        List<LayoutBlock> allBlocks = layoutBlockRepository
                .findByLayoutIdOrderByParentIdAscPositionAsc(layoutId);

        // Compute depth for each block via iterative BFS (handles any DB ordering)
        Map<UUID, Integer> depthMap = new HashMap<>();
        for (LayoutBlock block : allBlocks) {
            if (block.getParentId() == null) {
                depthMap.put(block.getId(), 0);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (LayoutBlock block : allBlocks) {
                if (!depthMap.containsKey(block.getId()) && block.getParentId() != null) {
                    Integer parentDepth = depthMap.get(block.getParentId());
                    if (parentDepth != null) {
                        depthMap.put(block.getId(), parentDepth + 1);
                        changed = true;
                    }
                }
            }
        }

        Optional<BlockTemplate> template = blockTemplateRepository.findByNameIgnoreCase(templateName);
        if (template.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        UUID templateId = template.get().getId();

        List<Map<String, Object>> result = allBlocks.stream()
                .filter(b -> templateId.equals(b.getBlockTemplateId()))
                .map(b -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", b.getId());
                    m.put("fullCode", b.getFullCode() != null ? b.getFullCode() : "");
                    m.put("templateName", templateName);
                    m.put("depth", depthMap.getOrDefault(b.getId(), 0));
                    return m;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Saves (upserts by layoutBlockId) a manually drawn polygon as a GisBlock.
     * Does NOT call GeoServer provisioning — that is a separate admin action.
     */
    @PostMapping("/blocks/manual")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_FLOOR_PLAN_MANAGE)")
    public ResponseEntity<?> saveManualBlock(
            @PathVariable String tenantSlug,
            @RequestBody ManualGisBlockRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);

        if (!layoutBlockRepository.existsById(request.getLayoutBlockId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Layout block not found."));
        }

        List<List<Double>> exteriorCoords = request.getRings().get(0);
        Coordinate[] coordinates = new Coordinate[exteriorCoords.size()];
        for (int i = 0; i < exteriorCoords.size(); i++) {
            List<Double> pt = exteriorCoords.get(i);
            coordinates[i] = new Coordinate(pt.get(0), pt.get(1));
        }

        GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326);
        LinearRing ring = gf.createLinearRing(coordinates);
        Polygon polygon = gf.createPolygon(ring);
        Point centroid = polygon.getCentroid();
        centroid.setSRID(4326);

        GisBlock gisBlock = gisBlockRepository
                .findByLayoutBlockId(request.getLayoutBlockId())
                .orElseGet(GisBlock::new);

        gisBlock.setLayoutBlockId(request.getLayoutBlockId());
        gisBlock.setTemplateName(request.getTemplateName());
        gisBlock.setLabel(request.getLabel());
        gisBlock.setPositionPath(request.getPositionPath());
        gisBlock.setDepth(request.getDepth());
        gisBlock.setGeometry(polygon);
        gisBlock.setCentroidGeom(centroid);

        gisBlock = gisBlockRepository.save(gisBlock);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", gisBlock.getId());
        resp.put("layoutBlockId", gisBlock.getLayoutBlockId());
        resp.put("templateName", gisBlock.getTemplateName());
        resp.put("label", gisBlock.getLabel());
        return ResponseEntity.ok(resp);
    }

    /**
     * Reassigns an existing GisBlock to a different LayoutBlock without changing its geometry.
     */
    @PatchMapping("/blocks/manual/{gisBlockId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_FLOOR_PLAN_MANAGE)")
    public ResponseEntity<?> reassignManualBlock(
            @PathVariable String tenantSlug,
            @PathVariable UUID gisBlockId,
            @RequestBody ReassignRequest request,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);

        Optional<GisBlock> opt = gisBlockRepository.findById(gisBlockId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        GisBlock gisBlock = opt.get();
        gisBlock.setLayoutBlockId(request.getLayoutBlockId());
        gisBlock.setLabel(request.getLabel());
        gisBlock.setPositionPath(request.getPositionPath());
        gisBlock.setDepth(request.getDepth());
        gisBlockRepository.save(gisBlock);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", gisBlock.getId());
        resp.put("layoutBlockId", gisBlock.getLayoutBlockId());
        resp.put("label", gisBlock.getLabel());
        return ResponseEntity.ok(resp);
    }

    /**
     * Deletes a manually drawn GisBlock polygon by its UUID.
     */
    @DeleteMapping("/blocks/manual/{gisBlockId}")
    @PreAuthorize("hasAuthority(T(com.warehouse.warehouse_platform.security.permissions.TenantPermissions).GIS_FLOOR_PLAN_MANAGE)")
    public ResponseEntity<Void> deleteManualBlock(
            @PathVariable String tenantSlug,
            @PathVariable UUID gisBlockId,
            Authentication authentication) {
        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        gisBlockRepository.deleteById(gisBlockId);
        return ResponseEntity.noContent().build();
    }

    // ── Request DTOs ──────────────────────────────────────────────────────────

    public static class ManualGisBlockRequest {
        private UUID layoutBlockId;
        private String templateName;
        private String label;
        private String positionPath;
        private int depth;
        private List<List<List<Double>>> rings;

        public UUID getLayoutBlockId() { return layoutBlockId; }
        public void setLayoutBlockId(UUID layoutBlockId) { this.layoutBlockId = layoutBlockId; }
        public String getTemplateName() { return templateName; }
        public void setTemplateName(String templateName) { this.templateName = templateName; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getPositionPath() { return positionPath; }
        public void setPositionPath(String positionPath) { this.positionPath = positionPath; }
        public int getDepth() { return depth; }
        public void setDepth(int depth) { this.depth = depth; }
        public List<List<List<Double>>> getRings() { return rings; }
        public void setRings(List<List<List<Double>>> rings) { this.rings = rings; }
    }

    public static class ReassignRequest {
        private UUID layoutBlockId;
        private String label;
        private String positionPath;
        private int depth;

        public UUID getLayoutBlockId() { return layoutBlockId; }
        public void setLayoutBlockId(UUID layoutBlockId) { this.layoutBlockId = layoutBlockId; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getPositionPath() { return positionPath; }
        public void setPositionPath(String positionPath) { this.positionPath = positionPath; }
        public int getDepth() { return depth; }
        public void setDepth(int depth) { this.depth = depth; }
    }
}
