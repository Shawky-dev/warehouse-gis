package com.warehouse.warehouse_platform.tenant.counting;

import com.warehouse.warehouse_platform.tenant.inventory.InventoryLedgerService;
import com.warehouse.warehouse_platform.tenant.inventory.StockEntry;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@SuppressWarnings("null")
public class CountSessionService {

    private static final String COUNT_ADJUSTMENT_REASON_CODE = "COUNT_ADJUSTMENT";

    private final CountSessionRepository countSessionRepository;
    private final CountLineRepository countLineRepository;
    private final LayoutBlockRepository layoutBlockRepository;
    private final BlockTemplateRepository blockTemplateRepository;
    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryLedgerService inventoryLedgerService;

    public CountSessionService(
            CountSessionRepository countSessionRepository,
            CountLineRepository countLineRepository,
            LayoutBlockRepository layoutBlockRepository,
            BlockTemplateRepository blockTemplateRepository,
            ProductRepository productRepository,
            StockMovementRepository stockMovementRepository,
            InventoryLedgerService inventoryLedgerService) {
        this.countSessionRepository = countSessionRepository;
        this.countLineRepository = countLineRepository;
        this.layoutBlockRepository = layoutBlockRepository;
        this.blockTemplateRepository = blockTemplateRepository;
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.inventoryLedgerService = inventoryLedgerService;
    }

    @Transactional
    public CountSessionDetailResult openSession(String name, List<UUID> locationIds, String actor) {
        String normalizedName = normalizeRequired(name, 120, "name");
        String normalizedActor = requireActor(actor);
        List<UUID> normalizedLocationIds = normalizeAndValidateLocationIds(locationIds);

        CountSession session = CountSession.builder()
                .name(normalizedName)
                .status(CountStatus.OPEN)
                .createdBy(normalizedActor)
                .locationIds(new LinkedHashSet<>(normalizedLocationIds))
                .build();
        CountSession savedSession = countSessionRepository.save(session);

        List<StockEntry> snapshot = normalizedLocationIds.isEmpty()
                ? List.of()
                : stockMovementRepository.findStockByLocationIds(normalizedLocationIds);

        List<CountLine> linesToCreate = snapshot.stream()
                .map(entry -> CountLine.builder()
                        .sessionId(savedSession.getId())
                        .locationId(entry.getLocationId())
                        .productId(entry.getProductId())
                        .lotNumber(entry.getLotNumber())
                        .expectedQty(entry.getQtyStock())
                        .countedQty(null)
                        .build())
                .toList();

        List<CountLine> savedLines = linesToCreate.isEmpty() ? List.of() : countLineRepository.saveAll(linesToCreate);

        Map<UUID, LayoutBlock> locationsById = layoutBlockRepository.findAllById(normalizedLocationIds).stream()
                .collect(Collectors.toMap(LayoutBlock::getId, Function.identity()));
        Map<UUID, Product> productsById = productRepository.findAllById(savedLines.stream()
                .map(CountLine::getProductId)
                .distinct()
                .toList()).stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        return toDetailResult(savedSession, savedLines, locationsById, productsById);
    }

    @Transactional
    public CountLineResult updateCountLine(UUID sessionId, UUID lineId, BigDecimal countedQty, String actor) {
        assertActor(actor);
        if (countedQty == null) {
            throw CountSessionManagementException.badRequest("countedQty is required");
        }
        if (countedQty.compareTo(BigDecimal.ZERO) < 0) {
            throw CountSessionManagementException.badRequest("countedQty must be greater than or equal to zero");
        }

        CountSession session = loadCountSession(sessionId);
        assertOpen(session, "Only open count sessions can be modified");

        CountLine line = countLineRepository.findBySessionIdAndId(sessionId, lineId)
                .orElseThrow(() -> CountSessionManagementException.notFound("Count line not found: " + lineId));

        line.setCountedQty(countedQty);
        CountLine saved = countLineRepository.save(line);

        LayoutBlock location = layoutBlockRepository.findById(saved.getLocationId())
                .orElseThrow(() -> CountSessionManagementException.badRequest(
                        "Location not found: " + saved.getLocationId()));
        Product product = productRepository.findById(saved.getProductId())
                .orElseThrow(() -> CountSessionManagementException.badRequest(
                        "Product not found: " + saved.getProductId()));

        return toLineResult(saved, Map.of(location.getId(), location), Map.of(product.getId(), product));
    }

    @Transactional
    public CountSessionDetailResult postSession(UUID sessionId, String actor) {
        String normalizedActor = requireActor(actor);
        CountSession session = loadCountSession(sessionId);
        assertOpen(session, "Only open count sessions can be posted");

        List<CountLine> lines = countLineRepository
                .findBySessionIdOrderByLocationIdAscProductIdAscLotNumberAsc(sessionId);
        long missingCount = lines.stream().filter(line -> line.getCountedQty() == null).count();
        if (missingCount > 0) {
            throw CountSessionManagementException.badRequest(
                    "All count lines must be filled before posting. Missing count: " + missingCount);
        }

        for (CountLine line : lines) {
            BigDecimal variance = line.getCountedQty().subtract(line.getExpectedQty());
            if (variance.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            inventoryLedgerService.adjust(
                    line.getLocationId(),
                    line.getProductId(),
                    variance,
                    line.getLotNumber(),
                    "Count session " + session.getName() + " (" + sessionId + ")",
                    sessionId,
                    COUNT_ADJUSTMENT_REASON_CODE,
                    normalizedActor);
        }

        session.setStatus(CountStatus.POSTED);
        session.setPostedAt(Instant.now());
        session.setPostedBy(normalizedActor);
        session.setVoidedAt(null);
        session.setVoidedBy(null);

        CountSession saved = countSessionRepository.save(session);
        return getSession(saved.getId());
    }

    @Transactional
    public CountSessionDetailResult voidSession(UUID sessionId, String actor) {
        String normalizedActor = requireActor(actor);
        CountSession session = loadCountSession(sessionId);
        assertOpen(session, "Only open count sessions can be voided");

        session.setStatus(CountStatus.VOID);
        session.setVoidedAt(Instant.now());
        session.setVoidedBy(normalizedActor);

        CountSession saved = countSessionRepository.save(session);
        return getSession(saved.getId());
    }

    @Transactional
    public void deleteDraft(UUID sessionId, String actor) {
        assertActor(actor);
        CountSession session = loadCountSession(sessionId);
        assertOpen(session, "Only open count sessions can be deleted");

        countLineRepository.deleteBySessionId(sessionId);
        countSessionRepository.delete(session);
    }

    @Transactional(readOnly = true)
    public CountSessionPageResult listSessions(int page, int size, CountStatus status, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CountSession> result = countSessionRepository.findAll(buildSessionSpecification(status, search), pageable);

        List<CountSessionListItem> content = result.getContent().stream()
                .map(session -> new CountSessionListItem(
                        session.getId(),
                        session.getName(),
                        session.getStatus(),
                        session.getCreatedBy(),
                        session.getCreatedAt(),
                        session.getPostedAt(),
                        session.getPostedBy(),
                        session.getVoidedAt(),
                        session.getVoidedBy(),
                        session.getLocationIds() != null ? session.getLocationIds().size() : 0,
                        countLineRepository.countBySessionId(session.getId())))
                .toList();

        return new CountSessionPageResult(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public CountSessionDetailResult getSession(UUID sessionId) {
        CountSession session = loadCountSession(sessionId);
        List<CountLine> lines = countLineRepository
                .findBySessionIdOrderByLocationIdAscProductIdAscLotNumberAsc(sessionId);

        Map<UUID, LayoutBlock> locationsById = layoutBlockRepository.findAllById(lines.stream()
                .map(CountLine::getLocationId)
                .distinct()
                .toList())
                .stream()
                .collect(Collectors.toMap(LayoutBlock::getId, Function.identity()));

        Map<UUID, Product> productsById = productRepository.findAllById(lines.stream()
                .map(CountLine::getProductId)
                .distinct()
                .toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        if (session.getLocationIds() != null && !session.getLocationIds().isEmpty()) {
            Map<UUID, LayoutBlock> sessionLocations = layoutBlockRepository.findAllById(session.getLocationIds())
                    .stream()
                    .collect(Collectors.toMap(LayoutBlock::getId, Function.identity()));
            locationsById.putAll(sessionLocations);
        }

        return toDetailResult(session, lines, locationsById, productsById);
    }

    private CountSession loadCountSession(UUID sessionId) {
        return countSessionRepository.findById(sessionId)
                .orElseThrow(() -> CountSessionManagementException.notFound("Count session not found: " + sessionId));
    }

    private void assertOpen(CountSession session, String message) {
        if (session.getStatus() != CountStatus.OPEN) {
            throw CountSessionManagementException.conflict(message);
        }
    }

    private List<UUID> normalizeAndValidateLocationIds(List<UUID> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            throw CountSessionManagementException.badRequest("At least one location is required");
        }

        List<UUID> normalized = new ArrayList<>(new LinkedHashSet<>(locationIds));
        List<LayoutBlock> locations = layoutBlockRepository.findAllById(normalized);
        Map<UUID, LayoutBlock> locationMap = locations.stream()
                .collect(Collectors.toMap(LayoutBlock::getId, Function.identity()));

        for (UUID locationId : normalized) {
            if (!locationMap.containsKey(locationId)) {
                throw CountSessionManagementException.badRequest("Location not found: " + locationId);
            }
            if (layoutBlockRepository.existsByParentId(locationId)) {
                throw CountSessionManagementException.badRequest(
                        "Location must be a leaf block: " + locationId);
            }
        }

        return normalized;
    }

    private void assertActor(String actor) {
        requireActor(actor);
    }

    private String requireActor(String actor) {
        return normalizeRequired(actor, 255, "actor");
    }

    private String normalizeRequired(String value, int maxLength, String fieldName) {
        String normalized = normalizeOptional(value, maxLength, fieldName);
        if (normalized == null) {
            throw CountSessionManagementException.badRequest(fieldName + " is required");
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
            throw CountSessionManagementException.badRequest(fieldName + " exceeds max length " + maxLength);
        }
        return normalized;
    }

    private Specification<CountSession> buildSessionSpecification(CountStatus status, String search) {
        String normalizedSearch = normalizeSearch(search);
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (normalizedSearch != null) {
                String like = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
                Predicate nameMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), like);
                UUID parsedId = tryParseUuid(normalizedSearch);
                if (parsedId != null) {
                    Predicate idMatch = criteriaBuilder.equal(root.get("id"), parsedId);
                    predicates.add(criteriaBuilder.or(nameMatch, idMatch));
                } else {
                    predicates.add(criteriaBuilder.or(nameMatch));
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

    private CountSessionDetailResult toDetailResult(
            CountSession session,
            List<CountLine> lines,
            Map<UUID, LayoutBlock> locationsById,
            Map<UUID, Product> productsById) {
        List<UUID> locationIds = session.getLocationIds() == null
                ? List.of()
                : new ArrayList<>(session.getLocationIds());

        List<CountLineResult> lineResults = lines.stream()
                .map(line -> toLineResult(line, locationsById, productsById))
                .toList();

        return new CountSessionDetailResult(
                session.getId(),
                session.getName(),
                session.getStatus(),
                session.getCreatedBy(),
                session.getCreatedAt(),
                session.getPostedAt(),
                session.getPostedBy(),
                session.getVoidedAt(),
                session.getVoidedBy(),
                locationIds,
                lineResults);
    }

    private CountLineResult toLineResult(
            CountLine line,
            Map<UUID, LayoutBlock> locationsById,
            Map<UUID, Product> productsById) {
        LayoutBlock location = locationsById.get(line.getLocationId());
        Product product = productsById.get(line.getProductId());

        BigDecimal variance = line.getVariance();
        if (variance == null && line.getCountedQty() != null && line.getExpectedQty() != null) {
            variance = line.getCountedQty().subtract(line.getExpectedQty());
        }

        return new CountLineResult(
                line.getId(),
                line.getSessionId(),
                line.getLocationId(),
                resolveLocationPathLabel(location, line.getLocationId()),
                line.getProductId(),
                product != null ? product.getSku() : null,
                product != null ? product.getName() : null,
                line.getLotNumber(),
                line.getExpectedQty(),
                line.getCountedQty(),
                variance);
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

    public record CountSessionPageResult(
            List<CountSessionListItem> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record CountSessionListItem(
            UUID id,
            String name,
            CountStatus status,
            String createdBy,
            Instant createdAt,
            Instant postedAt,
            String postedBy,
            Instant voidedAt,
            String voidedBy,
            int locationCount,
            long lineCount) {
        @com.fasterxml.jackson.annotation.JsonProperty
        public String qrData() {
            return "COUNT:" + id;
        }
    }

    public record CountSessionDetailResult(
            UUID id,
            String name,
            CountStatus status,
            String createdBy,
            Instant createdAt,
            Instant postedAt,
            String postedBy,
            Instant voidedAt,
            String voidedBy,
            List<UUID> locationIds,
            List<CountLineResult> lines) {
        @com.fasterxml.jackson.annotation.JsonProperty
        public String qrData() {
            return "COUNT:" + id;
        }
    }

    public record CountLineResult(
            UUID id,
            UUID sessionId,
            UUID locationId,
            String locationPathLabel,
            UUID productId,
            String productSku,
            String productName,
            String lotNumber,
            BigDecimal expectedQty,
            BigDecimal countedQty,
            BigDecimal variance) {
    }
}