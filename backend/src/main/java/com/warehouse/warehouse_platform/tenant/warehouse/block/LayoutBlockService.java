package com.warehouse.warehouse_platform.tenant.warehouse.block;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * Returns the full block tree for a layout, with children nested under their
     * parent.
     */
    @Transactional(readOnly = true)
    public List<BlockNode> getTree(UUID layoutId) {
        assertLayoutExists(layoutId);
        List<LayoutBlock> all = layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(layoutId);
        return buildTree(all, null, loadTemplateMap(all));
    }

    @Transactional(readOnly = true)
    public BlockResult getBlock(UUID layoutId, UUID blockId) {
        assertLayoutExists(layoutId);
        LayoutBlock block = loadBlock(blockId);
        assertBelongsToLayout(block, layoutId);
        return toResult(block, loadTemplate(block.getBlockTemplateId()));
    }

    /**
     * Adds a block to the layout.
     * If parentId is null, the block is added at the root level.
     * If position is null, it is appended after the last sibling.
     */
    @Transactional
    public BlockResult addBlock(UUID layoutId, UUID blockTemplateId, UUID parentId, Integer position, String side) {
        assertLayoutExists(layoutId);
        BlockTemplate template = loadTemplate(blockTemplateId);

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
                .side(normalizeSide(side, template))
                .build();

        LayoutBlock saved = saveBlock(block);
        BlockResult result = toResult(saved, template);
        tenantAuditService.record("LAYOUT_BLOCK_ADD", "LAYOUT_BLOCK", result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public BatchBlockResult addBlocks(
            UUID layoutId,
            UUID blockTemplateId,
            UUID parentId,
            Integer position,
            int count,
            String side) {
        assertLayoutExists(layoutId);
        if (count < 1) {
            throw WarehouseManagementException.badRequest("count must be >= 1");
        }

        BlockTemplate template = loadTemplate(blockTemplateId);

        if (parentId != null) {
            LayoutBlock parent = loadBlock(parentId);
            assertBelongsToLayout(parent, layoutId);
        }

        int resolvedPosition = resolvePosition(layoutId, parentId, null, position);
        shiftPositions(layoutId, parentId, resolvedPosition, count, null);

        List<BlockResult> createdBlocks = new ArrayList<>();
        String normalizedSide = normalizeSide(side, template);
        for (int index = 0; index < count; index++) {
            LayoutBlock block = LayoutBlock.builder()
                    .layoutId(layoutId)
                    .blockTemplateId(blockTemplateId)
                    .parentId(parentId)
                    .position(resolvedPosition + index)
                    .side(normalizedSide)
                    .build();
            LayoutBlock saved = saveBlock(block);
            createdBlocks.add(toResult(saved, template));
        }

        BatchBlockResult result = new BatchBlockResult(createdBlocks, createdBlocks.size(), createdBlocks.size());
        tenantAuditService.record("LAYOUT_BLOCK_BATCH_ADD", "LAYOUT_BLOCK", layoutId.toString(), null, result);
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
        BlockResult before = toResult(block, loadTemplate(block.getBlockTemplateId()));

        // Park the block at a temporary position outside the sibling range so that
        // the subsequent shifts do not collide with its current position in the DB.
        Integer currentMax = (oldParentId == null)
                ? layoutBlockRepository.findMaxRootPosition(layoutId)
                : layoutBlockRepository.findMaxChildPosition(layoutId, oldParentId);
        block.setPosition((currentMax == null ? 0 : currentMax) + 1);
        layoutBlockRepository.saveAndFlush(block);

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
        LayoutBlock saved = saveBlock(block);

        BlockResult after = toResult(saved, loadTemplate(saved.getBlockTemplateId()));
        tenantAuditService.record("LAYOUT_BLOCK_MOVE", "LAYOUT_BLOCK", blockId.toString(), before, after);
        return after;
    }

    /**
     * Replaces the block template of a layout block.
     */
    @Transactional
    public BlockResult reassignTemplate(UUID layoutId, UUID blockId, UUID newTemplateId) {
        assertLayoutExists(layoutId);
        BlockTemplate template = loadTemplate(newTemplateId);
        LayoutBlock block = loadBlock(blockId);
        assertBelongsToLayout(block, layoutId);

        BlockResult before = toResult(block, loadTemplate(block.getBlockTemplateId()));
        block.setSide(retainCompatibleSide(block.getSide(), template));
        block.setBlockTemplateId(newTemplateId);
        LayoutBlock saved = saveBlock(block);
        BlockResult after = toResult(saved, template);
        tenantAuditService.record("LAYOUT_BLOCK_REASSIGN", "LAYOUT_BLOCK", blockId.toString(), before, after);
        return after;
    }

    @Transactional
    public BlockResult updateMetadata(UUID layoutId, UUID blockId, String side) {
        assertLayoutExists(layoutId);
        LayoutBlock block = loadBlock(blockId);
        assertBelongsToLayout(block, layoutId);

        BlockTemplate template = loadTemplate(block.getBlockTemplateId());
        BlockResult before = toResult(block, template);
        block.setSide(normalizeSide(side, template));
        LayoutBlock saved = saveBlock(block);
        BlockResult after = toResult(saved, template);
        tenantAuditService.record("LAYOUT_BLOCK_UPDATE_METADATA", "LAYOUT_BLOCK", blockId.toString(), before, after);
        return after;
    }

    /**
     * Removes a block (and cascades deletion to all its descendants via DB ON
     * DELETE CASCADE).
     * Shifts remaining siblings to fill the gap.
     */
    @Transactional
    public void removeBlock(UUID layoutId, UUID blockId) {
        assertLayoutExists(layoutId);
        LayoutBlock block = loadBlock(blockId);
        assertBelongsToLayout(block, layoutId);

        BlockResult before = toResult(block, loadTemplate(block.getBlockTemplateId()));
        UUID parentId = block.getParentId();
        int position = block.getPosition();

        layoutBlockRepository.delete(block);

        // Close the gap in the sibling list
        shiftPositions(layoutId, parentId, position + 1, -1, null);

        tenantAuditService.record("LAYOUT_BLOCK_REMOVE", "LAYOUT_BLOCK", blockId.toString(), before, null);
    }

    @Transactional
    public BatchBlockResult copySubtree(
            UUID layoutId,
            UUID sourceBlockId,
            UUID targetParentId,
            Integer position,
            int copies) {
        assertLayoutExists(layoutId);
        if (copies < 1) {
            throw WarehouseManagementException.badRequest("copies must be >= 1");
        }

        LayoutBlock sourceBlock = loadBlock(sourceBlockId);
        assertBelongsToLayout(sourceBlock, layoutId);

        if (targetParentId != null) {
            LayoutBlock targetParent = loadBlock(targetParentId);
            assertBelongsToLayout(targetParent, layoutId);
            assertNotDescendant(layoutId, sourceBlockId, targetParentId);
        }

        List<LayoutBlock> allBlocks = layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(layoutId);
        Map<UUID, List<LayoutBlock>> childrenByParentId = buildChildrenByParentId(allBlocks);
        List<LayoutBlock> subtreeBlocks = collectSubtree(childrenByParentId, sourceBlockId);
        Map<UUID, BlockTemplate> templatesById = loadTemplateMap(subtreeBlocks);

        int resolvedPosition = resolvePosition(layoutId, targetParentId, null, position);
        shiftPositions(layoutId, targetParentId, resolvedPosition, copies, null);

        List<BlockResult> createdRoots = new ArrayList<>();
        int totalCreated = 0;
        for (int copyIndex = 0; copyIndex < copies; copyIndex++) {
            Map<UUID, UUID> clonedIds = new HashMap<>();
            int rootPosition = resolvedPosition + copyIndex;
            LayoutBlock clonedRoot = cloneBlock(sourceBlock, layoutId, targetParentId, rootPosition, clonedIds);
            LayoutBlock savedRoot = saveBlock(clonedRoot);
            clonedIds.put(sourceBlockId, savedRoot.getId());
            createdRoots.add(toResult(savedRoot, requireTemplate(templatesById, savedRoot.getBlockTemplateId())));
            totalCreated++;
            totalCreated += cloneChildrenRecursively(
                    childrenByParentId,
                    templatesById,
                    layoutId,
                    sourceBlockId,
                    savedRoot.getId(),
                    clonedIds);
        }

        BatchBlockResult result = new BatchBlockResult(createdRoots, totalCreated, createdRoots.size());
        tenantAuditService.record("LAYOUT_BLOCK_SUBTREE_COPY", "LAYOUT_BLOCK", sourceBlockId.toString(), null, result);
        return result;
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
     * Shifts the position of all siblings at or after {@code fromPosition} by
     * {@code delta}.
     * Optionally excludes a specific block (useful when the block being moved is
     * still in the list).
     * <p>
     * Uses a single bulk UPDATE so the unique-position constraint is evaluated
     * after all rows are updated, not row-by-row.
     */
    private void shiftPositions(UUID layoutId, UUID parentId, int fromPosition, int delta, UUID excludeBlockId) {
        // Use a random UUID as sentinel when there is nothing to exclude, so the
        // query parameter is never null.
        UUID exclude = excludeBlockId != null ? excludeBlockId : UUID.randomUUID();
        if (parentId == null) {
            layoutBlockRepository.shiftRootPositions(layoutId, fromPosition, delta, exclude);
        } else {
            layoutBlockRepository.shiftChildPositions(layoutId, parentId, fromPosition, delta, exclude);
        }
    }

    /**
     * Recursively checks that {@code candidateAncestorId} is not a descendant of
     * {@code blockId}.
     */
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
            if (child.equals(targetId))
                return true;
            if (isDescendant(childMap, child, targetId))
                return true;
        }
        return false;
    }

    private Map<UUID, List<LayoutBlock>> buildChildrenByParentId(List<LayoutBlock> allBlocks) {
        Map<UUID, List<LayoutBlock>> childrenByParentId = new HashMap<>();
        for (LayoutBlock block : allBlocks) {
            if (block.getParentId() != null) {
                childrenByParentId.computeIfAbsent(block.getParentId(), ignored -> new ArrayList<>()).add(block);
            }
        }
        childrenByParentId.values()
                .forEach(children -> children.sort(Comparator.comparingInt(LayoutBlock::getPosition)));
        return childrenByParentId;
    }

    private List<LayoutBlock> collectSubtree(Map<UUID, List<LayoutBlock>> childrenByParentId, UUID rootId) {
        List<LayoutBlock> subtree = new ArrayList<>();
        collectSubtree(childrenByParentId, rootId, subtree);
        return subtree;
    }

    private void collectSubtree(Map<UUID, List<LayoutBlock>> childrenByParentId, UUID currentId,
            List<LayoutBlock> subtree) {
        LayoutBlock current = loadBlock(currentId);
        subtree.add(current);
        for (LayoutBlock child : childrenByParentId.getOrDefault(currentId, List.of())) {
            collectSubtree(childrenByParentId, child.getId(), subtree);
        }
    }

    private LayoutBlock cloneBlock(
            LayoutBlock source,
            UUID layoutId,
            UUID parentId,
            int position,
            Map<UUID, UUID> clonedIds) {
        return LayoutBlock.builder()
                .layoutId(layoutId)
                .blockTemplateId(source.getBlockTemplateId())
                .parentId(parentId)
                .position(position)
                .side(source.getSide())
                .build();
    }

    private int cloneChildrenRecursively(
            Map<UUID, List<LayoutBlock>> childrenByParentId,
            Map<UUID, BlockTemplate> templatesById,
            UUID layoutId,
            UUID sourceParentId,
            UUID clonedParentId,
            Map<UUID, UUID> clonedIds) {
        int createdCount = 0;
        for (LayoutBlock child : childrenByParentId.getOrDefault(sourceParentId, List.of())) {
            LayoutBlock clonedChild = cloneBlock(child, layoutId, clonedParentId, child.getPosition(), clonedIds);
            LayoutBlock savedChild = saveBlock(clonedChild);
            clonedIds.put(child.getId(), savedChild.getId());
            requireTemplate(templatesById, savedChild.getBlockTemplateId());
            createdCount++;
            createdCount += cloneChildrenRecursively(
                    childrenByParentId,
                    templatesById,
                    layoutId,
                    child.getId(),
                    savedChild.getId(),
                    clonedIds);
        }
        return createdCount;
    }

    private List<BlockNode> buildTree(List<LayoutBlock> all, UUID parentId, Map<UUID, BlockTemplate> templatesById) {
        List<BlockNode> nodes = new ArrayList<>();
        for (LayoutBlock b : all) {
            if (Objects.equals(b.getParentId(), parentId)) {
                List<BlockNode> children = buildTree(all, b.getId(), templatesById);
                nodes.add(new BlockNode(toResult(b, requireTemplate(templatesById, b.getBlockTemplateId())), children));
            }
        }
        nodes.sort(Comparator.comparingInt(n -> n.block().position()));
        return nodes;
    }

    private void assertLayoutExists(UUID layoutId) {
        UUID checkedLayoutId = Objects.requireNonNull(layoutId, "layoutId must not be null");
        if (!layoutRepository.existsById(checkedLayoutId)) {
            throw WarehouseManagementException.notFound("Layout not found: " + layoutId);
        }
    }

    private void assertBelongsToLayout(LayoutBlock block, UUID layoutId) {
        if (!block.getLayoutId().equals(layoutId)) {
            throw WarehouseManagementException.notFound("Block not found in this layout");
        }
    }

    private BlockTemplate loadTemplate(UUID templateId) {
        UUID checkedTemplateId = Objects.requireNonNull(templateId, "templateId must not be null");
        return blockTemplateRepository.findById(checkedTemplateId)
                .orElseThrow(() -> WarehouseManagementException.notFound("Block template not found: " + templateId));
    }

    private Map<UUID, BlockTemplate> loadTemplateMap(List<LayoutBlock> blocks) {
        List<UUID> templateIds = blocks.stream()
                .map(LayoutBlock::getBlockTemplateId)
                .distinct()
                .toList();
        Map<UUID, BlockTemplate> templatesById = new HashMap<>();
        blockTemplateRepository.findAllById(Objects.requireNonNull(templateIds, "templateIds must not be null"))
                .forEach(template -> templatesById.put(template.getId(), template));
        return templatesById;
    }

    private BlockTemplate requireTemplate(Map<UUID, BlockTemplate> templatesById, UUID templateId) {
        BlockTemplate template = templatesById.get(templateId);
        if (template == null) {
            throw WarehouseManagementException.notFound("Block template not found: " + templateId);
        }
        return template;
    }

    private LayoutBlock loadBlock(UUID blockId) {
        UUID checkedBlockId = Objects.requireNonNull(blockId, "blockId must not be null");
        return layoutBlockRepository.findById(checkedBlockId)
                .orElseThrow(() -> WarehouseManagementException.notFound("Layout block not found: " + blockId));
    }

    @SuppressWarnings({ "null", "ConstantConditions" })
    private LayoutBlock saveBlock(LayoutBlock block) {
        return layoutBlockRepository.save(block);
    }

    private String normalizeSide(String side, BlockTemplate template) {
        String normalized = normalizeOptional(side, 50, "side");
        BlockTemplate.SideConfig sideConfig = template.getSideConfig() != null
                ? template.getSideConfig()
                : BlockTemplate.SideConfig.NONE;
        if (normalized == null) {
            return null;
        }

        return switch (sideConfig) {
            case NONE -> throw WarehouseManagementException.badRequest(
                    "side is not allowed when the template side configuration is NONE");
            case LR -> normalizeAgainstAllowed(normalized, List.of("L", "R"), "side must be one of: L, R");
            case AB -> normalizeAgainstAllowed(normalized, List.of("A", "B"), "side must be one of: A, B");
            case CUSTOM -> normalizeAgainstAllowed(normalized, parseSideOptions(template),
                    "side must match one of the template's custom side options");
        };
    }

    private String normalizeAgainstAllowed(String value, List<String> allowed, String errorMessage) {
        for (String candidate : allowed) {
            if (candidate.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw WarehouseManagementException.badRequest(errorMessage);
    }

    private List<String> parseSideOptions(BlockTemplate template) {
        String raw = template.getSideOptions();
        if (raw == null || raw.isBlank()) {
            throw WarehouseManagementException.badRequest("side options are not configured for this template");
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(option -> !option.isEmpty())
                .toList();
    }

    private String retainCompatibleSide(String side, BlockTemplate template) {
        if (side == null || side.isBlank()) {
            return null;
        }
        try {
            return normalizeSide(side, template);
        } catch (WarehouseManagementException exception) {
            if ("BAD_REQUEST".equals(exception.getCode())) {
                return null;
            }
            throw exception;
        }
    }

    private String normalizeOptional(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw WarehouseManagementException.badRequest(field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private String resolveIdentifier(LayoutBlock block, BlockTemplate template) {
        BlockTemplate.IdentifierFormat format = template.getIdentifierFormat();
        if (format == null) {
            return null;
        }
        return switch (format) {
            case NUMERIC -> String.valueOf(block.getPosition() + 1);
            case ALPHA -> toAlphabeticIdentifier(block.getPosition());
            case CUSTOM, FREE_TEXT -> null;
        };
    }

    private String toAlphabeticIdentifier(int position) {
        int value = position;
        StringBuilder builder = new StringBuilder();
        do {
            builder.append((char) ('A' + (value % 26)));
            value = (value / 26) - 1;
        } while (value >= 0);
        return builder.reverse().toString();
    }

    private BlockResult toResult(LayoutBlock b, BlockTemplate template) {
        return new BlockResult(
                b.getId(),
                b.getLayoutId(),
                b.getBlockTemplateId(),
                b.getParentId(),
                b.getPosition(),
                resolveIdentifier(b, template),
                b.getSide(),
                b.getCreatedAt(),
                b.getUpdatedAt());
    }

    public record BlockResult(
            UUID id,
            UUID layoutId,
            UUID blockTemplateId,
            UUID parentId,
            int position,
            String identifier,
            String side,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record BlockNode(BlockResult block, List<BlockNode> children) {
    }

    public record BatchBlockResult(
            List<BlockResult> createdBlocks,
            int totalCreated,
            int rootCount) {
    }
}
