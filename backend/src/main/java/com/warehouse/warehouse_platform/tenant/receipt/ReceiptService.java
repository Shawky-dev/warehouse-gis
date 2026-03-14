package com.warehouse.warehouse_platform.tenant.receipt;

import com.warehouse.warehouse_platform.tenant.inventory.InventoryLedgerService;
import com.warehouse.warehouse_platform.tenant.product.Product;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import com.warehouse.warehouse_platform.tenant.supplier.Supplier;
import com.warehouse.warehouse_platform.tenant.supplier.SupplierRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlock;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("null")
public class ReceiptService {

    private static final String DISPATCH_KIND_NAME = "dispatch";

    private final ReceiptDocumentRepository receiptDocumentRepository;
    private final ReceiptLineRepository receiptLineRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final LayoutBlockRepository layoutBlockRepository;
    private final InventoryLedgerService inventoryLedgerService;

    public ReceiptService(
            ReceiptDocumentRepository receiptDocumentRepository,
            ReceiptLineRepository receiptLineRepository,
            SupplierRepository supplierRepository,
            ProductRepository productRepository,
            LayoutBlockRepository layoutBlockRepository,
            InventoryLedgerService inventoryLedgerService) {
        this.receiptDocumentRepository = receiptDocumentRepository;
        this.receiptLineRepository = receiptLineRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.layoutBlockRepository = layoutBlockRepository;
        this.inventoryLedgerService = inventoryLedgerService;
    }

    @Transactional
    public ReceiptDetailResult createDraft(UUID supplierId, String reference, String notes, String actor) {
        Supplier supplier = loadSupplierIfProvided(supplierId);

        ReceiptDocument draft = ReceiptDocument.builder()
                .supplier(supplier)
                .reference(normalizeOptional(reference, 120, "reference"))
                .notes(normalizeOptional(notes, 500, "notes"))
                .status(ReceiptStatus.DRAFT)
                .createdBy(requireActor(actor))
                .build();

        ReceiptDocument saved = receiptDocumentRepository.save(draft);
        return toDetailResult(saved, List.of(), Map.of(), Map.of());
    }

    @Transactional
    public ReceiptLineResult addLine(
            UUID receiptId,
            UUID productId,
            UUID destinationLocationId,
            BigDecimal qty,
            String lotNumber,
            LocalDate expiryDate,
            String notes,
            String actor) {
        assertActor(actor);
        ReceiptDocument receipt = loadReceipt(receiptId);
        assertDraft(receipt, "Only draft receipts can be modified");

        Product product = loadActiveProduct(productId);
        LayoutBlock destination = validateDestinationLocation(destinationLocationId);
        validateTrackedFields(product, lotNumber, expiryDate);

        BigDecimal normalizedQty = validateQty(qty);
        int nextPosition = receiptLineRepository.findMaxPositionByReceiptId(receiptId) + 1;

        ReceiptLine line = ReceiptLine.builder()
                .receiptId(receiptId)
                .productId(productId)
                .destinationLocationId(destinationLocationId)
                .qty(normalizedQty)
                .lotNumber(normalizeOptional(lotNumber, 100, "lotNumber"))
                .expiryDate(expiryDate)
                .notes(normalizeOptional(notes, 500, "notes"))
                .position(nextPosition)
                .build();

        ReceiptLine saved = receiptLineRepository.save(line);
        return toLineResult(saved, Map.of(product.getId(), product), Map.of(destination.getId(), destination));
    }

    @Transactional
    public void removeLine(UUID receiptId, UUID lineId, String actor) {
        assertActor(actor);
        ReceiptDocument receipt = loadReceipt(receiptId);
        assertDraft(receipt, "Only draft receipts can be modified");

        ReceiptLine line = receiptLineRepository.findByReceiptIdAndId(receiptId, lineId)
                .orElseThrow(() -> ReceiptManagementException.notFound("Receipt line not found: " + lineId));

        receiptLineRepository.delete(line);
    }

    @Transactional
    public ReceiptLineResult updateLine(
            UUID receiptId,
            UUID lineId,
            BigDecimal qty,
            String lotNumber,
            LocalDate expiryDate,
            String notes,
            String actor) {
        assertActor(actor);
        ReceiptDocument receipt = loadReceipt(receiptId);
        assertDraft(receipt, "Only draft receipts can be modified");

        ReceiptLine line = receiptLineRepository.findByReceiptIdAndId(receiptId, lineId)
                .orElseThrow(() -> ReceiptManagementException.notFound("Receipt line not found: " + lineId));
        Product product = loadActiveProduct(line.getProductId());

        BigDecimal normalizedQty = validateQty(qty);
        validateTrackedFields(product, lotNumber, expiryDate);

        line.setQty(normalizedQty);
        line.setLotNumber(normalizeOptional(lotNumber, 100, "lotNumber"));
        line.setExpiryDate(expiryDate);
        line.setNotes(normalizeOptional(notes, 500, "notes"));

        ReceiptLine saved = receiptLineRepository.save(line);

        LayoutBlock destination = layoutBlockRepository.findById(saved.getDestinationLocationId())
                .orElseThrow(() -> ReceiptManagementException.badRequest(
                        "Destination location not found: " + saved.getDestinationLocationId()));

        return toLineResult(saved, Map.of(product.getId(), product), Map.of(destination.getId(), destination));
    }

    @Transactional
    public ReceiptDetailResult postReceipt(UUID receiptId, String actor) {
        String normalizedActor = requireActor(actor);
        ReceiptDocument receipt = loadReceipt(receiptId);
        if (receipt.getStatus() != ReceiptStatus.DRAFT) {
            throw ReceiptManagementException.conflict("Only draft receipts can be posted");
        }

        List<ReceiptLine> lines = receiptLineRepository.findByReceiptIdOrderByPosition(receiptId);
        if (lines.isEmpty()) {
            throw ReceiptManagementException.badRequest("Receipt must contain at least one line before posting");
        }

        for (ReceiptLine line : lines) {
            inventoryLedgerService.receive(
                    line.getDestinationLocationId(),
                    line.getProductId(),
                    line.getQty(),
                    line.getLotNumber(),
                    line.getExpiryDate(),
                    line.getNotes(),
                    receiptId,
                    null,
                    normalizedActor);
        }

        receipt.setStatus(ReceiptStatus.POSTED);
        receipt.setPostedAt(Instant.now());
        receipt.setPostedBy(normalizedActor);
        receipt.setVoidedAt(null);
        receipt.setVoidedBy(null);

        ReceiptDocument saved = receiptDocumentRepository.save(receipt);
        return getReceipt(saved.getId());
    }

    @Transactional
    public ReceiptDetailResult voidReceipt(UUID receiptId, String actor) {
        String normalizedActor = requireActor(actor);
        ReceiptDocument receipt = loadReceipt(receiptId);
        if (receipt.getStatus() != ReceiptStatus.POSTED) {
            throw ReceiptManagementException.conflict("Only posted receipts can be voided");
        }

        List<ReceiptLine> lines = receiptLineRepository.findByReceiptIdOrderByPosition(receiptId);
        if (lines.isEmpty()) {
            throw ReceiptManagementException.badRequest("Posted receipt has no lines to void");
        }

        for (ReceiptLine line : lines) {
            inventoryLedgerService.adjust(
                    line.getDestinationLocationId(),
                    line.getProductId(),
                    line.getQty().negate(),
                    line.getLotNumber(),
                    "Void receipt " + receiptId,
                    receiptId,
                    "VOID_RECEIPT",
                    normalizedActor);
        }

        receipt.setStatus(ReceiptStatus.VOID);
        receipt.setVoidedAt(Instant.now());
        receipt.setVoidedBy(normalizedActor);

        ReceiptDocument saved = receiptDocumentRepository.save(receipt);
        return getReceipt(saved.getId());
    }

    @Transactional(readOnly = true)
    public ReceiptPageResult listReceipts(int page, int size, ReceiptStatus status, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ReceiptDocument> result = receiptDocumentRepository.findAll(
                buildReceiptSpecification(status, search),
                pageable);

        List<ReceiptListItem> content = result.getContent().stream()
                .map(this::toListItem)
                .toList();

        return new ReceiptPageResult(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ReceiptDetailResult getReceipt(UUID receiptId) {
        ReceiptDocument receipt = loadReceipt(receiptId);
        List<ReceiptLine> lines = receiptLineRepository.findByReceiptIdOrderByPosition(receiptId);

        Map<UUID, Product> productsById = productRepository.findAllById(lines.stream()
                .map(ReceiptLine::getProductId)
                .distinct()
                .toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<UUID, LayoutBlock> locationsById = layoutBlockRepository.findAllById(lines.stream()
                .map(ReceiptLine::getDestinationLocationId)
                .distinct()
                .toList())
                .stream()
                .collect(Collectors.toMap(LayoutBlock::getId, Function.identity()));

        return toDetailResult(receipt, lines, productsById, locationsById);
    }

    private ReceiptDocument loadReceipt(UUID receiptId) {
        return receiptDocumentRepository.findById(receiptId)
                .orElseThrow(() -> ReceiptManagementException.notFound("Receipt not found: " + receiptId));
    }

    private Supplier loadSupplierIfProvided(UUID supplierId) {
        if (supplierId == null) {
            return null;
        }
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> ReceiptManagementException.badRequest("Supplier not found: " + supplierId));

        if (!Boolean.TRUE.equals(supplier.getActive())) {
            throw ReceiptManagementException.badRequest("Supplier is inactive: " + supplierId);
        }
        return supplier;
    }

    private Product loadActiveProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ReceiptManagementException.badRequest("Product not found: " + productId));
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw ReceiptManagementException.badRequest("Product is inactive: " + productId);
        }
        return product;
    }

    private LayoutBlock validateDestinationLocation(UUID destinationLocationId) {
        LayoutBlock location = layoutBlockRepository.findById(destinationLocationId)
                .orElseThrow(() -> ReceiptManagementException.badRequest(
                        "Destination location not found: " + destinationLocationId));

        if (layoutBlockRepository.existsByParentId(destinationLocationId)) {
            throw ReceiptManagementException.badRequest("Destination location must be a leaf block");
        }

        String locationKindName = Optional.ofNullable(location.getLocationKind())
                .map(kind -> kind.getName())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .orElse("");
        if (DISPATCH_KIND_NAME.equals(locationKindName)) {
            throw ReceiptManagementException.badRequest("Destination location kind DISPATCH is not allowed");
        }

        return location;
    }

    private void validateTrackedFields(Product product, String lotNumber, LocalDate expiryDate) {
        String normalizedLot = normalizeOptional(lotNumber, 100, "lotNumber");
        if (Boolean.TRUE.equals(product.getTrackLot()) && (normalizedLot == null || normalizedLot.isBlank())) {
            throw ReceiptManagementException.badRequest("Lot number is required for lot-tracked products");
        }
        if (Boolean.TRUE.equals(product.getTrackExpiry()) && expiryDate == null) {
            throw ReceiptManagementException.badRequest("Expiry date is required for expiry-tracked products");
        }
    }

    private BigDecimal validateQty(BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw ReceiptManagementException.badRequest("Quantity must be greater than zero");
        }
        return qty;
    }

    private void assertDraft(ReceiptDocument receipt, String message) {
        if (receipt.getStatus() != ReceiptStatus.DRAFT) {
            throw ReceiptManagementException.conflict(message);
        }
    }

    private void assertActor(String actor) {
        requireActor(actor);
    }

    private String requireActor(String actor) {
        String normalized = normalizeOptional(actor, 255, "actor");
        if (normalized == null) {
            throw ReceiptManagementException.badRequest("actor is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw ReceiptManagementException.badRequest(fieldName + " exceeds max length " + maxLength);
        }
        return normalized;
    }

    private Specification<ReceiptDocument> buildReceiptSpecification(ReceiptStatus status, String search) {
        String normalizedSearch = normalizeSearch(search);
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (normalizedSearch != null) {
                String like = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
                Predicate referenceMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("reference")), like);
                Predicate supplierMatch = criteriaBuilder.like(
                        criteriaBuilder
                                .lower(root.join("supplier", jakarta.persistence.criteria.JoinType.LEFT).get("name")),
                        like);
                predicates.add(criteriaBuilder.or(referenceMatch, supplierMatch));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String normalized = search.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ReceiptListItem toListItem(ReceiptDocument receipt) {
        Supplier supplier = receipt.getSupplier();
        return new ReceiptListItem(
                receipt.getId(),
                supplier != null ? supplier.getId() : null,
                supplier != null ? supplier.getName() : null,
                receipt.getReference(),
                receipt.getNotes(),
                receipt.getStatus(),
                receipt.getCreatedBy(),
                receipt.getCreatedAt(),
                receipt.getPostedAt(),
                receipt.getPostedBy(),
                receipt.getVoidedAt(),
                receipt.getVoidedBy());
    }

    private ReceiptDetailResult toDetailResult(
            ReceiptDocument receipt,
            List<ReceiptLine> lines,
            Map<UUID, Product> productsById,
            Map<UUID, LayoutBlock> locationsById) {
        Supplier supplier = receipt.getSupplier();

        List<ReceiptLineResult> lineResults = lines.stream()
                .map(line -> toLineResult(line, productsById, locationsById))
                .toList();

        return new ReceiptDetailResult(
                receipt.getId(),
                supplier != null ? supplier.getId() : null,
                supplier != null ? supplier.getName() : null,
                receipt.getReference(),
                receipt.getNotes(),
                receipt.getStatus(),
                receipt.getCreatedBy(),
                receipt.getCreatedAt(),
                receipt.getPostedAt(),
                receipt.getPostedBy(),
                receipt.getVoidedAt(),
                receipt.getVoidedBy(),
                lineResults);
    }

    private ReceiptLineResult toLineResult(
            ReceiptLine line,
            Map<UUID, Product> productsById,
            Map<UUID, LayoutBlock> locationsById) {
        Product product = productsById.get(line.getProductId());
        LayoutBlock location = locationsById.get(line.getDestinationLocationId());

        return new ReceiptLineResult(
                line.getId(),
                line.getReceiptId(),
                line.getProductId(),
                product != null ? product.getSku() : null,
                product != null ? product.getName() : null,
                line.getDestinationLocationId(),
                location != null ? location.getFullCode() : null,
                line.getQty(),
                line.getLotNumber(),
                line.getExpiryDate(),
                line.getNotes(),
                line.getPosition());
    }

    public record ReceiptPageResult(
            List<ReceiptListItem> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record ReceiptListItem(
            UUID id,
            UUID supplierId,
            String supplierName,
            String reference,
            String notes,
            ReceiptStatus status,
            String createdBy,
            Instant createdAt,
            Instant postedAt,
            String postedBy,
            Instant voidedAt,
            String voidedBy) {
    }

    public record ReceiptDetailResult(
            UUID id,
            UUID supplierId,
            String supplierName,
            String reference,
            String notes,
            ReceiptStatus status,
            String createdBy,
            Instant createdAt,
            Instant postedAt,
            String postedBy,
            Instant voidedAt,
            String voidedBy,
            List<ReceiptLineResult> lines) {
    }

    public record ReceiptLineResult(
            UUID id,
            UUID receiptId,
            UUID productId,
            String productSku,
            String productName,
            UUID destinationLocationId,
            String locationPathLabel,
            BigDecimal qty,
            String lotNumber,
            LocalDate expiryDate,
            String notes,
            int position) {
    }
}
