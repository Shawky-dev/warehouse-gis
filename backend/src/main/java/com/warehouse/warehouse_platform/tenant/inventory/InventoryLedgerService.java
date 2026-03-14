package com.warehouse.warehouse_platform.tenant.inventory;

import com.warehouse.warehouse_platform.tenant.product.Product;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplate;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplateRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlock;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayout;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayoutRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InventoryLedgerService {

    private final StockMovementRepository movementRepository;
    private final LayoutBlockRepository layoutBlockRepository;
    private final ProductRepository productRepository;
    private final WarehouseLayoutRepository warehouseLayoutRepository;
    private final BlockTemplateRepository blockTemplateRepository;

    public InventoryLedgerService(
            StockMovementRepository movementRepository,
            LayoutBlockRepository layoutBlockRepository,
            ProductRepository productRepository,
            WarehouseLayoutRepository warehouseLayoutRepository,
            BlockTemplateRepository blockTemplateRepository) {
        this.movementRepository = movementRepository;
        this.layoutBlockRepository = layoutBlockRepository;
        this.productRepository = productRepository;
        this.warehouseLayoutRepository = warehouseLayoutRepository;
        this.blockTemplateRepository = blockTemplateRepository;
    }

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
        assertSelectableLocation(locationId);
        Product product = loadProduct(productId);

        StockMovement movement = StockMovement.builder()
                .locationId(locationId)
                .productId(product.getId())
                .qty(qty)
                .type(MovementType.RECEIVE)
                .lotNumber(normalizeOptional(lotNumber))
                .expiryDate(expiryDate)
                .notes(normalizeOptional(notes))
                .createdBy(actor)
                .build();

        return enrichMovement(movementRepository.save(movement), null, null);
    }

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

        assertSelectableLocation(fromLocationId);
        assertSelectableLocation(toLocationId);
        Product product = loadProduct(productId);
        assertSufficientStock(fromLocationId, product.getId(), qty);

        UUID referenceId = UUID.randomUUID();

        StockMovement out = StockMovement.builder()
                .locationId(fromLocationId)
                .productId(product.getId())
                .qty(qty.negate())
                .type(MovementType.TRANSFER_OUT)
                .referenceId(referenceId)
                .lotNumber(normalizeOptional(lotNumber))
                .notes(normalizeOptional(notes))
                .createdBy(actor)
                .build();

        StockMovement in = StockMovement.builder()
                .locationId(toLocationId)
                .productId(product.getId())
                .qty(qty)
                .type(MovementType.TRANSFER_IN)
                .referenceId(referenceId)
                .lotNumber(normalizeOptional(lotNumber))
                .notes(normalizeOptional(notes))
                .createdBy(actor)
                .build();

        StockMovement savedOut = movementRepository.save(out);
        StockMovement savedIn = movementRepository.save(in);
        ProductSummary productSummary = toProductSummary(product);
        Map<UUID, LocationSummary> locations = buildLocationSummaryMap(List.of(fromLocationId, toLocationId));

        return new TransferResult(
                referenceId,
                enrichMovement(savedOut, locations, Map.of(product.getId(), productSummary), savedIn.getLocationId()),
                enrichMovement(savedIn, locations, Map.of(product.getId(), productSummary), savedOut.getLocationId()));
    }

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

        assertSelectableLocation(locationId);
        Product product = loadProduct(productId);

        if (qty.compareTo(BigDecimal.ZERO) < 0) {
            assertSufficientStock(locationId, product.getId(), qty.abs());
        }

        String normalizedNotes = normalizeRequiredNotes(notes);

        StockMovement movement = StockMovement.builder()
                .locationId(locationId)
                .productId(product.getId())
                .qty(qty)
                .type(MovementType.ADJUST)
                .notes(normalizedNotes)
                .createdBy(actor)
                .build();

        return enrichMovement(movementRepository.save(movement), null, Map.of(product.getId(), toProductSummary(product)));
    }

    @Transactional(readOnly = true)
    public List<OnHandResult> getOnHand(UUID productId, UUID locationId) {
        if (locationId != null) {
            assertLocationExists(locationId);
        }
        if (productId != null) {
            loadProduct(productId);
        }

        List<OnHandEntry> rows = switch (buildFilterKey(productId, locationId)) {
            case BOTH -> movementRepository.findOnHandByLocationAndProduct(locationId, productId);
            case PRODUCT -> movementRepository.findOnHandByProduct(productId);
            case LOCATION -> movementRepository.findOnHandByLocation(locationId);
            case NONE -> movementRepository.findAllOnHand();
        };

        return enrichOnHand(rows);
    }

    @Transactional(readOnly = true)
    public MovementPageResult getMovements(UUID productId, UUID locationId, int page, int size) {
        if (locationId != null) {
            assertLocationExists(locationId);
        }
        if (productId != null) {
            loadProduct(productId);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StockMovement> result = switch (buildFilterKey(productId, locationId)) {
            case BOTH -> movementRepository.findByLocationIdAndProductIdOrderByCreatedAtDesc(locationId, productId, pageable);
            case PRODUCT -> movementRepository.findByProductIdOrderByCreatedAtDesc(productId, pageable);
            case LOCATION -> movementRepository.findByLocationIdOrderByCreatedAtDesc(locationId, pageable);
            case NONE -> movementRepository.findAllByOrderByCreatedAtDesc(pageable);
        };
        return toPageResult(result);
    }

    @Transactional(readOnly = true)
    public ProductLookupPageResult listProductLookups(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sku"));
        Page<Product> products = productRepository.findAll(buildProductLookupSpecification(search), pageable);
        List<ProductLookupItem> content = products.getContent().stream()
                .map(product -> new ProductLookupItem(
                        product.getId(),
                        product.getSku(),
                        product.getName(),
                        product.getBaseUom().getCode(),
                        Boolean.TRUE.equals(product.getTrackLot()),
                        Boolean.TRUE.equals(product.getTrackExpiry()),
                        Boolean.TRUE.equals(product.getActive())))
                .toList();

        return new ProductLookupPageResult(
                content,
                products.getNumber(),
                products.getSize(),
                products.getTotalElements(),
                products.getTotalPages());
    }

    @Transactional(readOnly = true)
    public LocationLookupPageResult listLocationLookups(int page, int size, String search) {
        Optional<WarehouseLayout> activeLayout = warehouseLayoutRepository.findByIsActiveTrue();
        if (activeLayout.isEmpty()) {
            return new LocationLookupPageResult(List.of(), page, size, 0, 0);
        }

        WarehouseLayout layout = activeLayout.get();
        List<LayoutBlock> blocks = layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(layout.getId());
        if (blocks.isEmpty()) {
            return new LocationLookupPageResult(List.of(), page, size, 0, 0);
        }

        Map<UUID, LayoutBlock> blockById = blocks.stream()
                .collect(Collectors.toMap(LayoutBlock::getId, block -> block));
        Set<UUID> parentIds = blocks.stream()
                .map(LayoutBlock::getParentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<UUID, BlockTemplate> templateById = loadTemplateMap(blocks);

        List<LocationLookupItem> filtered = blocks.stream()
                .filter(block -> !parentIds.contains(block.getId()))
                .map(block -> toLocationLookupItem(layout, block, blockById, templateById))
                .filter(item -> matchesLocationSearch(item, search))
                .sorted(Comparator.comparing(LocationLookupItem::pathLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return paginateList(filtered, page, size, LocationLookupPageResult::new);
    }

    private enum FilterKey {
        NONE,
        PRODUCT,
        LOCATION,
        BOTH
    }

    private FilterKey buildFilterKey(UUID productId, UUID locationId) {
        if (productId != null && locationId != null) {
            return FilterKey.BOTH;
        }
        if (productId != null) {
            return FilterKey.PRODUCT;
        }
        if (locationId != null) {
            return FilterKey.LOCATION;
        }
        return FilterKey.NONE;
    }

    private void assertSelectableLocation(UUID locationId) {
        WarehouseLayout activeLayout = warehouseLayoutRepository.findByIsActiveTrue()
                .orElseThrow(() -> InventoryLedgerException.badRequest(
                        "Inventory operations require an active warehouse layout"));

        List<LayoutBlock> blocks = layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(activeLayout.getId());
        if (blocks.isEmpty()) {
            throw InventoryLedgerException.badRequest(
                    "Inventory operations require at least one selectable location in the active layout");
        }

        Set<UUID> parentIds = blocks.stream()
                .map(LayoutBlock::getParentId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        boolean existsInActiveLayout = blocks.stream().anyMatch(block -> block.getId().equals(locationId));
        boolean selectable = existsInActiveLayout && !parentIds.contains(locationId);

        if (!existsInActiveLayout) {
            if (layoutBlockRepository.existsById(locationId)) {
                throw InventoryLedgerException.badRequest(
                        "Location is not selectable for inventory operations in the active layout");
            }
            throw InventoryLedgerException.notFound("Location not found: " + locationId);
        }
        if (!selectable) {
            throw InventoryLedgerException.badRequest(
                    "Location is not selectable for inventory operations in the active layout");
        }
    }

    private void assertLocationExists(UUID locationId) {
        if (!layoutBlockRepository.existsById(locationId)) {
            throw InventoryLedgerException.notFound("Location not found: " + locationId);
        }
    }

    private Product loadProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> InventoryLedgerException.notFound("Product not found: " + productId));
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

    private String normalizeRequiredNotes(String notes) {
        String normalized = normalizeOptional(notes);
        if (normalized == null) {
            throw InventoryLedgerException.badRequest("A reason note is required for adjustments");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<OnHandResult> enrichOnHand(List<OnHandEntry> rows) {
        Set<UUID> productIds = rows.stream().map(OnHandEntry::getProductId).collect(Collectors.toSet());
        Set<UUID> locationIds = rows.stream().map(OnHandEntry::getLocationId).collect(Collectors.toSet());
        Map<UUID, ProductSummary> products = loadProductSummaryMap(productIds);
        Map<UUID, LocationSummary> locations = buildLocationSummaryMap(locationIds);

        return rows.stream()
                .map(row -> {
                    ProductSummary product = products.get(row.getProductId());
                    LocationSummary location = locations.get(row.getLocationId());
                    return new OnHandResult(
                            row.getLocationId(),
                            row.getProductId(),
                            row.getQtyOnHand(),
                            location == null ? null : location.label(),
                            location == null ? null : location.pathLabel(),
                            location == null ? null : location.layoutId(),
                            location == null ? null : location.layoutName(),
                            location == null ? null : location.identifier(),
                            location == null ? null : location.side(),
                            product == null ? null : product.sku(),
                            product == null ? null : product.name(),
                            product == null ? null : product.baseUomCode(),
                            product == null ? null : product.trackLot(),
                            product == null ? null : product.trackExpiry());
                })
                .toList();
    }

    private MovementPageResult toPageResult(Page<StockMovement> page) {
        List<StockMovement> rows = page.getContent();
        Set<UUID> productIds = rows.stream().map(StockMovement::getProductId).collect(Collectors.toSet());
        Set<UUID> locationIds = rows.stream().map(StockMovement::getLocationId).collect(Collectors.toSet());
        List<UUID> transferRefs = rows.stream()
                .map(StockMovement::getReferenceId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        List<StockMovement> pairedTransfers = transferRefs.isEmpty()
                ? List.of()
                : movementRepository.findByReferenceIdIn(transferRefs);

        pairedTransfers.stream()
                .map(StockMovement::getLocationId)
                .forEach(locationIds::add);
        pairedTransfers.stream()
                .map(StockMovement::getProductId)
                .forEach(productIds::add);

        Map<UUID, ProductSummary> products = loadProductSummaryMap(productIds);
        Map<UUID, LocationSummary> locations = buildLocationSummaryMap(locationIds);
        Map<UUID, UUID> counterpartLocationByMovementId = buildCounterpartLocationMap(rows, pairedTransfers);

        return new MovementPageResult(
                rows.stream()
                        .map(row -> enrichMovement(
                                row,
                                locations,
                                products,
                                counterpartLocationByMovementId.get(row.getId())))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private MovementResult enrichMovement(
            StockMovement movement,
            Map<UUID, LocationSummary> locationSummaryMap,
            Map<UUID, ProductSummary> productSummaryMap) {
        return enrichMovement(movement, locationSummaryMap, productSummaryMap, null);
    }

    private MovementResult enrichMovement(
            StockMovement movement,
            Map<UUID, LocationSummary> locationSummaryMap,
            Map<UUID, ProductSummary> productSummaryMap,
            UUID counterpartLocationId) {
        Map<UUID, LocationSummary> resolvedLocations = locationSummaryMap == null
                ? buildLocationSummaryMap(buildLocationIds(movement.getLocationId(), counterpartLocationId))
                : locationSummaryMap;
        Map<UUID, ProductSummary> resolvedProducts = productSummaryMap == null
                ? loadProductSummaryMap(List.of(movement.getProductId()))
                : productSummaryMap;

        LocationSummary location = resolvedLocations.get(movement.getLocationId());
        LocationSummary counterpart = counterpartLocationId == null ? null : resolvedLocations.get(counterpartLocationId);
        ProductSummary product = resolvedProducts.get(movement.getProductId());

        return new MovementResult(
                movement.getId(),
                movement.getLocationId(),
                movement.getProductId(),
                movement.getQty(),
                movement.getType(),
                movement.getReferenceId(),
                movement.getLotNumber(),
                movement.getExpiryDate(),
                movement.getNotes(),
                movement.getCreatedBy(),
                movement.getCreatedAt(),
                location == null ? null : location.label(),
                location == null ? null : location.pathLabel(),
                location == null ? null : location.layoutId(),
                location == null ? null : location.layoutName(),
                location == null ? null : location.identifier(),
                location == null ? null : location.side(),
                product == null ? null : product.sku(),
                product == null ? null : product.name(),
                product == null ? null : product.baseUomCode(),
                product == null ? null : product.trackLot(),
                product == null ? null : product.trackExpiry(),
                counterpartLocationId,
                counterpart == null ? null : counterpart.label(),
                counterpart == null ? null : counterpart.pathLabel());
    }

    private List<UUID> buildLocationIds(UUID locationId, UUID counterpartLocationId) {
        List<UUID> ids = new ArrayList<>();
        if (locationId != null) {
            ids.add(locationId);
        }
        if (counterpartLocationId != null) {
            ids.add(counterpartLocationId);
        }
        return ids;
    }

    private Map<UUID, UUID> buildCounterpartLocationMap(List<StockMovement> currentPage, List<StockMovement> pairedTransfers) {
        Map<UUID, List<StockMovement>> byReference = pairedTransfers.stream()
                .filter(movement -> movement.getReferenceId() != null)
                .collect(Collectors.groupingBy(StockMovement::getReferenceId));
        Map<UUID, UUID> result = new HashMap<>();

        for (StockMovement movement : currentPage) {
            UUID referenceId = movement.getReferenceId();
            if (referenceId == null) {
                continue;
            }
            List<StockMovement> siblings = byReference.getOrDefault(referenceId, List.of());
            siblings.stream()
                    .filter(candidate -> !candidate.getId().equals(movement.getId()))
                    .findFirst()
                    .ifPresent(candidate -> result.put(movement.getId(), candidate.getLocationId()));
        }
        return result;
    }

    private Map<UUID, ProductSummary> loadProductSummaryMap(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }

        return productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, this::toProductSummary));
    }

    private ProductSummary toProductSummary(Product product) {
        return new ProductSummary(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getBaseUom().getCode(),
                Boolean.TRUE.equals(product.getTrackLot()),
                Boolean.TRUE.equals(product.getTrackExpiry()));
    }

    private Map<UUID, LocationSummary> buildLocationSummaryMap(Collection<UUID> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> filteredIds = locationIds.stream().filter(id -> id != null).distinct().toList();
        if (filteredIds.isEmpty()) {
            return Map.of();
        }

        List<LayoutBlock> blocks = layoutBlockRepository.findAllById(filteredIds);
        if (blocks.isEmpty()) {
            return Map.of();
        }

        Set<UUID> layoutIds = blocks.stream().map(LayoutBlock::getLayoutId).collect(Collectors.toSet());
        List<LayoutBlock> layoutScopeBlocks = new ArrayList<>(blocks);
        for (UUID layoutId : layoutIds) {
            layoutScopeBlocks.addAll(layoutBlockRepository.findByLayoutIdOrderByParentIdAscPositionAsc(layoutId));
        }

        Map<UUID, LayoutBlock> blockById = layoutScopeBlocks.stream()
                .collect(Collectors.toMap(LayoutBlock::getId, block -> block, (left, right) -> left, LinkedHashMap::new));
        Map<UUID, BlockTemplate> templateById = loadTemplateMap(layoutScopeBlocks);
        Map<UUID, WarehouseLayout> layoutById = warehouseLayoutRepository.findAllById(layoutIds).stream()
                .collect(Collectors.toMap(WarehouseLayout::getId, layout -> layout));

        Map<UUID, LocationSummary> summaries = new HashMap<>();
        for (LayoutBlock block : blocks) {
            WarehouseLayout layout = layoutById.get(block.getLayoutId());
            summaries.put(block.getId(), toLocationSummary(block, blockById, templateById, layout));
        }
        return summaries;
    }

    private Map<UUID, BlockTemplate> loadTemplateMap(Collection<LayoutBlock> blocks) {
        Set<UUID> templateIds = blocks.stream()
                .map(LayoutBlock::getBlockTemplateId)
                .collect(Collectors.toSet());

        return blockTemplateRepository.findAllById(templateIds).stream()
                .collect(Collectors.toMap(BlockTemplate::getId, template -> template));
    }

    private LocationLookupItem toLocationLookupItem(
            WarehouseLayout layout,
            LayoutBlock block,
            Map<UUID, LayoutBlock> blockById,
            Map<UUID, BlockTemplate> templateById) {
        LocationSummary summary = toLocationSummary(block, blockById, templateById, layout);
        return new LocationLookupItem(
                summary.id(),
                summary.layoutId(),
                summary.layoutName(),
                summary.label(),
                summary.pathLabel(),
                summary.identifier(),
                summary.side());
    }

    private LocationSummary toLocationSummary(
            LayoutBlock block,
            Map<UUID, LayoutBlock> blockById,
            Map<UUID, BlockTemplate> templateById,
            WarehouseLayout layout) {
        String label = formatBlockLabel(block, templateById.get(block.getBlockTemplateId()));
        String pathLabel = buildPathLabel(block, blockById, templateById);
        return new LocationSummary(
                block.getId(),
                block.getLayoutId(),
                layout == null ? null : layout.getName(),
                label,
                pathLabel,
                resolveIdentifier(block, templateById.get(block.getBlockTemplateId())),
                block.getSide());
    }

    private String buildPathLabel(
            LayoutBlock block,
            Map<UUID, LayoutBlock> blockById,
            Map<UUID, BlockTemplate> templateById) {
        List<String> labels = new ArrayList<>();
        LayoutBlock cursor = block;
        Set<UUID> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor.getId())) {
            labels.add(formatBlockLabel(cursor, templateById.get(cursor.getBlockTemplateId())));
            UUID parentId = cursor.getParentId();
            cursor = parentId == null ? null : blockById.get(parentId);
        }
        java.util.Collections.reverse(labels);
        return String.join(" / ", labels);
    }

    private String formatBlockLabel(LayoutBlock block, BlockTemplate template) {
        List<String> parts = new ArrayList<>();
        if (template != null && template.getName() != null && !template.getName().isBlank()) {
            parts.add(template.getName().trim());
        }
        String identifier = resolveIdentifier(block, template);
        if (identifier != null && !identifier.isBlank()) {
            parts.add(identifier);
        }
        if (block.getSide() != null && !block.getSide().isBlank()) {
            parts.add(block.getSide().trim());
        }
        if (parts.isEmpty()) {
            return block.getId().toString();
        }
        return String.join(" · ", parts);
    }

    private String resolveIdentifier(LayoutBlock block, BlockTemplate template) {
        if (template == null || template.getIdentifierFormat() == null) {
            return null;
        }
        return switch (template.getIdentifierFormat()) {
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

    private boolean matchesLocationSearch(LocationLookupItem item, String search) {
        String value = normalizeSearch(search);
        if (value == null) {
            return true;
        }
        return containsIgnoreCase(item.layoutName(), value)
                || containsIgnoreCase(item.label(), value)
                || containsIgnoreCase(item.pathLabel(), value)
                || containsIgnoreCase(item.identifier(), value)
                || containsIgnoreCase(item.side(), value);
    }

    private Specification<Product> buildProductLookupSpecification(String search) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("active"), true));

            String value = normalizeSearch(search);
            if (value != null) {
                String likeValue = "%" + value + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("sku")), likeValue),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), likeValue)));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String normalized = search.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean containsIgnoreCase(String source, String search) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(search);
    }

    private <T, R> R paginateList(
            List<T> items,
            int page,
            int size,
            LookupPageFactory<T, R> factory) {
        int fromIndex = Math.min(page * size, items.size());
        int toIndex = Math.min(fromIndex + size, items.size());
        List<T> content = items.subList(fromIndex, toIndex);
        int totalPages = items.isEmpty() ? 0 : (int) Math.ceil((double) items.size() / size);
        return factory.create(content, page, size, items.size(), totalPages);
    }

    @FunctionalInterface
    private interface LookupPageFactory<T, R> {
        R create(List<T> content, int page, int size, long totalElements, int totalPages);
    }

    private record ProductSummary(
            UUID id,
            String sku,
            String name,
            String baseUomCode,
            boolean trackLot,
            boolean trackExpiry) {
    }

    private record LocationSummary(
            UUID id,
            UUID layoutId,
            String layoutName,
            String label,
            String pathLabel,
            String identifier,
            String side) {
    }

    public record ProductLookupItem(
            UUID id,
            String sku,
            String name,
            String baseUomCode,
            boolean trackLot,
            boolean trackExpiry,
            boolean active) {
    }

    public record ProductLookupPageResult(
            List<ProductLookupItem> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record LocationLookupItem(
            UUID id,
            UUID layoutId,
            String layoutName,
            String label,
            String pathLabel,
            String identifier,
            String side) {
    }

    public record LocationLookupPageResult(
            List<LocationLookupItem> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record OnHandResult(
            UUID locationId,
            UUID productId,
            BigDecimal qtyOnHand,
            String locationLabel,
            String locationPathLabel,
            UUID layoutId,
            String layoutName,
            String locationIdentifier,
            String locationSide,
            String productSku,
            String productName,
            String baseUomCode,
            Boolean trackLot,
            Boolean trackExpiry) {
    }

    public record MovementResult(
            UUID id,
            UUID locationId,
            UUID productId,
            BigDecimal qty,
            MovementType type,
            UUID referenceId,
            String lotNumber,
            LocalDate expiryDate,
            String notes,
            String createdBy,
            Instant createdAt,
            String locationLabel,
            String locationPathLabel,
            UUID layoutId,
            String layoutName,
            String locationIdentifier,
            String locationSide,
            String productSku,
            String productName,
            String baseUomCode,
            Boolean trackLot,
            Boolean trackExpiry,
            UUID counterpartLocationId,
            String counterpartLocationLabel,
            String counterpartLocationPathLabel) {
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
