package com.warehouse.warehouse_platform.tenant.gis.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic tree layout algorithm for warehouse floor plans.
 *
 * <p>Takes a {@link LayoutNode} tree and assigns normalized 0–100 coordinates
 * ({@code x}, {@code y}, {@code width}, {@code height}) to every node by
 * recursively dividing each parent's space into equal horizontal strips
 * (top-to-bottom) among its children.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>No sibling overlaps — each child gets an exclusive strip.</li>
 *   <li>All coordinates stay within the root's bounds.</li>
 *   <li>O(n) time complexity.</li>
 *   <li>Deterministic — identical input always produces identical output.</li>
 * </ul>
 *
 * <p>The {@code side} field on {@link LayoutNode} is intentionally ignored here.
 * It is physical metadata (left/right side of an aisle) that does not affect
 * the geometric subdivision in this top-down layout view.
 */
public class SimpleTreeLayout {

    private static final double BOUNDS_TOLERANCE = 0.0001;

    /**
     * Applies the layout algorithm to the entire tree rooted at {@code root}.
     *
     * <p>After this call every node in the tree has valid {@code x}, {@code y},
     * {@code width}, {@code height}, and {@code depth} values.
     *
     * <p><b>Depth note:</b> to make top-level DB blocks receive {@code depth=0},
     * set {@code root.setDepth(-1)} on a synthetic wrapper root before calling
     * this method. The algorithm always assigns {@code child.depth = parent.depth + 1}.
     *
     * @param root the root node (may be a synthetic wrapper node)
     */
    public void layout(LayoutNode root) {
        root.setX(0.0);
        root.setY(0.0);
        root.setWidth(100.0);
        root.setHeight(100.0);
        layoutChildren(root);
    }

    /**
     * Recursively divides {@code parent}'s height equally among its children,
     * stacking them as horizontal strips (top to bottom).
     */
    private void layoutChildren(LayoutNode parent) {
        List<LayoutNode> children = parent.getChildren();
        if (children.isEmpty()) {
            return;
        }

        int n = children.size();
        double sliceHeight = parent.getHeight() / n;

        for (int i = 0; i < n; i++) {
            LayoutNode child = children.get(i);
            child.setX(parent.getX());
            child.setY(parent.getY() + i * sliceHeight);
            child.setWidth(parent.getWidth());
            child.setHeight(sliceHeight);
            child.setDepth(parent.getDepth() + 1);
        }

        // Snap the last child's bottom edge to the parent's bottom to absorb
        // any floating-point rounding error accumulated over many divisions.
        LayoutNode last = children.get(n - 1);
        last.setHeight(parent.getY() + parent.getHeight() - last.getY());

        // Recurse
        for (LayoutNode child : children) {
            layoutChildren(child);
        }
    }

    /**
     * Returns all nodes in DFS pre-order (parent before its children).
     * The root itself is included as the first element.
     */
    public List<LayoutNode> flatten(LayoutNode root) {
        List<LayoutNode> result = new ArrayList<>();
        dfs(root, result);
        return result;
    }

    private void dfs(LayoutNode node, List<LayoutNode> acc) {
        acc.add(node);
        for (LayoutNode child : node.getChildren()) {
            dfs(child, acc);
        }
    }

    /**
     * Validates the layout of the tree rooted at {@code root}.
     *
     * <p>Checks:
     * <ol>
     *   <li>Every node's bounding box lies within 0–100 (with a small float tolerance).</li>
     *   <li>No two siblings overlap spatially.</li>
     * </ol>
     *
     * @throws IllegalStateException if any validation check fails
     */
    public void validate(LayoutNode root) {
        List<LayoutNode> all = flatten(root);

        // 1. Bounds check
        for (LayoutNode node : all) {
            if (node.getX() < -BOUNDS_TOLERANCE
                    || node.getY() < -BOUNDS_TOLERANCE
                    || node.getX() + node.getWidth()  > 100.0 + BOUNDS_TOLERANCE
                    || node.getY() + node.getHeight() > 100.0 + BOUNDS_TOLERANCE) {
                throw new IllegalStateException(
                        "Node " + node.getNodeId() + " (" + node.getBlockType() + ") is out of bounds: "
                        + "x=" + node.getX() + " y=" + node.getY()
                        + " w=" + node.getWidth() + " h=" + node.getHeight());
            }
        }

        // 2. Sibling overlap check
        for (LayoutNode node : all) {
            List<LayoutNode> siblings = node.getChildren();
            for (int i = 0; i < siblings.size(); i++) {
                for (int j = i + 1; j < siblings.size(); j++) {
                    if (overlaps(siblings.get(i), siblings.get(j))) {
                        throw new IllegalStateException(
                                "Sibling overlap detected between "
                                + siblings.get(i).getNodeId() + " and "
                                + siblings.get(j).getNodeId()
                                + " under parent " + node.getNodeId());
                    }
                }
            }
        }
    }

    /**
     * Returns {@code true} if the two nodes' bounding boxes overlap.
     * Touching edges (shared boundary) are NOT considered overlaps.
     */
    private boolean overlaps(LayoutNode a, LayoutNode b) {
        return a.getX() < b.getX() + b.getWidth()  - BOUNDS_TOLERANCE
            && b.getX() < a.getX() + a.getWidth()  - BOUNDS_TOLERANCE
            && a.getY() < b.getY() + b.getHeight() - BOUNDS_TOLERANCE
            && b.getY() < a.getY() + a.getHeight() - BOUNDS_TOLERANCE;
    }
}
