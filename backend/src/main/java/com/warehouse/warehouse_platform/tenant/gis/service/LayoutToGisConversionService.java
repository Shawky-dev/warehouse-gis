package com.warehouse.warehouse_platform.tenant.gis.service;

import com.warehouse.warehouse_platform.tenant.gis.GisException;
import com.warehouse.warehouse_platform.tenant.gis.config.WarehouseGisProperties;
import com.warehouse.warehouse_platform.tenant.gis.model.GisBlock;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisBlockRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplate;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplateRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlock;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayout;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayoutRepository;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LayoutToGisConversionService {

    private static final double LAT_DEGREE_PER_METER = 1.0 / 111_000.0;
    private static final double LON_DEGREE_PER_METER = 1.0 / 111_000.0;

    private final WarehouseLayoutRepository warehouseLayoutRepository;
    private final LayoutBlockRepository layoutBlockRepository;
    private final BlockTemplateRepository blockTemplateRepository;
    private final GisBlockRepository gisBlockRepository;
    private final WarehouseGisProperties gisProperties;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public LayoutToGisConversionService(
            WarehouseLayoutRepository warehouseLayoutRepository,
            LayoutBlockRepository layoutBlockRepository,
            BlockTemplateRepository blockTemplateRepository,
            GisBlockRepository gisBlockRepository,
            WarehouseGisProperties gisProperties) {
        this.warehouseLayoutRepository = warehouseLayoutRepository;
        this.layoutBlockRepository = layoutBlockRepository;
        this.blockTemplateRepository = blockTemplateRepository;
        this.gisBlockRepository = gisBlockRepository;
        this.gisProperties = gisProperties;
    }

    private record Bounds(double minLon, double minLat, double maxLon, double maxLat) {}

    /**
     * Converts the active warehouse layout into GIS shadow data.
     * Every block in the tree is stored in gis_blocks with its template_name.
     *
     * @return map of templateName → count of blocks converted
     */
    @Transactional
    public Map<String, Integer> convertActiveLayout() {
        // Step 1 — Load active layout
        WarehouseLayout layout = warehouseLayoutRepository.findByIsActiveTrue()
                .orElseThrow(() -> GisException.notFound("No active warehouse layout found"));

        // Step 2 — Load all blocks + templates for that layout
        List<LayoutBlock> blocks = layoutBlockRepository
                .findByLayoutIdOrderByParentIdAscPositionAsc(layout.getId());

        Set<UUID> templateIds = blocks.stream()
                .map(LayoutBlock::getBlockTemplateId)
                .collect(Collectors.toSet());

        Map<UUID, BlockTemplate> templateById = blockTemplateRepository.findAllById(templateIds)
                .stream()
                .collect(Collectors.toMap(BlockTemplate::getId, t -> t));

        // Step 3 — Clear existing GIS rows for this layout
        List<UUID> allBlockIds = blocks.stream().map(LayoutBlock::getId).collect(Collectors.toList());
        if (!allBlockIds.isEmpty()) {
            gisBlockRepository.deleteAllByLayoutBlockIdIn(allBlockIds);
        }

        // Step 4 — Build in-memory parent→children tree (null key = root blocks)
        Map<UUID, List<LayoutBlock>> childrenByParentId = new HashMap<>();
        for (LayoutBlock block : blocks) {
            childrenByParentId.computeIfAbsent(block.getParentId(), k -> new ArrayList<>()).add(block);
        }

        // Step 5 — Recursive geometry subdivision
        double anchorLon = gisProperties.getAnchorLon();
        double anchorLat = gisProperties.getAnchorLat();
        Bounds warehouseBounds = new Bounds(
                anchorLon,
                anchorLat,
                anchorLon + (gisProperties.getWidthMeters() * LON_DEGREE_PER_METER),
                anchorLat + (gisProperties.getLengthMeters() * LAT_DEGREE_PER_METER)
        );

        Map<String, Integer> counts = new HashMap<>();
        List<LayoutBlock> rootBlocks = childrenByParentId.getOrDefault(null, List.of());
        processLevel(rootBlocks, warehouseBounds, false, templateById, childrenByParentId, counts, 0);

        return counts;
    }

    private void processLevel(
            List<LayoutBlock> siblings,
            Bounds parentBounds,
            boolean splitAlongWidth,
            Map<UUID, BlockTemplate> templateById,
            Map<UUID, List<LayoutBlock>> childrenByParentId,
            Map<String, Integer> counts,
            int depth) {

        int n = siblings.size();
        if (n == 0) return;

        for (int i = 0; i < n; i++) {
            LayoutBlock block = siblings.get(i);
            Bounds blockBounds;

            if (splitAlongWidth) {
                double sliceWidth = (parentBounds.maxLon() - parentBounds.minLon()) / n;
                double bMinLon = parentBounds.minLon() + i * sliceWidth;
                double bMaxLon = parentBounds.minLon() + (i + 1) * sliceWidth;
                double bMinLat = parentBounds.minLat();
                double bMaxLat = parentBounds.maxLat();

                // Side split: halve lat range for L/A (left) vs R/B (right)
                if (block.getSide() != null) {
                    double midLat = (bMinLat + bMaxLat) / 2.0;
                    String side = block.getSide();
                    if ("L".equalsIgnoreCase(side) || "A".equalsIgnoreCase(side)) {
                        bMaxLat = midLat;
                    } else {
                        bMinLat = midLat;
                    }
                }
                blockBounds = new Bounds(bMinLon, bMinLat, bMaxLon, bMaxLat);
            } else {
                double sliceHeight = (parentBounds.maxLat() - parentBounds.minLat()) / n;
                blockBounds = new Bounds(
                        parentBounds.minLon(),
                        parentBounds.minLat() + i * sliceHeight,
                        parentBounds.maxLon(),
                        parentBounds.minLat() + (i + 1) * sliceHeight
                );
            }

            // Build JTS Polygon: SW → SE → NE → NW → SW (closed ring)
            Polygon polygon = geometryFactory.createPolygon(new Coordinate[]{
                    new Coordinate(blockBounds.minLon(), blockBounds.minLat()),
                    new Coordinate(blockBounds.maxLon(), blockBounds.minLat()),
                    new Coordinate(blockBounds.maxLon(), blockBounds.maxLat()),
                    new Coordinate(blockBounds.minLon(), blockBounds.maxLat()),
                    new Coordinate(blockBounds.minLon(), blockBounds.minLat())
            });
            Point centroid = polygon.getCentroid();

            BlockTemplate template = templateById.get(block.getBlockTemplateId());
            String templateName = template != null ? template.getName() : "Unknown";

            GisBlock gisBlock = GisBlock.builder()
                    .layoutBlockId(block.getId())
                    .templateName(templateName)
                    .label(block.getFullCode())
                    .positionPath(block.getFullCode())
                    .depth(depth)
                    .geometry(polygon)
                    .centroidGeom((Point) centroid)
                    .build();
            gisBlockRepository.save(gisBlock);
            counts.merge(templateName, 1, Integer::sum);

            // Recurse into children, alternating split axis
            List<LayoutBlock> children = childrenByParentId.getOrDefault(block.getId(), List.of());
            if (!children.isEmpty()) {
                processLevel(children, blockBounds, !splitAlongWidth, templateById, childrenByParentId, counts, depth + 1);
            }
        }
    }

    /**
     * Returns a GeoJSON FeatureCollection of all gis_blocks for the given templateName.
     * Returns an empty FeatureCollection if no data exists for that template name.
     */
    public String buildGeoJsonFeatureCollection(String templateName) {
        List<Object[]> rows = gisBlockRepository.findAllForGeoJsonByTemplateName(templateName);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sb.append(",");
            Object[] row = rows.get(i);
            // row: [0]=id, [1]=label, [2]=position_path, [3]=depth, [4]=geom_json
            String id = row[0] != null ? row[0].toString() : "";
            String label = row[1] != null ? jsonEscape(row[1].toString()) : "";
            String positionPath = row[2] != null ? jsonEscape(row[2].toString()) : "";
            int depth = row[3] != null ? Integer.parseInt(row[3].toString()) : 0;
            String geomJson = row[4] != null ? row[4].toString() : "null";

            sb.append("{\"type\":\"Feature\"");
            sb.append(",\"id\":\"").append(id).append("\"");
            sb.append(",\"geometry\":").append(geomJson);
            sb.append(",\"properties\":{");
            sb.append("\"templateName\":\"").append(jsonEscape(templateName)).append("\"");
            sb.append(",\"label\":\"").append(label).append("\"");
            sb.append(",\"positionPath\":\"").append(positionPath).append("\"");
            sb.append(",\"depth\":").append(depth);
            sb.append("}}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
