package com.warehouse.warehouse_platform.tenant.gis.service;

import com.warehouse.warehouse_platform.tenant.gis.GisException;
import com.warehouse.warehouse_platform.tenant.gis.config.WarehouseGisProperties;
import com.warehouse.warehouse_platform.tenant.gis.layout.LayoutNode;
import com.warehouse.warehouse_platform.tenant.gis.layout.LayoutNodeBuilder;
import com.warehouse.warehouse_platform.tenant.gis.layout.SimpleTreeLayout;
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

    /**
     * Generates GIS shadow data from the active warehouse layout.
     *
     * <p>
     * Fails with conflict if GIS shadow data already exists.
     */
    @Transactional
    public Map<String, Integer> generateFromActiveLayout() {
        return convertActiveLayout(false);
    }

    /**
     * Rebuilds GIS shadow data from the active warehouse layout,
     * replacing any existing GIS rows.
     */
    @Transactional
    public Map<String, Integer> updateFromActiveLayout() {
        return convertActiveLayout(true);
    }

    /**
     * Converts the active warehouse layout into GIS shadow data.
     *
     * <p>
     * Uses {@link SimpleTreeLayout} to assign each block a normalized 0–100
     * bounding box, then maps those boxes to real EPSG:4326 polygons using the
     * warehouse anchor and dimensions from {@link WarehouseGisProperties}.
     *
     * <p>
     * Coordinate-system assumptions:
     * <ul>
     * <li>{@code anchorLon}/{@code anchorLat} = SW corner (minimum lon/lat).</li>
     * <li>Normalized Y=0 = north edge (max lat); Y=100 = south edge
     * (anchorLat).</li>
     * <li>1 degree ≈ 111 000 m (used for both lat and lon — sufficient for small
     * warehouses).</li>
     * </ul>
     *
     * @return map of templateName → count of GisBlocks created
     */
    private Map<String, Integer> convertActiveLayout(boolean overwriteExisting) {
        if (!overwriteExisting && gisBlockRepository.count() > 0) {
            throw GisException.conflict(
                    "Warehouse GIS layout already exists. Use /gis/layout/update to overwrite using the active layout.");
        }

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

        if (overwriteExisting) {
            // Step 3 — Clear existing GIS rows before rebuilding from active layout
            gisBlockRepository.deleteAllInBatch();
        }

        // Step 4 — Build LayoutNode tree and apply SimpleTreeLayout
        List<LayoutNode> rootNodes = LayoutNodeBuilder.buildForest(blocks, templateById);

        // Wrap all DB root nodes under a synthetic root (depth = -1) so that
        // SimpleTreeLayout divides the full 0-100 space equally among them, and
        // they naturally receive depth = -1 + 1 = 0.
        LayoutNode syntheticRoot = new LayoutNode(null, "__root__", null, null, 0);
        syntheticRoot.setDepth(-1);
        for (LayoutNode r : rootNodes) {
            syntheticRoot.addChild(r);
        }

        SimpleTreeLayout treeLayout = new SimpleTreeLayout();
        treeLayout.layout(syntheticRoot);

        // Flatten; skip the synthetic root (nodeId == null, not persisted)
        List<LayoutNode> allNodes = new ArrayList<>(treeLayout.flatten(syntheticRoot));
        allNodes.removeFirst();

        // Step 5 — Map normalized 0-100 coords → real EPSG:4326 and persist GisBlocks
        //
        // anchorLon/anchorLat = SW corner of the warehouse (minimum lon/lat).
        // Normalized X [0,100] maps linearly to [anchorLon, anchorLon + lonSpan].
        // Normalized Y [0,100] maps INVERTED: Y=0 = north (max lat), Y=100 = south
        // (anchorLat).
        double anchorLon = gisProperties.getAnchorLon();
        double anchorLat = gisProperties.getAnchorLat();
        double lonSpan = gisProperties.getWidthMeters() / 111_000.0;
        double latSpan = gisProperties.getLengthMeters() / 111_000.0;

        Map<String, Integer> counts = new HashMap<>();
        for (LayoutNode node : allNodes) {
            double minLon = anchorLon + (node.getX() / 100.0) * lonSpan;
            double maxLon = anchorLon + ((node.getX() + node.getWidth()) / 100.0) * lonSpan;
            double maxLat = anchorLat + ((100.0 - node.getY()) / 100.0) * latSpan;
            double minLat = anchorLat + ((100.0 - (node.getY() + node.getHeight())) / 100.0) * latSpan;

            // SW → SE → NE → NW → SW (closed ring)
            Polygon polygon = geometryFactory.createPolygon(new Coordinate[] {
                    new Coordinate(minLon, minLat),
                    new Coordinate(maxLon, minLat),
                    new Coordinate(maxLon, maxLat),
                    new Coordinate(minLon, maxLat),
                    new Coordinate(minLon, minLat)
            });
            Point centroid = polygon.getCentroid();

            GisBlock gisBlock = GisBlock.builder()
                    .layoutBlockId(node.getNodeId())
                    .templateName(node.getBlockType())
                    .label(node.getBlockName())
                    .positionPath(node.getBlockName())
                    .depth(node.getDepth())
                    .geometry(polygon)
                    .centroidGeom((Point) centroid)
                    .build();
            gisBlockRepository.save(gisBlock);
            counts.merge(node.getBlockType(), 1, Integer::sum);
        }

        return counts;
    }

    /**
     * Returns a GeoJSON FeatureCollection of all gis_blocks for the given
     * templateName.
     * Returns an empty FeatureCollection if no data exists yet (run generate or
     * update first).
     *
     * Geometry is serialized from the JTS Polygon in Java to avoid a dependency on
     * ST_AsGeoJSON, which is unreachable when the connection search_path is scoped
     * to
     * a tenant schema that does not include the public PostGIS schema.
     */
    public String buildGeoJsonFeatureCollection(String templateName) {
        List<GisBlock> blocks = gisBlockRepository.findAllByTemplateNameOrderByDepthAsc(templateName);

        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[");
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0)
                sb.append(",");
            GisBlock block = blocks.get(i);

            sb.append("{\"type\":\"Feature\"");
            sb.append(",\"id\":\"").append(block.getId()).append("\"");
            sb.append(",\"geometry\":").append(polygonToGeoJson(block.getGeometry()));
            sb.append(",\"properties\":{");
            sb.append("\"templateName\":\"").append(jsonEscape(templateName)).append("\"");
            sb.append(",\"label\":\"").append(jsonEscape(block.getLabel())).append("\"");
            sb.append(",\"positionPath\":\"").append(jsonEscape(block.getPositionPath())).append("\"");
            sb.append(",\"layoutBlockId\":\"").append(block.getLayoutBlockId()).append("\"");
            sb.append(",\"depth\":").append(block.getDepth());
            sb.append("}}");
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Serializes a JTS Polygon to a GeoJSON geometry string.
     * Coordinates are [longitude, latitude] (x=lon, y=lat in EPSG:4326).
     */
    private static String polygonToGeoJson(Polygon polygon) {
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
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
