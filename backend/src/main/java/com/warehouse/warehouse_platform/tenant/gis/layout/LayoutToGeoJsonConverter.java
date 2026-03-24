package com.warehouse.warehouse_platform.tenant.gis.layout;

import java.util.List;

/**
 * Converts a list of laid-out {@link LayoutNode}s into a GeoJSON FeatureCollection string.
 *
 * <p><b>Note:</b> This class is NOT wired into the main conversion pipeline.
 * The live GeoJSON path uses {@code ST_AsGeoJSON} via the database (more efficient).
 * This converter exists for unit testing and ad-hoc use only.
 *
 * <p>Precondition: each node's {@code x}/{@code y}/{@code width}/{@code height} must
 * already be in real EPSG:4326 coordinates (lon/lat), not normalized 0–100 space.
 */
public final class LayoutToGeoJsonConverter {

    private LayoutToGeoJsonConverter() {}

    /**
     * Produces a GeoJSON FeatureCollection from a list of {@link LayoutNode}s.
     *
     * @param nodes list of nodes with real lon/lat coordinates
     * @return GeoJSON string
     */
    public static String toFeatureCollection(List<LayoutNode> nodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"FeatureCollection\",\"features\":[");
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) sb.append(",");
            appendFeature(sb, nodes.get(i));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void appendFeature(StringBuilder sb, LayoutNode node) {
        double minLon = node.getX();
        double minLat = node.getY();
        double maxLon = node.getX() + node.getWidth();
        double maxLat = node.getY() + node.getHeight();

        sb.append("{\"type\":\"Feature\"");
        if (node.getNodeId() != null) {
            sb.append(",\"id\":\"").append(node.getNodeId()).append("\"");
        }
        sb.append(",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[");
        appendCoord(sb, minLon, minLat); sb.append(",");
        appendCoord(sb, maxLon, minLat); sb.append(",");
        appendCoord(sb, maxLon, maxLat); sb.append(",");
        appendCoord(sb, minLon, maxLat); sb.append(",");
        appendCoord(sb, minLon, minLat);
        sb.append("]]}");
        sb.append(",\"properties\":{");
        sb.append("\"templateName\":\"").append(escape(node.getBlockType())).append("\"");
        sb.append(",\"label\":\"").append(escape(node.getBlockName())).append("\"");
        sb.append(",\"depth\":").append(node.getDepth());
        sb.append("}}");
    }

    private static void appendCoord(StringBuilder sb, double lon, double lat) {
        sb.append("[").append(lon).append(",").append(lat).append("]");
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
