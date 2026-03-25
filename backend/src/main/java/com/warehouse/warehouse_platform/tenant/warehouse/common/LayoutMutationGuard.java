package com.warehouse.warehouse_platform.tenant.warehouse.common;

import com.warehouse.warehouse_platform.tenant.counting.CountSessionRepository;
import com.warehouse.warehouse_platform.tenant.dispatch.DispatchLineRepository;
import com.warehouse.warehouse_platform.tenant.inventory.StockMovementRepository;
import com.warehouse.warehouse_platform.tenant.receipt.ReceiptLineRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlock;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Centralised pre-mutation safety checks for the layout block tree.
 * Every mutation method in LayoutBlockService and WarehouseLayoutService
 * must consult this guard before executing so that downstream subsystems
 * (inventory, documents, counting, GIS) are never left in an inconsistent state.
 */
@Component
public class LayoutMutationGuard {

    private final StockMovementRepository stockMovementRepository;
    private final ReceiptLineRepository receiptLineRepository;
    private final DispatchLineRepository dispatchLineRepository;
    private final CountSessionRepository countSessionRepository;
    private final LayoutBlockRepository layoutBlockRepository;

    public LayoutMutationGuard(
            StockMovementRepository stockMovementRepository,
            ReceiptLineRepository receiptLineRepository,
            DispatchLineRepository dispatchLineRepository,
            CountSessionRepository countSessionRepository,
            LayoutBlockRepository layoutBlockRepository) {
        this.stockMovementRepository = stockMovementRepository;
        this.receiptLineRepository = receiptLineRepository;
        this.dispatchLineRepository = dispatchLineRepository;
        this.countSessionRepository = countSessionRepository;
        this.layoutBlockRepository = layoutBlockRepository;
    }

    /**
     * Rejects deletion of a block subtree if any downstream subsystem holds
     * references to those location IDs.
     */
    public void checkRemoveBlock(Set<UUID> subtreeIds) {
        if (subtreeIds.isEmpty()) return;
        List<UUID> ids = List.copyOf(subtreeIds);

        List<String> blockers = new ArrayList<>();
        if (stockMovementRepository.existsByLocationIdIn(ids))
            blockers.add("stock movements");
        if (receiptLineRepository.existsByDestinationLocationIdIn(ids))
            blockers.add("receipt lines");
        if (dispatchLineRepository.existsBySourceLocationIdIn(ids))
            blockers.add("dispatch lines");
        if (countSessionRepository.existsOpenSessionWithAnyLocation(ids))
            blockers.add("open count sessions");

        if (!blockers.isEmpty()) {
            throw WarehouseManagementException.conflict(
                    "Cannot delete block: referenced by " + String.join(", ", blockers));
        }
    }

    /**
     * Rejects adding a child block under a parent that currently holds live stock.
     * Adding children would demote the parent from a selectable leaf to a structural
     * node, trapping any existing inventory there.
     */
    public void checkAddChildToBlock(UUID parentBlockId) {
        if (parentBlockId == null) return;
        if (!stockMovementRepository.findStockByLocation(parentBlockId).isEmpty()) {
            throw WarehouseManagementException.conflict(
                    "Cannot add children to a block that holds live stock — transfer stock out first");
        }
    }

    /**
     * Rejects switching the active layout if the current active layout still has
     * live inventory or open count sessions. Switching without clearing these would
     * orphan stock and make it unreachable by any inventory operation.
     */
    public void checkActivateLayout(UUID currentLayoutId) {
        if (currentLayoutId == null) return;

        List<UUID> blockIds = layoutBlockRepository
                .findByLayoutIdOrderByParentIdAscPositionAsc(currentLayoutId)
                .stream().map(LayoutBlock::getId).toList();

        if (blockIds.isEmpty()) return;

        if (!stockMovementRepository.findStockByLocationIds(blockIds).isEmpty()) {
            throw WarehouseManagementException.conflict(
                    "Cannot switch layouts: the current active layout has live inventory");
        }
        if (countSessionRepository.existsOpenSessionWithAnyLocation(blockIds)) {
            throw WarehouseManagementException.conflict(
                    "Cannot switch layouts: open count sessions reference locations in the current layout");
        }
    }

    /**
     * Rejects moving a block (and its subtree) if any of those locations are
     * referenced by an open count session. Moving mid-session would invalidate
     * the expected-quantity snapshot taken when the session was opened.
     */
    public void checkMoveBlock(Collection<UUID> subtreeIds) {
        if (subtreeIds.isEmpty()) return;
        if (countSessionRepository.existsOpenSessionWithAnyLocation(List.copyOf(subtreeIds))) {
            throw WarehouseManagementException.conflict(
                    "Cannot move a block that is referenced by an open count session");
        }
    }
}
