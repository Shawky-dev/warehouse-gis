package com.warehouse.warehouse_platform.tenant.gis.layout;

import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplate;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a {@link LayoutNode} forest from flat lists of database entities.
 *
 * <p>
 * This is a pure static utility — no Spring beans, no I/O.
 * It is designed to consume exactly the inputs that
 * {@code LayoutToGisConversionService.generateFromActiveLayout()} and
 * {@code LayoutToGisConversionService.updateFromActiveLayout()} already load:
 * <ul>
 * <li>The flat list from
 * {@code layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc()}</li>
 * <li>The {@code Map<UUID, BlockTemplate>} already built in that method.</li>
 * </ul>
 */
public final class LayoutNodeBuilder {

    private LayoutNodeBuilder() {
    }

    /**
     * Assembles a forest (list of root {@link LayoutNode}s) from the flat entity
     * lists.
     *
     * <p>
     * Two-pass algorithm:
     * <ol>
     * <li>Create one {@link LayoutNode} per {@link LayoutBlock}.</li>
     * <li>Wire parent→child relationships; collect nodes whose
     * {@code parentId == null} as roots.</li>
     * </ol>
     *
     * <p>
     * Orphan nodes (non-null {@code parentId} but missing parent) are treated
     * as additional roots rather than silently dropped.
     *
     * @param blocks       flat list of all {@link LayoutBlock} rows for the active
     *                     layout,
     *                     ordered by parentId ASC, position ASC
     * @param templateById map from {@link BlockTemplate#getId()} to
     *                     {@link BlockTemplate}
     * @return list of root-level nodes, each with its full child subtree attached
     */
    public static List<LayoutNode> buildForest(
            List<LayoutBlock> blocks,
            Map<UUID, BlockTemplate> templateById) {

        // Pass 1: create LayoutNode for every LayoutBlock
        Map<UUID, LayoutNode> nodeById = new LinkedHashMap<>(blocks.size() * 2);
        for (LayoutBlock block : blocks) {
            BlockTemplate template = templateById.get(block.getBlockTemplateId());
            String blockType = template != null ? template.getName() : "Unknown";
            LayoutNode node = new LayoutNode(
                    block.getId(),
                    blockType,
                    block.getFullCode(),
                    block.getSide(),
                    block.getPosition());
            nodeById.put(block.getId(), node);
        }

        // Pass 2: wire relationships; collect roots
        List<LayoutNode> roots = new ArrayList<>();
        for (LayoutBlock block : blocks) {
            LayoutNode node = nodeById.get(block.getId());
            if (block.getParentId() == null) {
                roots.add(node);
            } else {
                LayoutNode parent = nodeById.get(block.getParentId());
                if (parent != null) {
                    parent.addChild(node);
                } else {
                    // Orphan node — treat as root to avoid data loss
                    roots.add(node);
                }
            }
        }

        // Children are already in position order from the DB query,
        // but sort defensively in case order is not guaranteed.
        sortChildrenRecursively(roots);

        return roots;
    }

    private static void sortChildrenRecursively(List<LayoutNode> nodes) {
        for (LayoutNode node : nodes) {
            node.getChildren().sort(Comparator.comparingInt(LayoutNode::getPosition));
            sortChildrenRecursively(node.getChildren());
        }
    }
}
