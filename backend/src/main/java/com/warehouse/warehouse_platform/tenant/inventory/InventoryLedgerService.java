package com.warehouse.warehouse_platform.tenant.inventory;

import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryLedgerService {

    private final StockMovementRepository movementRepository;
    private final LayoutBlockRepository layoutBlockRepository;
    private final ProductRepository productRepository;

    public InventoryLedgerService(
            StockMovementRepository movementRepository,
            LayoutBlockRepository layoutBlockRepository,
            ProductRepository productRepository) {
        this.movementRepository = movementRepository;
        this.layoutBlockRepository = layoutBlockRepository;
        this.productRepository = productRepository;
    }

    // -------------------------------------------------------------------------
    // Receive
    // -------------------------------------------------------------------------

    @Transactional
    public MovementResult receive(
            UUID locationId,
            UUID productId,
            BigDecimal qty,
            String lotNumber,
            LocalDate expiryDate,
            String notes,
            String actor) {
        validateQtyPositive(qty, "receive qty must be positive");
        assertLocationExists(locationId);
        assertProductExists(productId);

        StockMovement movement = StockMovement.builder()
                .locationId(locationId)
                .productId(productId)
                .qty(qty)
                .type(MovementType.RECEIVE)
                .lotNumber(lotNumber)
                .expiryDate(expiryDate)
                .notes(notes)
                .createdBy(actor)
                .build();

        return toResult(movementRepository.save(movement));
    }

    // -------------------------------------------------------------------------
    // Transfer
    // -------------------------------------------------------------------------

    @Transactional
    public TransferResult transfer(
            UUID fromLocationId,
            UUID toLocationId,
            UUID productId,
            BigDecimal qty,
            String lotNumber,
            String notes,
            String actor) {
        validateQtyPositive(qty, "transfer qty must be positive");

        if (fromLocationId.equals(toLocationId)) {
            throw InventoryLedgerException.badRequest("Source and destination locations must differ");
        }

        assertLocationExists(fromLocationId);
        assertLocationExists(toLocationId);
        assertProductExists(productId);
        assertSufficientStock(fromLocationId, productId, qty);

        UUID referenceId = UUID.randomUUID();

        StockMovement out = StockMovement.builder()
                .locationId(fromLocationId)
                .productId(productId)
                .qty(qty.negate())
                .type(MovementType.TRANSFER_OUT)
                .referenceId(referenceId)
                .lotNumber(lotNumber)
                .notes(notes)
                .createdBy(actor)
                .build();

        StockMovement in = StockMovement.builder()
                .locationId(toLocationId)
                .productId(productId)
                .qty(qty)
                .type(MovementType.TRANSFER_IN)
                .referenceId(referenceId)
                .lotNumber(lotNumber)
                .notes(notes)
                .createdBy(actor)
                .build();

        StockMovement savedOut = movementRepository.save(out);
        StockMovement savedIn = movementRepository.save(in);

        return new TransferResult(referenceId, toResult(savedOut), toResult(savedIn));
    }

    // -------------------------------------------------------------------------
    // Adjust
    // -------------------------------------------------------------------------

    @Transactional
    public MovementResult adjust(
            UUID locationId,
            UUID productId,
            BigDecimal qty,
            String notes,
            String actor) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) == 0) {
            throw InventoryLedgerException.badRequest("Adjustment qty must not be zero");
        }

        assertLocationExists(locationId);
        assertProductExists(productId);

        // Negative adjustments must not drive stock below zero
        if (qty.compareTo(BigDecimal.ZERO) < 0) {
            assertSufficientStock(locationId, productId, qty.abs());
        }

        if (notes == null || notes.isBlank()) {
            throw InventoryLedgerException.badRequest("A reason note is required for adjustments");
        }

        StockMovement movement = StockMovement.builder()
                .locationId(locationId)
                .productId(productId)
                .qty(qty)
                .type(MovementType.ADJUST)
                .notes(notes.trim())
                .createdBy(actor)
                .build();

        return toResult(movementRepository.save(movement));
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OnHandEntry> getAllOnHand() {
        return movementRepository.findAllOnHand();
    }

    @Transactional(readOnly = true)
    public List<OnHandEntry> getOnHandByLocation(UUID locationId) {
        assertLocationExists(locationId);
        return movementRepository.findOnHandByLocation(locationId);
    }

    @Transactional(readOnly = true)
    public List<OnHandEntry> getOnHandByProduct(UUID productId) {
        assertProductExists(productId);
        return movementRepository.findOnHandByProduct(productId);
    }

    @Transactional(readOnly = true)
    public MovementPageResult getMovementsByLocation(UUID locationId, int page, int size) {
        assertLocationExists(locationId);
        Pageable pageable = PageRequest.of(page, size);
        Page<StockMovement> result = movementRepository
                .findByLocationIdOrderByCreatedAtDesc(locationId, pageable);
        return toPageResult(result);
    }

    @Transactional(readOnly = true)
    public MovementPageResult getMovementsByProduct(UUID productId, int page, int size) {
        assertProductExists(productId);
        Pageable pageable = PageRequest.of(page, size);
        Page<StockMovement> result = movementRepository
                .findByProductIdOrderByCreatedAtDesc(productId, pageable);
        return toPageResult(result);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void assertLocationExists(UUID locationId) {
        if (!layoutBlockRepository.existsById(locationId)) {
            throw InventoryLedgerException.notFound("Location not found: " + locationId);
        }
    }

    private void assertProductExists(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw InventoryLedgerException.notFound("Product not found: " + productId);
        }
    }

    private void assertSufficientStock(UUID locationId, UUID productId, BigDecimal required) {
        BigDecimal current = movementRepository.findOnHandQtyByLocationAndProduct(locationId, productId)
                .orElse(BigDecimal.ZERO);

        if (current.compareTo(required) < 0) {
            throw InventoryLedgerException.badRequest(
                    "Insufficient stock: available " + current + ", requested " + required);
        }
    }

    private void validateQtyPositive(BigDecimal qty, String message) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw InventoryLedgerException.badRequest(message);
        }
    }

    private MovementResult toResult(StockMovement m) {
        return new MovementResult(
                m.getId(),
                m.getLocationId(),
                m.getProductId(),
                m.getQty(),
                m.getType(),
                m.getReferenceId(),
                m.getLotNumber(),
                m.getExpiryDate(),
                m.getNotes(),
                m.getCreatedBy(),
                m.getCreatedAt());
    }

    private MovementPageResult toPageResult(Page<StockMovement> page) {
        return new MovementPageResult(
                page.getContent().stream().map(this::toResult).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    // -------------------------------------------------------------------------
    // Result types
    // -------------------------------------------------------------------------

    public record MovementResult(
            UUID id,
            UUID locationId,
            UUID productId,
            BigDecimal qty,
            MovementType type,
            UUID referenceId,
            String lotNumber,
            java.time.LocalDate expiryDate,
            String notes,
            String createdBy,
            java.time.Instant createdAt) {
    }

    public record TransferResult(
            UUID referenceId,
            MovementResult out,
            MovementResult in) {
    }

    public record MovementPageResult(
            List<MovementResult> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
