package com.warehouse.warehouse_platform.tenant.warehouse.block;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LayoutBlockService {

    private final LayoutBlockRepository layoutBlockRepository;
    private final BlockTemplateRepository blockTemplateRepository;
    private final WarehouseLayoutRepository layoutRepository;
    private final TenantAuditService tenantAuditService;

    public LayoutBlockService(
            LayoutBlockRepository layoutBlockRepository,
            BlockTemplateRepository blockTemplateRepository,
            WarehouseLayoutRepository layoutRepository,
            TenantAuditService tenantAuditService) {
        this.layoutBlockRepository = layoutBlockRepository;
        this.blockTemplateRepository = blockTemplateRepository;
        this.layoutRepository = layoutRepository;
        this.tenantAuditService = tenantAuditService;
    }

    /**
     * Returns the full block tree for a layout, with children nested under their parent.
     */
    @Transactional(readOnly = true)
    public List<BlockNode> getTree(UUID layoutId) {
        assertLayoutExists(layoutId);
        List<LayoutBlock> all = layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(layoutId);
        return buildTree(all, null);
    }

    @Transactional(readOnly = true)
    public BlockResult getBlock(UUID layoutId, UUID blockId) {
        assertLayoutExists(layoutId);
        LayoutBlock block = loadBlock(blockId);
        assertBelongsToLayout(block, layoutId);
        return toResult(block);
    }

    /**
     * Adds a block to the layout.
     * If parentId is null, the block is added at the root level.
     * If position is null, it is appended after the last sibling.
     */
    @Transactional
    public BlockResult addBlock(UUID layoutId, UUID blockTemplateId, UUID parentId, Integer position) {
        assertLayoutExists(layoutId);
        assertTemplateExists(blockTemplateId);

        if (parentId != null) {
            LayoutBlock parent = loadBlock(parentId);
            assertBelongsToLayout(parent, layoutId);
        }

        int resolvedPosition = resolvePosition(layoutId, parentId, null, position);

        // Shift existing siblings up to make room
        shiftPositions(layoutId, parentId, resolvedPosition, +1, null);

        LayoutBlock block = LayoutBlock.builder()
                .layoutId(layoutId)
                .blockTemplateId(blockTemplateId)
                .parentId(parentId)
                .position(resolvedPosition)
                .build();

        LayoutBlock saved = layoutBlockRepository.save(block);
        BlockResult result = toResult(saved);
        tenantAuditService.record("LAYOUT_BLOCK_ADD", "LAYOUT_BLOCK", result.id().toString(), null, result);
        return result;
    }

    /**
     * Moves a block to a new parent and/or position.
     * Also supports re-parenting (changing the block's parent).
     */
    @Transactional
    public BlockResult moveBlock(UUID layoutId, UUID blockId, UUID newParentId, int newPosition) {
        assertLayoutExists(layoutId);
        LayoutBlock block = loadBlock(blockId);
        assertBelongsToLayout(block, layoutId);

        if (newParentId != null) {
            LayoutBlock newParent = loadBlock(newParentId);
            assertBelongsToLayout(newParent, layoutId);
            // Prevent a block from being moved under itself or its own descendant
            assertNotDescendant(layoutId, blockId, newParentId);
        }

        UUID oldParentId = block.getParentId();
        int oldPosition = block.getPosition();
        BlockResult before = toResult(block);

        // Temporarily remove from old position (shift siblings down)
        shiftPositions(layoutId, oldParentId, oldPosition + 1, -1, blockId);

        // If same parent, recalculate position after the shift
        int resolvedNew = newPosition;
        if (java.util.Objects.equals(oldParentId, newParentId) && newPosition > oldPosition) {
            resolvedNew = newPosition - 1;
        }

        // Make room at new position
        shiftPositions(layoutId, newParentId, resolvedNew, +1, blockId);

        block.setParentId(newParentId);
        block.setPosition(resolvedNew);
        LayoutBlock saved = layoutBlockRepository.save(block);

        BlockResult after = toResult(saved);
        tenantAuditService.record("LAYOUT_BLOCK_MOVE", "LAYOUT_BLOCK", blockId.toString(), before, after);
        return after;
    }

    /**
     * Replaces the block template of a layout block.
     */
    @Transactional
    public BlockResult reassignTemplate(UUID layoutId, UUID blockId, UUID newTemplateId) {
        assertLayoutExists(layoutId);
        assertTemplateExists(newTemplateId);
        LayoutBlock block = loadBlock(blockId);
        assertBelongsToLayout(block, layoutId);

        BlockResult before = toResult(block);
        block.setBlockTemplateId(newTemplateId);
        LayoutBlock saved = layoutBlockRepository.save(block);
        BlockResult after = toResult(saved);
        tenantAuditService.record("LAYOUT_BLOCK_REASSIGN", "LAYOUT_BLOCK", blockId.toString(), before, after);
        return after;
    }

    /**
     * Removes a block (and cascades deletion to all its descendants via DB ON DELETE CASCADE).
     * Shifts remaining siblings to fill the gap.
     */
    @Transactional
    public void removeBlock(UUID layoutId, UUID blockId) {
        assertLayoutExists(layoutId);
        LayoutBlock block = loadBlock(blockId);
        assertBelongsToLayout(block, layoutId);

        BlockResult before = toResult(block);
        UUID parentId = block.getParentId();
        int position = block.getPosition();

        layoutBlockRepository.delete(block);

        // Close the gap in the sibling list
        shiftPositions(layoutId, parentId, position + 1, -1, null);

        tenantAuditService.record("LAYOUT_BLOCK_REMOVE", "LAYOUT_BLOCK", blockId.toString(), before, null);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int resolvePosition(UUID layoutId, UUID parentId, UUID excludeId, Integer requestedPosition) {
        if (requestedPosition != null) {
            if (requestedPosition < 0) {
                throw WarehouseManagementException.badRequest("position must be >= 0");
            }
            return requestedPosition;
        }
        // Append at the end
        Integer max = (parentId == null)
                ? layoutBlockRepository.findMaxRootPosition(layoutId)
                : layoutBlockRepository.findMaxChildPosition(layoutId, parentId);
        return (max == null) ? 0 : max + 1;
    }

    /**
     * Shifts the position of all siblings at or after {@code fromPosition} by {@code delta}.
     * Optionally excludes a specific block (useful when the block being moved is still in the list).
     */
    private void shiftPositions(UUID layoutId, UUID parentId, int fromPosition, int delta, UUID excludeBlockId) {
        List<LayoutBlock> siblings = (parentId == null)
                ? layoutBlockRepository.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(layoutId)
                : layoutBlockRepository.findByLayoutIdAndParentIdOrderByPositionAsc(layoutId, parentId);

        List<LayoutBlock> toUpdate = new ArrayList<>();
        for (LayoutBlock sibling : siblings) {
            if (excludeBlockId != null && sibling.getId().equals(excludeBlockId)) continue;
            if (sibling.getPosition() >= fromPosition) {
                sibling.setPosition(sibling.getPosition() + delta);
                toUpdate.add(sibling);
            }
        }
        if (!toUpdate.isEmpty()) {
            layoutBlockRepository.saveAll(toUpdate);
        }
    }

    /** Recursively checks that {@code candidateAncestorId} is not a descendant of {@code blockId}. */
    private void assertNotDescendant(UUID layoutId, UUID blockId, UUID candidateAncestorId) {
        List<LayoutBlock> all = layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(layoutId);
        // Build a child map
        Map<UUID, List<UUID>> childMap = new HashMap<>();
        for (LayoutBlock b : all) {
            if (b.getParentId() != null) {
                childMap.computeIfAbsent(b.getParentId(), k -> new ArrayList<>()).add(b.getId());
            }
        }
        if (isDescendant(childMap, blockId, candidateAncestorId)) {
            throw WarehouseManagementException.badRequest(
                    "Cannot move a block under one of its own descendants");
        }
    }

    private boolean isDescendant(Map<UUID, List<UUID>> childMap, UUID rootId, UUID targetId) {
        List<UUID> children = childMap.getOrDefault(rootId, List.of());
        for (UUID child : children) {
            if (child.equals(targetId)) return true;
            if (isDescendant(childMap, child, targetId)) return true;
        }
        return false;
    }

    private List<BlockNode> buildTree(List<LayoutBlock> all, UUID parentId) {
        List<BlockNode> nodes = new ArrayList<>();
        for (LayoutBlock b : all) {
            if (java.util.Objects.equals(b.getParentId(), parentId)) {
                List<BlockNode> children = buildTree(all, b.getId());
                nodes.add(new BlockNode(toResult(b), children));
            }
        }
        nodes.sort(java.util.Comparator.comparingInt(n -> n.block().position()));
        return nodes;
    }

    private void assertLayoutExists(UUID layoutId) {
        if (!layoutRepository.existsById(layoutId)) {
            throw WarehouseManagementException.notFound("Layout not found: " + layoutId);
        }
    }

    private void assertTemplateExists(UUID templateId) {
        if (!blockTemplateRepository.existsById(templateId)) {
            throw WarehouseManagementException.notFound("Block template not found: " + templateId);
        }
    }

    private void assertBelongsToLayout(LayoutBlock block, UUID layoutId) {
        if (!block.getLayoutId().equals(layoutId)) {
            throw WarehouseManagementException.notFound("Block not found in this layout");
        }
    }

    private LayoutBlock loadBlock(UUID blockId) {
        return layoutBlockRepository.findById(blockId)
                .orElseThrow(() -> WarehouseManagementException.notFound("Layout block not found: " + blockId));
    }

    private BlockResult toResult(LayoutBlock b) {
        return new BlockResult(
                b.getId(),
                b.getLayoutId(),
                b.getBlockTemplateId(),
                b.getParentId(),
                b.getPosition(),
                b.getCreatedAt(),
                b.getUpdatedAt());
    }

    public record BlockResult(
            UUID id,
            UUID layoutId,
            UUID blockTemplateId,
            UUID parentId,
            int position,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record BlockNode(BlockResult block, List<BlockNode> children) {
    }
}
