package com.warehouse.warehouse_platform.tenant.gis.layout;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimpleTreeLayoutTest {

    private static final double EPSILON = 1e-9;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static LayoutNode node(String blockType) {
        return new LayoutNode(UUID.randomUUID(), blockType, blockType + "-code", null, 0);
    }

    private static LayoutNode node(String blockType, int position) {
        return new LayoutNode(UUID.randomUUID(), blockType, blockType + "-code", null, position);
    }

    private static SimpleTreeLayout layout() {
        return new SimpleTreeLayout();
    }

    // ── Root assignment ───────────────────────────────────────────────────────

    @Test
    void rootGetsFullNormalizedSpace() {
        LayoutNode root = node("Warehouse");
        layout().layout(root);

        assertEquals(0.0,   root.getX(),      EPSILON);
        assertEquals(0.0,   root.getY(),      EPSILON);
        assertEquals(100.0, root.getWidth(),  EPSILON);
        assertEquals(100.0, root.getHeight(), EPSILON);
    }

    @Test
    void rootWithoutChildrenHasDepthZero() {
        LayoutNode root = node("Warehouse");
        layout().layout(root);
        assertEquals(0, root.getDepth());
    }

    // ── Single child ──────────────────────────────────────────────────────────

    @Test
    void singleChildInheritsFullParentBounds() {
        LayoutNode root  = node("Warehouse");
        LayoutNode child = node("Zone");
        root.addChild(child);

        layout().layout(root);

        assertEquals(0.0,   child.getX(),      EPSILON);
        assertEquals(0.0,   child.getY(),      EPSILON);
        assertEquals(100.0, child.getWidth(),  EPSILON);
        assertEquals(100.0, child.getHeight(), EPSILON);
    }

    // ── Two children ──────────────────────────────────────────────────────────

    @Test
    void twoChildrenSplitHeightEqually() {
        LayoutNode root = node("Warehouse");
        LayoutNode a    = node("Zone", 0);
        LayoutNode b    = node("Zone", 1);
        root.addChild(a);
        root.addChild(b);

        layout().layout(root);

        // First child: top half
        assertEquals(0.0,  a.getY(),      EPSILON);
        assertEquals(50.0, a.getHeight(), EPSILON);

        // Second child: bottom half
        assertEquals(50.0, b.getY(),      EPSILON);
        assertEquals(50.0, b.getHeight(), EPSILON);

        // Both children span full width
        assertEquals(100.0, a.getWidth(), EPSILON);
        assertEquals(100.0, b.getWidth(), EPSILON);
    }

    @Test
    void twoChildrenDoNotOverlap() {
        LayoutNode root = node("Warehouse");
        root.addChild(node("Zone", 0));
        root.addChild(node("Zone", 1));

        SimpleTreeLayout tl = layout();
        tl.layout(root);
        assertDoesNotThrow(() -> tl.validate(root));
    }

    // ── Three children ────────────────────────────────────────────────────────

    @Test
    void threeChildrenDivideIntoEqualThirds() {
        LayoutNode root = node("Warehouse");
        LayoutNode a = node("Aisle", 0);
        LayoutNode b = node("Aisle", 1);
        LayoutNode c = node("Aisle", 2);
        root.addChild(a);
        root.addChild(b);
        root.addChild(c);

        layout().layout(root);

        assertEquals(0.0,              a.getY(), EPSILON);
        assertEquals(100.0 / 3.0,     b.getY(), EPSILON);
        assertEquals(100.0 * 2.0 / 3, c.getY(), EPSILON);

        // Last child snapped to parent bottom — total must be exactly 100
        double totalHeight = a.getHeight() + b.getHeight() + c.getHeight();
        assertEquals(100.0, totalHeight, EPSILON);
    }

    @Test
    void threeChildrenValidate() {
        LayoutNode root = node("Warehouse");
        root.addChild(node("Aisle", 0));
        root.addChild(node("Aisle", 1));
        root.addChild(node("Aisle", 2));

        SimpleTreeLayout tl = layout();
        tl.layout(root);
        assertDoesNotThrow(() -> tl.validate(root));
    }

    // ── Depth correctness ─────────────────────────────────────────────────────

    @Test
    void depthIncreasesWithTreeLevel() {
        LayoutNode root  = node("Warehouse");
        LayoutNode child = node("Zone");
        LayoutNode grand = node("Aisle");
        root.addChild(child);
        child.addChild(grand);

        layout().layout(root);

        assertEquals(0, root.getDepth());
        assertEquals(1, child.getDepth());
        assertEquals(2, grand.getDepth());
    }

    @Test
    void syntheticRootAtMinusOneGivesDbRootsDepthZero() {
        LayoutNode syntheticRoot = new LayoutNode(null, "__root__", null, null, 0);
        syntheticRoot.setDepth(-1);

        LayoutNode dbRoot1 = node("Zone");
        LayoutNode dbRoot2 = node("Zone");
        syntheticRoot.addChild(dbRoot1);
        syntheticRoot.addChild(dbRoot2);

        LayoutNode child = node("Aisle");
        dbRoot1.addChild(child);

        SimpleTreeLayout tl = layout();
        tl.layout(syntheticRoot);

        assertEquals(0, dbRoot1.getDepth(), "DB root should have depth 0");
        assertEquals(0, dbRoot2.getDepth(), "DB root should have depth 0");
        assertEquals(1, child.getDepth(),   "Child of DB root should have depth 1");
    }

    // ── Flatten ───────────────────────────────────────────────────────────────

    @Test
    void flattenReturnsAllNodesInPreOrder() {
        LayoutNode root  = node("Warehouse");
        LayoutNode child = node("Zone");
        LayoutNode grand = node("Aisle");
        root.addChild(child);
        child.addChild(grand);

        List<LayoutNode> flat = layout().flatten(root);

        assertEquals(3, flat.size());
        assertSame(root,  flat.get(0));
        assertSame(child, flat.get(1));
        assertSame(grand, flat.get(2));
    }

    @Test
    void flattenSingleNodeReturnsSingletonList() {
        LayoutNode root = node("Warehouse");
        List<LayoutNode> flat = layout().flatten(root);
        assertEquals(1, flat.size());
        assertSame(root, flat.getFirst());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void validatePassesOnWellFormedTree() {
        LayoutNode root = node("Warehouse");
        LayoutNode a = node("Aisle", 0);
        LayoutNode b = node("Aisle", 1);
        LayoutNode shelf1 = node("Shelf", 0);
        LayoutNode shelf2 = node("Shelf", 1);
        root.addChild(a);
        root.addChild(b);
        a.addChild(shelf1);
        a.addChild(shelf2);

        SimpleTreeLayout tl = layout();
        tl.layout(root);
        assertDoesNotThrow(() -> tl.validate(root));
    }

    @Test
    void validateFailsWhenNodeOutOfBounds() {
        LayoutNode root = node("Warehouse");
        SimpleTreeLayout tl = layout();
        tl.layout(root);

        // Manually break the layout
        root.setX(-1.0);

        assertThrows(IllegalStateException.class, () -> tl.validate(root));
    }

    // ── Coordinate coverage (all blocks stay in 0-100) ────────────────────────

    @Test
    void allNodesStayWithin0to100AfterLayout() {
        // Build a 3-level tree
        LayoutNode root = node("Warehouse");
        for (int i = 0; i < 3; i++) {
            LayoutNode aisle = node("Aisle", i);
            for (int j = 0; j < 4; j++) {
                LayoutNode shelf = node("Shelf", j);
                aisle.addChild(shelf);
            }
            root.addChild(aisle);
        }

        SimpleTreeLayout tl = layout();
        tl.layout(root);

        List<LayoutNode> all = tl.flatten(root);
        for (LayoutNode n : all) {
            assertTrue(n.getX()              >= -1e-9, "x out of range for " + n.getBlockType());
            assertTrue(n.getY()              >= -1e-9, "y out of range for " + n.getBlockType());
            assertTrue(n.getX() + n.getWidth()  <= 100 + 1e-9, "right edge out of range for " + n.getBlockType());
            assertTrue(n.getY() + n.getHeight() <= 100 + 1e-9, "bottom edge out of range for " + n.getBlockType());
        }
    }

    // ── Side field is ignored ─────────────────────────────────────────────────

    @Test
    void sideFieldDoesNotAffectLayout() {
        // Two trees: one with side=L/R, one without. Layout must be identical.
        LayoutNode root1 = node("Warehouse");
        LayoutNode noSide1 = new LayoutNode(UUID.randomUUID(), "Bay", "Bay-code", null, 0);
        LayoutNode noSide2 = new LayoutNode(UUID.randomUUID(), "Bay", "Bay-code", null, 1);
        root1.addChild(noSide1);
        root1.addChild(noSide2);

        LayoutNode root2 = node("Warehouse");
        LayoutNode withL = new LayoutNode(UUID.randomUUID(), "Bay", "Bay-code", "L", 0);
        LayoutNode withR = new LayoutNode(UUID.randomUUID(), "Bay", "Bay-code", "R", 1);
        root2.addChild(withL);
        root2.addChild(withR);

        layout().layout(root1);
        layout().layout(root2);

        assertEquals(noSide1.getY(),      withL.getY(),      EPSILON);
        assertEquals(noSide1.getHeight(), withL.getHeight(), EPSILON);
        assertEquals(noSide2.getY(),      withR.getY(),      EPSILON);
        assertEquals(noSide2.getHeight(), withR.getHeight(), EPSILON);
    }
}
