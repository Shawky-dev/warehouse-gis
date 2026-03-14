package com.warehouse.warehouse_platform.tenant.dispatch;

import com.warehouse.warehouse_platform.tenant.inventory.InventoryLedgerService;
import com.warehouse.warehouse_platform.tenant.inventory.StockMovementRepository;
import com.warehouse.warehouse_platform.tenant.product.Product;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplate;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplateRepository;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("null")
public class DispatchService {

    private static final String STRUCTURAL_KIND_NAME = "structural";
    private static final String VOID_REASON_CODE = "VOID_DISPATCH";

    private final DispatchDocumentRepository dispatchDocumentRepository;
    private final DispatchLineRepository dispatchLineRepository;
    private final ProductRepository productRepository;
    private final LayoutBlockRepository layoutBlockRepository;
    private final BlockTemplateRepository blockTemplateRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryLedgerService inventoryLedgerService;

    public DispatchService(
            DispatchDocumentRepository dispatchDocumentRepository,
            DispatchLineRepository dispatchLineRepository,
            ProductRepository productRepository,
            LayoutBlockRepository layoutBlockRepository,
            BlockTemplateRepository blockTemplateRepository,
            StockMovementRepository stockMovementRepository,
            InventoryLedgerService inventoryLedgerService) {
        this.dispatchDocumentRepository = dispatchDocumentRepository;
        this.dispatchLineRepository = dispatchLineRepository;
        this.productRepository = productRepository;
        this.layoutBlockRepository = layoutBlockRepository;
        this.blockTemplateRepository = blockTemplateRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.inventoryLedgerService = inventoryLedgerService;
    }

    @Transactional
    public DispatchDetailResult createDraft(String destination, String reference, String notes, String actor) {
        DispatchDocument draft = DispatchDocument.builder()
                .destination(normalizeOptional(destination, 200, "destination"))
                .reference(normalizeOptional(reference, 120, "reference"))
                .notes(normalizeOptional(notes, 500, "notes"))
                .status(DispatchStatus.DRAFT)
                .createdBy(requireActor(actor))
                .build();

        DispatchDocument saved = dispatchDocumentRepository.save(draft);
        return toDetailResult(saved, List.of(), Map.of(), Map.of());
    }

    @Transactional
    public DispatchLineResult addLine(
            UUID dispatchId,
            UUID productId,
            UUID sourceLocationId,
            BigDecimal qty,
            String lotNumber,
            String notes,
            String actor) {
        assertActor(actor);
        DispatchDocument dispatch = loadDispatch(dispatchId);
        assertDraft(dispatch, "Only draft dispatches can be modified");

        Product product = loadActiveProduct(productId);
        LayoutBlock sourceLocation = validateSourceLocation(sourceLocationId);
        validateLot(product, lotNumber);

        BigDecimal normalizedQty = validateQty(qty);
        int nextPosition = dispatchLineRepository.findMaxPositionByDispatchId(dispatchId) + 1;

        DispatchLine line = DispatchLine.builder()
                .dispatchId(dispatchId)
                .productId(productId)
                .sourceLocationId(sourceLocationId)
                .qty(normalizedQty)
                .lotNumber(normalizeOptional(lotNumber, 100, "lotNumber"))
                .notes(normalizeOptional(notes, 500, "notes"))
                .position(nextPosition)
                .build();

        DispatchLine saved = dispatchLineRepository.save(line);
        return toLineResult(saved, Map.of(product.getId(), product), Map.of(sourceLocation.getId(), sourceLocation));
    }

    @Transactional
    public DispatchLineResult updateLine(
            UUID dispatchId,
            UUID lineId,
            BigDecimal qty,
            String lotNumber,
            String notes,
            String actor) {
        assertActor(actor);
        DispatchDocument dispatch = loadDispatch(dispatchId);
        assertDraft(dispatch, "Only draft dispatches can be modified");

        DispatchLine line = dispatchLineRepository.findByDispatchIdAndId(dispatchId, lineId)
                .orElseThrow(() -> DispatchManagementException.notFound("Dispatch line not found: " + lineId));
        Product product = loadActiveProduct(line.getProductId());

        validateLot(product, lotNumber);

        line.setQty(validateQty(qty));
        line.setLotNumber(normalizeOptional(lotNumber, 100, "lotNumber"));
        line.setNotes(normalizeOptional(notes, 500, "notes"));

        DispatchLine saved = dispatchLineRepository.save(line);

        LayoutBlock sourceLocation = layoutBlockRepository.findById(saved.getSourceLocationId())
                .orElseThrow(() -> DispatchManagementException.badRequest(
                        "Source location not found: " + saved.getSourceLocationId()));

        return toLineResult(saved, Map.of(product.getId(), product), Map.of(sourceLocation.getId(), sourceLocation));
    }

    @Transactional
    public void removeLine(UUID dispatchId, UUID lineId, String actor) {
        assertActor(actor);
        DispatchDocument dispatch = loadDispatch(dispatchId);
        assertDraft(dispatch, "Only draft dispatches can be modified");

        DispatchLine line = dispatchLineRepository.findByDispatchIdAndId(dispatchId, lineId)
                .orElseThrow(() -> DispatchManagementException.notFound("Dispatch line not found: " + lineId));
        dispatchLineRepository.delete(line);
    }

    @Transactional
    public DispatchDetailResult postDispatch(UUID dispatchId, String actor) {
        String normalizedActor = requireActor(actor);
        DispatchDocument dispatch = loadDispatch(dispatchId);
        if (dispatch.getStatus() != DispatchStatus.DRAFT) {
            throw DispatchManagementException.conflict("Only draft dispatches can be posted");
        }

        List<DispatchLine> lines = dispatchLineRepository.findByDispatchIdOrderByPosition(dispatchId);
        if (lines.isEmpty()) {
            throw DispatchManagementException.badRequest("Dispatch must contain at least one line before posting");
        }

        Map<UUID, Product> productsById = productRepository.findAllById(lines.stream()
                .map(DispatchLine::getProductId)
                .distinct()
                .toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (DispatchLine line : lines) {
            Product product = productsById.get(line.getProductId());
            if (product == null) {
                throw DispatchManagementException.badRequest("Product not found: " + line.getProductId());
            }
            if (!Boolean.TRUE.equals(product.getActive())) {
                throw DispatchManagementException.badRequest("Product is inactive: " + product.getId());
            }

            BigDecimal available = Boolean.TRUE.equals(product.getTrackLot())
                    ? stockMovementRepository
                            .findStockQtyByLocationProductAndLot(
                                    line.getSourceLocationId(),
                                    line.getProductId(),
                                    line.getLotNumber())
                            .orElse(BigDecimal.ZERO)
                    : stockMovementRepository
                            .findStockQtyByLocationAndProduct(line.getSourceLocationId(), line.getProductId())
                            .orElse(BigDecimal.ZERO);

            if (available.compareTo(line.getQty()) < 0) {
                BigDecimal deficit = line.getQty().subtract(available);
                throw DispatchManagementException.badRequest(
                        "Insufficient stock at line " + (line.getPosition() + 1)
                                + ": required=" + line.getQty() + ", available=" + available
                                + ", deficit=" + deficit);
            }
        }

        for (DispatchLine line : lines) {
            inventoryLedgerService.pick(
                    line.getSourceLocationId(),
                    line.getProductId(),
                    line.getQty(),
                    line.getLotNumber(),
                    dispatchId,
                    normalizedActor);
        }

        dispatch.setStatus(DispatchStatus.POSTED);
        dispatch.setPostedAt(Instant.now());
        dispatch.setPostedBy(normalizedActor);
        dispatch.setVoidedAt(null);
        dispatch.setVoidedBy(null);

        DispatchDocument saved = dispatchDocumentRepository.save(dispatch);
        return getDispatch(saved.getId());
    }

    @Transactional
    public DispatchDetailResult voidDispatch(UUID dispatchId, String actor) {
        String normalizedActor = requireActor(actor);
        DispatchDocument dispatch = loadDispatch(dispatchId);
        if (dispatch.getStatus() != DispatchStatus.POSTED) {
            throw DispatchManagementException.conflict("Only posted dispatches can be voided");
        }

        List<DispatchLine> lines = dispatchLineRepository.findByDispatchIdOrderByPosition(dispatchId);
        if (lines.isEmpty()) {
            throw DispatchManagementException.badRequest("Posted dispatch has no lines to void");
        }

        for (DispatchLine line : lines) {
            inventoryLedgerService.receive(
                    line.getSourceLocationId(),
                    line.getProductId(),
                    line.getQty(),
                    line.getLotNumber(),
                    null,
                    buildDocumentLineNote("Void dispatch", dispatch.getReference(), dispatch.getDestination(),
                            dispatchId,
                            line.getNotes()),
                    dispatchId,
                    VOID_REASON_CODE,
                    normalizedActor);
        }

        dispatch.setStatus(DispatchStatus.VOID);
        dispatch.setVoidedAt(Instant.now());
        dispatch.setVoidedBy(normalizedActor);

        DispatchDocument saved = dispatchDocumentRepository.save(dispatch);
        return getDispatch(saved.getId());
    }

    @Transactional(readOnly = true)
    public DispatchPageResult listDispatches(int page, int size, DispatchStatus status, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DispatchDocument> result = dispatchDocumentRepository.findAll(
                buildDispatchSpecification(status, search),
                pageable);

        List<DispatchListItem> content = result.getContent().stream()
                .map(this::toListItem)
                .toList();

        return new DispatchPageResult(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DispatchDetailResult getDispatch(UUID dispatchId) {
        DispatchDocument dispatch = loadDispatch(dispatchId);
        List<DispatchLine> lines = dispatchLineRepository.findByDispatchIdOrderByPosition(dispatchId);

        Map<UUID, Product> productsById = productRepository.findAllById(lines.stream()
                .map(DispatchLine::getProductId)
                .distinct()
                .toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<UUID, LayoutBlock> locationsById = layoutBlockRepository.findAllById(lines.stream()
                .map(DispatchLine::getSourceLocationId)
                .distinct()
                .toList())
                .stream()
                .collect(Collectors.toMap(LayoutBlock::getId, Function.identity()));

        return toDetailResult(dispatch, lines, productsById, locationsById);
    }

    private DispatchDocument loadDispatch(UUID dispatchId) {
        return dispatchDocumentRepository.findById(dispatchId)
                .orElseThrow(() -> DispatchManagementException.notFound("Dispatch not found: " + dispatchId));
    }

    private Product loadActiveProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> DispatchManagementException.badRequest("Product not found: " + productId));
        if (!Boolean.TRUE.equals(product.getActive())) {
            throw DispatchManagementException.badRequest("Product is inactive: " + productId);
        }
        return product;
    }

    private LayoutBlock validateSourceLocation(UUID sourceLocationId) {
        LayoutBlock location = layoutBlockRepository.findById(sourceLocationId)
                .orElseThrow(() -> DispatchManagementException.badRequest(
                        "Source location not found: " + sourceLocationId));

        if (layoutBlockRepository.existsByParentId(sourceLocationId)) {
            throw DispatchManagementException.badRequest("Source location must be a leaf block");
        }

        String locationKindName = Optional.ofNullable(location.getLocationKind())
                .map(kind -> kind.getName())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .orElse("");

        if (STRUCTURAL_KIND_NAME.equals(locationKindName)) {
            throw DispatchManagementException.badRequest("Source location kind STRUCTURAL is not allowed");
        }

        return location;
    }

    private void validateLot(Product product, String lotNumber) {
        String normalizedLot = normalizeOptional(lotNumber, 100, "lotNumber");
        if (Boolean.TRUE.equals(product.getTrackLot()) && (normalizedLot == null || normalizedLot.isBlank())) {
            throw DispatchManagementException.badRequest("Lot number is required for lot-tracked products");
        }
    }

    private BigDecimal validateQty(BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw DispatchManagementException.badRequest("Quantity must be greater than zero");
        }
        return qty;
    }

    private void assertDraft(DispatchDocument dispatch, String message) {
        if (dispatch.getStatus() != DispatchStatus.DRAFT) {
            throw DispatchManagementException.conflict(message);
        }
    }

    private void assertActor(String actor) {
        requireActor(actor);
    }

    private String requireActor(String actor) {
        String normalized = normalizeOptional(actor, 255, "actor");
        if (normalized == null) {
            throw DispatchManagementException.badRequest("actor is required");
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
            throw DispatchManagementException.badRequest(fieldName + " exceeds max length " + maxLength);
        }
        return normalized;
    }

    private Specification<DispatchDocument> buildDispatchSpecification(DispatchStatus status, String search) {
        String normalizedSearch = normalizeSearch(search);
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (normalizedSearch != null) {
                String like = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
                Predicate referenceMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("reference")), like);
                Predicate destinationMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("destination")), like);
                UUID parsedId = tryParseUuid(normalizedSearch);
                if (parsedId != null) {
                    Predicate idMatch = criteriaBuilder.equal(root.get("id"), parsedId);
                    predicates.add(criteriaBuilder.or(referenceMatch, destinationMatch, idMatch));
                } else {
                    predicates.add(criteriaBuilder.or(referenceMatch, destinationMatch));
                }
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

    private UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String buildDocumentLineNote(
            String prefix,
            String reference,
            String destination,
            UUID documentId,
            String lineNotes) {
        String normalizedLineNotes = normalizeOptional(lineNotes, 500, "notes");
        String normalizedReference = normalizeOptional(reference, 120, "reference");
        String normalizedDestination = normalizeOptional(destination, 200, "destination");
        String suffix = normalizedReference != null ? normalizedReference
                : normalizedDestination != null ? normalizedDestination : "NO_REF";
        String documentPart = prefix + " " + suffix + " (" + documentId + ")";

        if (normalizedLineNotes == null) {
            return documentPart;
        }
        return normalizedLineNotes + " | " + documentPart;
    }

    private DispatchListItem toListItem(DispatchDocument dispatch) {
        return new DispatchListItem(
                dispatch.getId(),
                dispatch.getDestination(),
                dispatch.getReference(),
                dispatch.getNotes(),
                dispatch.getStatus(),
                dispatch.getCreatedBy(),
                dispatch.getCreatedAt(),
                dispatch.getPostedAt(),
                dispatch.getPostedBy(),
                dispatch.getVoidedAt(),
                dispatch.getVoidedBy());
    }

    private DispatchDetailResult toDetailResult(
            DispatchDocument dispatch,
            List<DispatchLine> lines,
            Map<UUID, Product> productsById,
            Map<UUID, LayoutBlock> locationsById) {

        List<DispatchLineResult> lineResults = lines.stream()
                .map(line -> toLineResult(line, productsById, locationsById))
                .toList();

        return new DispatchDetailResult(
                dispatch.getId(),
                dispatch.getDestination(),
                dispatch.getReference(),
                dispatch.getNotes(),
                dispatch.getStatus(),
                dispatch.getCreatedBy(),
                dispatch.getCreatedAt(),
                dispatch.getPostedAt(),
                dispatch.getPostedBy(),
                dispatch.getVoidedAt(),
                dispatch.getVoidedBy(),
                lineResults);
    }

    private DispatchLineResult toLineResult(
            DispatchLine line,
            Map<UUID, Product> productsById,
            Map<UUID, LayoutBlock> locationsById) {
        Product product = productsById.get(line.getProductId());
        LayoutBlock location = locationsById.get(line.getSourceLocationId());

        return new DispatchLineResult(
                line.getId(),
                line.getDispatchId(),
                line.getProductId(),
                product != null ? product.getSku() : null,
                product != null ? product.getName() : null,
                line.getSourceLocationId(),
                resolveLocationPathLabel(location, line.getSourceLocationId()),
                line.getQty(),
                line.getLotNumber(),
                line.getNotes(),
                line.getPosition());
    }

    private String resolveLocationPathLabel(LayoutBlock location, UUID fallbackId) {
        if (location == null) {
            return fallbackId != null ? fallbackId.toString() : null;
        }
        if (location.getFullCode() != null && !location.getFullCode().isBlank()) {
            return location.getFullCode();
        }
        if (location.getScanCode() != null && !location.getScanCode().isBlank()) {
            return location.getScanCode();
        }
        String computedPath = buildLocationPath(location);
        if (computedPath != null && !computedPath.isBlank()) {
            return computedPath;
        }
        if (location.getId() != null) {
            return location.getId().toString();
        }
        return fallbackId != null ? fallbackId.toString() : null;
    }

    private String buildLocationPath(LayoutBlock leaf) {
        List<LayoutBlock> chain = new ArrayList<>();
        LayoutBlock current = leaf;
        Set<UUID> visited = new HashSet<>();

        while (current != null && current.getId() != null && visited.add(current.getId())) {
            chain.add(0, current);
            UUID parentId = current.getParentId();
            if (parentId == null) {
                break;
            }
            current = layoutBlockRepository.findById(parentId).orElse(null);
        }

        return chain.stream()
                .map(this::formatPathSegment)
                .filter(segment -> segment != null && !segment.isBlank())
                .collect(Collectors.joining("->"));
    }

    private String formatPathSegment(LayoutBlock block) {
        BlockTemplate template = blockTemplateRepository.findById(block.getBlockTemplateId()).orElse(null);
        String identifier = resolveIdentifier(block, template);
        if (identifier != null && !identifier.isBlank()) {
            return identifier;
        }
        return String.valueOf(block.getPosition() + 1);
    }

    private String resolveIdentifier(LayoutBlock block, BlockTemplate template) {
        if (template == null || template.getIdentifierFormat() == null) {
            return null;
        }
        if (template.getIdentifierFormat() == BlockTemplate.IdentifierFormat.NUMERIC) {
            return String.valueOf(block.getPosition() + 1);
        }
        if (template.getIdentifierFormat() == BlockTemplate.IdentifierFormat.ALPHA) {
            return toAlphabeticIdentifier(block.getPosition());
        }
        return null;
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

    public record DispatchPageResult(
            List<DispatchListItem> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record DispatchListItem(
            UUID id,
            String destination,
            String reference,
            String notes,
            DispatchStatus status,
            String createdBy,
            Instant createdAt,
            Instant postedAt,
            String postedBy,
            Instant voidedAt,
            String voidedBy) {
    }

    public record DispatchDetailResult(
            UUID id,
            String destination,
            String reference,
            String notes,
            DispatchStatus status,
            String createdBy,
            Instant createdAt,
            Instant postedAt,
            String postedBy,
            Instant voidedAt,
            String voidedBy,
            List<DispatchLineResult> lines) {
    }

    public record DispatchLineResult(
            UUID id,
            UUID dispatchId,
            UUID productId,
            String productSku,
            String productName,
            UUID sourceLocationId,
            String locationPathLabel,
            BigDecimal qty,
            String lotNumber,
            String notes,
            int position) {
    }
}
