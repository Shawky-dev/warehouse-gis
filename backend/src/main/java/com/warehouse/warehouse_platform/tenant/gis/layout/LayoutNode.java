package com.warehouse.warehouse_platform.tenant.gis.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * In-memory tree node representing one warehouse block during GIS layout computation.
 *
 * This is a plain Java object — NOT a JPA entity. It is distinct from
 * {@link com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlock}, which is the
 * database entity. The name "LayoutNode" is intentional to avoid confusion.
 *
 * Instances are built by {@link LayoutNodeBuilder} from database entities, then
 * positioned by {@link SimpleTreeLayout}, and finally mapped to real EPSG:4326
 * coordinates inside {@code LayoutToGisConversionService}.
 */
public class LayoutNode {

    // ── Identity ─────────────────────────────────────────────────────────────

    /** Corresponds to {@code LayoutBlock.id}. Null only for the synthetic root. */
    private final UUID nodeId;

    /** Corresponds to {@code BlockTemplate.name}, e.g. "Zone", "Aisle", "Shelf". */
    private final String blockType;

    /** Corresponds to {@code LayoutBlock.fullCode}. Used as label and positionPath in GisBlock. */
    private final String blockName;

    /**
     * Corresponds to {@code LayoutBlock.side} (nullable, e.g. "L", "R", "A", "B").
     * Stored for reference but NOT used by {@link SimpleTreeLayout} — all siblings
     * receive equal space regardless of their side value.
     */
    private final String side;

    /** Corresponds to {@code LayoutBlock.position} (0-based ordering within parent). */
    private final int position;

    // ── Tree structure ────────────────────────────────────────────────────────

    private LayoutNode parent;
    private final List<LayoutNode> children = new ArrayList<>();

    // ── Normalized layout (0–100 space, set by SimpleTreeLayout) ─────────────

    private double x;
    private double y;
    private double width;
    private double height;
    private int depth;

    public LayoutNode(UUID nodeId, String blockType, String blockName, String side, int position) {
        this.nodeId = nodeId;
        this.blockType = blockType;
        this.blockName = blockName;
        this.side = side;
        this.position = position;
    }

    public void addChild(LayoutNode child) {
        children.add(child);
        child.parent = this;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getNodeId()          { return nodeId; }
    public String getBlockType()     { return blockType; }
    public String getBlockName()     { return blockName; }
    public String getSide()          { return side; }
    public int getPosition()         { return position; }
    public LayoutNode getParent()    { return parent; }
    public List<LayoutNode> getChildren() { return children; }
    public double getX()             { return x; }
    public double getY()             { return y; }
    public double getWidth()         { return width; }
    public double getHeight()        { return height; }
    public int getDepth()            { return depth; }

    // ── Setters (layout fields only) ──────────────────────────────────────────

    public void setX(double x)           { this.x = x; }
    public void setY(double y)           { this.y = y; }
    public void setWidth(double width)   { this.width = width; }
    public void setHeight(double height) { this.height = height; }
    public void setDepth(int depth)      { this.depth = depth; }

    @Override
    public String toString() {
        return String.format("LayoutNode{id=%s, type=%s, x=%.2f, y=%.2f, w=%.2f, h=%.2f, depth=%d}",
                nodeId, blockType, x, y, width, height, depth);
    }
}
