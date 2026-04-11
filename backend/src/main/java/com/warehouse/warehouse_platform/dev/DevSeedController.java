package com.warehouse.warehouse_platform.dev;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.audit.AuditLog;
import com.warehouse.warehouse_platform.tenant.audit.AuditLogRepository;
import com.warehouse.warehouse_platform.tenant.category.ProductCategory;
import com.warehouse.warehouse_platform.tenant.category.ProductCategoryRepository;
import com.warehouse.warehouse_platform.tenant.counting.CountLine;
import com.warehouse.warehouse_platform.tenant.counting.CountLineRepository;
import com.warehouse.warehouse_platform.tenant.counting.CountSession;
import com.warehouse.warehouse_platform.tenant.counting.CountSessionRepository;
import com.warehouse.warehouse_platform.tenant.counting.CountStatus;
import com.warehouse.warehouse_platform.tenant.dispatch.DispatchDocument;
import com.warehouse.warehouse_platform.tenant.dispatch.DispatchDocumentRepository;
import com.warehouse.warehouse_platform.tenant.dispatch.DispatchLine;
import com.warehouse.warehouse_platform.tenant.dispatch.DispatchLineRepository;
import com.warehouse.warehouse_platform.tenant.dispatch.DispatchStatus;
import com.warehouse.warehouse_platform.tenant.gis.model.GisBlock;
import com.warehouse.warehouse_platform.tenant.gis.model.GisHazardBuffer;
import com.warehouse.warehouse_platform.tenant.gis.model.GisZone;
import com.warehouse.warehouse_platform.tenant.gis.model.GisZoneCategoryRule;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisHazardBufferRepository;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisBlockRepository;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisZoneCategoryRuleRepository;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisZoneRepository;
import com.warehouse.warehouse_platform.tenant.hazardtype.HazardType;
import com.warehouse.warehouse_platform.tenant.hazardtype.HazardTypeRepository;
import com.warehouse.warehouse_platform.tenant.inventory.MovementType;
import com.warehouse.warehouse_platform.tenant.inventory.StockMovement;
import com.warehouse.warehouse_platform.tenant.inventory.StockMovementRepository;
import com.warehouse.warehouse_platform.tenant.product.Product;
import com.warehouse.warehouse_platform.tenant.product.ProductSupplier;
import com.warehouse.warehouse_platform.tenant.product.ProductSupplierId;
import com.warehouse.warehouse_platform.tenant.product.ProductSupplierRepository;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import com.warehouse.warehouse_platform.tenant.receipt.ReceiptDocument;
import com.warehouse.warehouse_platform.tenant.receipt.ReceiptDocumentRepository;
import com.warehouse.warehouse_platform.tenant.receipt.ReceiptLine;
import com.warehouse.warehouse_platform.tenant.receipt.ReceiptLineRepository;
import com.warehouse.warehouse_platform.tenant.receipt.ReceiptStatus;
import com.warehouse.warehouse_platform.tenant.supplier.Supplier;
import com.warehouse.warehouse_platform.tenant.supplier.SupplierRepository;
import com.warehouse.warehouse_platform.tenant.uom.UnitOfMeasure;
import com.warehouse.warehouse_platform.tenant.uom.UnitOfMeasureRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplate;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplateRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlock;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayout;
import com.warehouse.warehouse_platform.tenant.warehouse.layout.WarehouseLayoutRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.locationkind.WarehouseLocationKind;
import com.warehouse.warehouse_platform.tenant.warehouse.locationkind.WarehouseLocationKindRepository;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dev-only seeder. Active only when the {@code dev} Spring profile is enabled.
 *
 * <pre>
 * POST   /{tenantSlug}/dev/seed?reset=false&withGis=true
 *   Seeds a "Demo Warehouse" layout with 3 aisles, 4 bays per aisle, 3 shelves
 *   per bay (36 leaf storage locations), 20 demo products across 4 categories,
 *   and RECEIVE stock movements at every leaf location.
 *
 *   ?reset=true  — wipes demo data first, then re-seeds
 *   ?withGis=false — skips gis_blocks generation while keeping the rest of the
 *                    demo data available for non-GIS flows
 *
 * POST   /{tenantSlug}/dev/seed-showcase?withGis=true
 *   Wipes previous demo/showcase rows, re-seeds the demo warehouse, then adds
 *   receipts, dispatches, count sessions, zone rules, hazard buffers, audit
 *   rows, and extra stock movements tuned to populate dashboard widgets.
 *
 * DELETE /{tenantSlug}/dev/seed
 *   Wipes demo and showcase data. UOMs, hazard types, location kinds, and
 *   permissions are never touched.
 * </pre>
 *
 * <p>
 * <b>Layout structure:</b>
 * 
 * <pre>
 *   3 Aisles (A, B, C)
 *   └── 4 Bays per aisle (Bay 1-L, Bay 1-R, Bay 2-L, Bay 2-R)
 *       └── 3 Shelves each   → 36 leaf locations total
 * </pre>
 *
 * <p>
 * <b>GIS grid (when withGis=true):</b>
 * 
 * <pre>
 *   Leaves are placed in a 9-column × 4-row grid anchored at the floor-plan
 *   origin (lon≈0.000228°, lat≈0.000255°).
 *   Columns 0-2 = Aisle A, 3-5 = Aisle B, 6-8 = Aisle C.
 *   Rows 0-1 = Bay 1 (L/R), rows 2-3 = Bay 2 (L/R).
 *   Cell size: 0.000050° × 0.000040° — matches the coordinate space of real
 *   GIS floor-plan blocks so the viewer overlays align correctly.
 * </pre>
 */
@RestController
@Profile("dev")
@RequestMapping("/{tenantSlug}/dev")
@RequiredArgsConstructor
public class DevSeedController {

    // ── GIS grid constants ────────────────────────────────────────────────────
    // Anchored to match the floor-plan coordinate space used by the GIS viewer
    // (values near origin, in fractional degrees). Derived from a real Aisle block:
    // minLon=0.000228, maxLon=0.000409 → aisle width ≈ 0.000181°
    // minLat=0.000255, maxLat=0.000442 → aisle height ≈ 0.000188°
    // 3 shelves wide: 3·CELL_LON + 2·GAP_LON = 0.000180°
    // 4 bay rows tall: 4·CELL_LAT + 3·GAP_LAT = 0.000190°

    private static final double BASE_LON = 0.000228;
    private static final double BASE_LAT = 0.000255;
    private static final double CELL_LON = 0.000050;
    private static final double CELL_LAT = 0.000040;
    private static final double GAP_LON = 0.000015;
    private static final double GAP_LAT = 0.000010;

    // Inline initializer → excluded from @RequiredArgsConstructor
    private final GeometryFactory geomFactory = new GeometryFactory(new PrecisionModel(), 4326);

    // ── Injected dependencies ─────────────────────────────────────────────────

    private final TenantAccessPolicy tenantAccessPolicy;
    private final UnitOfMeasureRepository uomRepo;
    private final ProductCategoryRepository categoryRepo;
    private final HazardTypeRepository hazardTypeRepo;
    private final ProductRepository productRepo;
    private final BlockTemplateRepository blockTemplateRepo;
    private final WarehouseLayoutRepository layoutRepo;
    private final LayoutBlockRepository blockRepo;
    private final WarehouseLocationKindRepository locationKindRepo;
    private final GisBlockRepository gisBlockRepo;
    private final StockMovementRepository movementRepo;
    private final SupplierRepository supplierRepo;
    private final ProductSupplierRepository productSupplierRepo;
    private final ReceiptDocumentRepository receiptDocumentRepo;
    private final ReceiptLineRepository receiptLineRepo;
    private final DispatchDocumentRepository dispatchDocumentRepo;
    private final DispatchLineRepository dispatchLineRepo;
    private final CountSessionRepository countSessionRepo;
    private final CountLineRepository countLineRepo;
    private final GisZoneRepository gisZoneRepo;
    private final GisZoneCategoryRuleRepository gisZoneCategoryRuleRepo;
    private final GisHazardBufferRepository gisHazardBufferRepo;
    private final AuditLogRepository auditLogRepo;

    // ── Response DTO ──────────────────────────────────────────────────────────

    public record SeedResult(
            int uoms,
            int categories,
            int products,
            int blockTemplates,
            int layoutBlocks,
            int leafLocations,
            int gisBlocks,
            int stockMovements,
            boolean layoutActive,
            boolean skipped) {
    }

    public record ShowcaseSeedResult(
            int uoms,
            int categories,
            int products,
            int suppliers,
            int productSuppliers,
            int blockTemplates,
            int layoutBlocks,
            int leafLocations,
            int gisBlocks,
            int zones,
            int zoneRules,
            int hazardBuffers,
            int stockMovements,
            int receiptDocuments,
            int dispatchDocuments,
            int countSessions,
            int countLines,
            int auditLogs,
            boolean layoutActive) {
    }

    private record BaseSeedData(
            Map<String, UnitOfMeasure> uoms,
            Map<String, ProductCategory> categories,
            Map<String, HazardType> hazards,
            List<Product> products,
            Map<String, BlockTemplate> templates,
            LayoutSeedData layoutData,
            int gisCount,
            int stockMovementCount) {
    }

    private record ShowcaseExtras(
            int suppliers,
            int productSuppliers,
            int zones,
            int zoneRules,
            int hazardBuffers,
            int stockMovements,
            int receiptDocuments,
            int dispatchDocuments,
            int countSessions,
            int countLines,
            int auditLogs) {
    }

    private record SupplierSeedResult(int suppliers, int links) {
    }

    private record ZoneSeedResult(int zones, int rules) {
    }

    private record CountSeedResult(int sessions, int lines) {
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @PostMapping("/seed")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SeedResult> seed(
            @PathVariable String tenantSlug,
            @RequestParam(defaultValue = "false") boolean reset,
            @RequestParam(defaultValue = "true") boolean withGis,
            Authentication authentication) {

        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);

        boolean exists = layoutRepo.findByNameIgnoreCase("Demo Warehouse").isPresent();
        if (exists && !reset) {
            return ResponseEntity.ok(new SeedResult(0, 0, 0, 0, 0, 0, 0, 0, false, true));
        }
        if (reset) {
            doWipeShowcase();
        }
        if (exists) {
            doWipe();
        }

        BaseSeedData base = seedBaseData(withGis, false);

        return ResponseEntity.ok(new SeedResult(
                base.uoms().size(),
                base.categories().size(),
                base.products().size(),
                base.templates().size(),
                base.layoutData().allBlocks().size(),
                base.layoutData().leafBlocks().size(),
                base.gisCount(),
                base.stockMovementCount(),
                base.layoutData().layoutActive(),
                false));
    }

    @PostMapping("/seed-showcase")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ShowcaseSeedResult> seedShowcase(
            @PathVariable String tenantSlug,
            @RequestParam(defaultValue = "true") boolean withGis,
            Authentication authentication) {

        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);

        doWipeShowcase();
        doWipe();

        BaseSeedData base = seedBaseData(withGis, true);
        ShowcaseExtras extras = seedShowcaseExtras(base, withGis, tenantSlug);

        return ResponseEntity.ok(new ShowcaseSeedResult(
                base.uoms().size(),
                base.categories().size(),
                base.products().size(),
                extras.suppliers(),
                extras.productSuppliers(),
                base.templates().size(),
                base.layoutData().allBlocks().size(),
                base.layoutData().leafBlocks().size(),
                base.gisCount(),
                extras.zones(),
                extras.zoneRules(),
                extras.hazardBuffers(),
                base.stockMovementCount() + extras.stockMovements(),
                extras.receiptDocuments(),
                extras.dispatchDocuments(),
                extras.countSessions(),
                extras.countLines(),
                extras.auditLogs(),
                base.layoutData().layoutActive()));
    }

    @DeleteMapping("/seed")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> wipe(
            @PathVariable String tenantSlug,
            Authentication authentication) {

        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
        doWipeShowcase();
        doWipe();
        return ResponseEntity.noContent().build();
    }

    // ── Wipe ──────────────────────────────────────────────────────────────────

    private void doWipe() {
        layoutRepo.findByNameIgnoreCase("Demo Warehouse").ifPresent(layout -> {

            // 1. Gather all block IDs so we can target only their movements.
            List<UUID> blockIds = blockRepo
                    .findByLayoutIdOrderByParentIdAscPositionAsc(layout.getId())
                    .stream().map(LayoutBlock::getId).toList();

            // 2. Delete stock movements that reference demo-layout locations only.
            if (!blockIds.isEmpty()) {
                Specification<StockMovement> inDemo = (root, query, cb) -> root.get("locationId").in(blockIds);
                movementRepo.deleteAllInBatch(movementRepo.findAll(inDemo));
            }

            // 3. Delete root blocks — DB ON DELETE CASCADE removes children and
            // their gis_blocks (gis_blocks.layout_block_id has ON DELETE CASCADE).
            List<LayoutBlock> rootBlocks = blockRepo.findByLayoutIdAndParentIdIsNullOrderByPositionAsc(layout.getId());
            blockRepo.deleteAllInBatch(rootBlocks);

            // 4. Delete the layout shell (all blocks are already gone).
            layoutRepo.delete(layout);
        });

        // 5. Remove seeder block templates — only if no other layout still uses them.
        for (String name : List.of("Shelf", "Bay", "Aisle")) {
            blockTemplateRepo.findByNameIgnoreCase(name).ifPresent(t -> {
                if (blockRepo.countByBlockTemplateId(t.getId()) == 0) {
                    blockTemplateRepo.delete(t);
                }
            });
        }

        // 6. Remove DEV- products (their movements are already gone).
        productRepo.deleteAllInBatch(
                productRepo.findAll().stream()
                        .filter(p -> p.getSku().startsWith("DEV-"))
                        .toList());

        // 7. Remove seeder-specific categories — only if no product still references
        // them.
        for (String code : List.of("DEV_ELECTRONICS", "DEV_TOOLS")) {
            categoryRepo.findByCodeIgnoreCase(code).ifPresent(c -> {
                if (productRepo.countByCategory_Id(c.getId()) == 0) {
                    categoryRepo.delete(c);
                }
            });
        }
    }

    private void doWipeShowcase() {
        List<CountSession> showcaseSessions = countSessionRepo.findAll().stream()
                .filter(session -> session.getName() != null && session.getName().startsWith("SHOWCASE "))
                .toList();
        for (CountSession session : showcaseSessions) {
            countLineRepo.deleteBySessionId(session.getId());
        }
        if (!showcaseSessions.isEmpty()) {
            countSessionRepo.deleteAll(showcaseSessions);
        }

        List<ReceiptDocument> showcaseReceipts = receiptDocumentRepo.findAll().stream()
                .filter(receipt -> receipt.getReference() != null && receipt.getReference().startsWith("SHOWCASE-RCV-"))
                .toList();
        for (ReceiptDocument receipt : showcaseReceipts) {
            receiptLineRepo.deleteByReceiptId(receipt.getId());
        }
        if (!showcaseReceipts.isEmpty()) {
            receiptDocumentRepo.deleteAll(showcaseReceipts);
        }

        List<DispatchDocument> showcaseDispatches = dispatchDocumentRepo.findAll().stream()
                .filter(dispatch -> dispatch.getReference() != null && dispatch.getReference().startsWith("SHOWCASE-DSP-"))
                .toList();
        for (DispatchDocument dispatch : showcaseDispatches) {
            dispatchLineRepo.deleteByDispatchId(dispatch.getId());
        }
        if (!showcaseDispatches.isEmpty()) {
            dispatchDocumentRepo.deleteAll(showcaseDispatches);
        }

        List<Supplier> showcaseSuppliers = supplierRepo.findAll().stream()
                .filter(supplier -> supplier.getCode() != null && supplier.getCode().startsWith("SHOWCASE-"))
                .toList();
        if (!showcaseSuppliers.isEmpty()) {
            Set<UUID> supplierIds = showcaseSuppliers.stream().map(Supplier::getId).collect(java.util.stream.Collectors.toSet());
            List<ProductSupplier> links = productSupplierRepo.findAll().stream()
                    .filter(link -> supplierIds.contains(link.getSupplier().getId()))
                    .toList();
            if (!links.isEmpty()) {
                productSupplierRepo.deleteAll(links);
            }
            supplierRepo.deleteAll(showcaseSuppliers);
        }

        List<GisZone> showcaseZones = gisZoneRepo.findAllByOrderByCreatedAtAsc().stream()
                .filter(zone -> zone.getName() != null && zone.getName().startsWith("Showcase "))
                .toList();
        for (GisZone zone : showcaseZones) {
            gisZoneCategoryRuleRepo.deleteAll(gisZoneCategoryRuleRepo.findByZoneId(zone.getId()));
        }
        if (!showcaseZones.isEmpty()) {
            gisZoneRepo.deleteAll(showcaseZones);
        }

        List<GisHazardBuffer> showcaseBuffers = gisHazardBufferRepo.findAllByOrderByNameAscIdAsc().stream()
                .filter(buffer -> buffer.getName() != null && buffer.getName().startsWith("Showcase "))
                .toList();
        if (!showcaseBuffers.isEmpty()) {
            gisHazardBufferRepo.deleteAll(showcaseBuffers);
        }

        List<AuditLog> showcaseAuditLogs = auditLogRepo.findAll().stream()
                .filter(log -> (log.getActorEmail() != null && log.getActorEmail().endsWith("@showcase.dev"))
                        || (log.getRequestPath() != null && log.getRequestPath().startsWith("/dev/showcase/")))
                .toList();
        if (!showcaseAuditLogs.isEmpty()) {
            auditLogRepo.deleteAllInBatch(showcaseAuditLogs);
        }
    }

    private BaseSeedData seedBaseData(boolean withGis, boolean spreadMovementsAcrossHistory) {
        Map<String, UnitOfMeasure> uoms = seedUoms();
        Map<String, ProductCategory> categories = seedCategories();
        Map<String, HazardType> hazards = loadHazardTypes();
        List<Product> products = seedProducts(uoms, categories, hazards);
        Map<String, BlockTemplate> templates = seedBlockTemplates();

        WarehouseLocationKind storageKind = locationKindRepo.findByNameIgnoreCase("Storage")
                .orElseGet(() -> locationKindRepo.findFirstByOrderBySortOrderAscIdAsc()
                        .orElseThrow(() -> new IllegalStateException(
                                "No warehouse_location_kinds rows found — is the DB migrated?")));

        LayoutSeedData layoutData = seedLayout(templates, storageKind);
        int gisCount = withGis ? seedGisBlocks(layoutData) : 0;
        int stockMovementCount = spreadMovementsAcrossHistory
                ? seedShowcaseBaseStock(layoutData.leafBlocks(), products)
                : seedStock(layoutData.leafBlocks(), products);

        return new BaseSeedData(
                uoms,
                categories,
                hazards,
                products,
                templates,
                layoutData,
                gisCount,
                stockMovementCount);
    }

    private ShowcaseExtras seedShowcaseExtras(BaseSeedData base, boolean withGis, String tenantSlug) {
        Instant now = Instant.now();
        Map<String, Product> productsBySku = base.products().stream()
                .collect(java.util.stream.Collectors.toMap(Product::getSku, product -> product, (left, right) -> left, LinkedHashMap::new));
        applyShowcaseProductFlags(productsBySku, now);
        SupplierSeedResult supplierSeed = seedShowcaseSuppliers(base.products());
        int receiptCount = seedShowcaseReceipts(base.layoutData().leafBlocks(), productsBySku, now);
        int dispatchCount = seedShowcaseDispatches(base.layoutData().leafBlocks(), productsBySku, now);
        int stockMovementCount = seedShowcaseMovements(base.layoutData().leafBlocks(), productsBySku, now);
        int countSessionCount;
        int countLineCount;
        int zoneCount = 0;
        int zoneRuleCount = 0;
        int hazardBufferCount = 0;
        if (withGis) {
            ZoneSeedResult zoneSeed = seedShowcaseZones(base.categories());
            zoneCount = zoneSeed.zones();
            zoneRuleCount = zoneSeed.rules();
            hazardBufferCount = seedShowcaseHazardBuffers(base.hazards(), base.layoutData());
        }
        CountSeedResult countSeed = seedShowcaseCounts(base.layoutData().leafBlocks(), productsBySku, now);
        countSessionCount = countSeed.sessions();
        countLineCount = countSeed.lines();
        int auditCount = seedShowcaseAuditLogs(tenantSlug, now);

        return new ShowcaseExtras(
                supplierSeed.suppliers(),
                supplierSeed.links(),
                zoneCount,
                zoneRuleCount,
                hazardBufferCount,
                stockMovementCount,
                receiptCount,
                dispatchCount,
                countSessionCount,
                countLineCount,
                auditCount);
    }

    // ── Seed helpers ──────────────────────────────────────────────────────────

    private Map<String, UnitOfMeasure> seedUoms() {
        record Spec(String code, String name, String symbol) {
        }
        List<Spec> specs = List.of(
                new Spec("UNIT", "Unit", "ea"),
                new Spec("KG", "Kilogram", "kg"),
                new Spec("PALLET", "Pallet", "plt"),
                new Spec("BOX", "Box", "box"));

        Map<String, UnitOfMeasure> result = new LinkedHashMap<>();
        for (Spec s : specs) {
            UnitOfMeasure uom = uomRepo.findByCodeIgnoreCase(s.code())
                    .orElseGet(() -> uomRepo.save(UnitOfMeasure.builder()
                            .code(s.code())
                            .name(s.name())
                            .symbol(s.symbol())
                            .build()));
            result.put(s.code(), uom);
        }
        return result;
    }

    private Map<String, ProductCategory> seedCategories() {
        // STANDARD and PERISHABLE are seeded by migration V36; reuse if present.
        record Spec(String code, String name) {
        }
        List<Spec> specs = List.of(
                new Spec("STANDARD", "Standard"),
                new Spec("PERISHABLE", "Perishable"),
                new Spec("DEV_ELECTRONICS", "Electronics"),
                new Spec("DEV_TOOLS", "Tools & Equipment"));

        Map<String, ProductCategory> result = new LinkedHashMap<>();
        for (Spec s : specs) {
            ProductCategory cat = categoryRepo.findByCodeIgnoreCase(s.code())
                    .orElseGet(() -> categoryRepo.save(ProductCategory.builder()
                            .code(s.code())
                            .name(s.name())
                            .displayName(s.name())
                            .build()));
            result.put(s.code(), cat);
        }
        return result;
    }

    private Map<String, HazardType> loadHazardTypes() {
        Map<String, HazardType> result = new LinkedHashMap<>();
        for (String code : List.of("NONE", "FLAMMABLE")) {
            hazardTypeRepo.findByCodeIgnoreCase(code).ifPresent(h -> result.put(code, h));
        }
        return result;
    }

    private List<Product> seedProducts(
            Map<String, UnitOfMeasure> uoms,
            Map<String, ProductCategory> cats,
            Map<String, HazardType> hazards) {

        UnitOfMeasure unit = uoms.get("UNIT");
        UnitOfMeasure kg = uoms.get("KG");
        UnitOfMeasure box = uoms.get("BOX");
        ProductCategory elec = cats.get("DEV_ELECTRONICS");
        ProductCategory tool = cats.get("DEV_TOOLS");
        ProductCategory peri = cats.get("PERISHABLE");
        ProductCategory std = cats.get("STANDARD");
        HazardType none = hazards.get("NONE");
        HazardType flam = hazards.getOrDefault("FLAMMABLE", none);

        record Spec(String sku, String name,
                UnitOfMeasure uom, ProductCategory cat, HazardType hazard) {
        }

        List<Spec> specs = List.of(
                // Electronics — 5 products
                new Spec("DEV-ELX-001", "Laptop Computer", unit, elec, none),
                new Spec("DEV-ELX-002", "Smartphone", unit, elec, none),
                new Spec("DEV-ELX-003", "Tablet Device", unit, elec, none),
                new Spec("DEV-ELX-004", "USB Cable (10-pack)", box, elec, none),
                new Spec("DEV-ELX-005", "Power Adapter", unit, elec, none),
                // Tools — 4 standard + 1 flammable
                new Spec("DEV-TLS-001", "Adjustable Wrench", unit, tool, none),
                new Spec("DEV-TLS-002", "Power Drill", unit, tool, none),
                new Spec("DEV-TLS-003", "Claw Hammer", unit, tool, none),
                new Spec("DEV-TLS-004", "Screwdriver Set", box, tool, none),
                new Spec("DEV-TLS-005", "Welding Gas Canister", unit, tool, flam),
                // Perishable — 5 products
                new Spec("DEV-PRD-001", "Fresh Milk", kg, peri, none),
                new Spec("DEV-PRD-002", "Aged Cheddar Cheese", kg, peri, none),
                new Spec("DEV-PRD-003", "Unsalted Butter", kg, peri, none),
                new Spec("DEV-PRD-004", "Greek Yogurt", kg, peri, none),
                new Spec("DEV-PRD-005", "Heavy Cream", kg, peri, none),
                // Standard — 4 general + 1 flammable
                new Spec("DEV-STD-001", "Ball-Point Pens", box, std, none),
                new Spec("DEV-STD-002", "A4 Notebooks", box, std, none),
                new Spec("DEV-STD-003", "Packing Tape", box, std, none),
                new Spec("DEV-STD-004", "Safety Scissors", box, std, none),
                new Spec("DEV-STD-005", "Spray Lubricant", unit, std, flam));

        List<Product> result = new ArrayList<>();
        for (Spec s : specs) {
            Product p = productRepo.findBySkuIgnoreCase(s.sku())
                    .orElseGet(() -> productRepo.save(Product.builder()
                            .sku(s.sku())
                            .name(s.name())
                            .baseUom(s.uom())
                            .category(s.cat())
                            .hazardType(s.hazard())
                            .build()));
            result.add(p);
        }
        return result;
    }

    private Map<String, BlockTemplate> seedBlockTemplates() {
        record Spec(String name,
                BlockTemplate.IdentifierFormat fmt,
                BlockTemplate.SideConfig side) {
        }

        List<Spec> specs = List.of(
                new Spec("Aisle", BlockTemplate.IdentifierFormat.ALPHA, BlockTemplate.SideConfig.NONE),
                new Spec("Bay", BlockTemplate.IdentifierFormat.NUMERIC, BlockTemplate.SideConfig.LR),
                new Spec("Shelf", BlockTemplate.IdentifierFormat.NUMERIC, BlockTemplate.SideConfig.NONE));

        Map<String, BlockTemplate> result = new LinkedHashMap<>();
        for (Spec s : specs) {
            BlockTemplate tpl = blockTemplateRepo.findByNameIgnoreCase(s.name())
                    .orElseGet(() -> blockTemplateRepo.save(BlockTemplate.builder()
                            .name(s.name())
                            .identifierFormat(s.fmt())
                            .sideConfig(s.side())
                            .required(true)
                            .build()));
            result.put(s.name(), tpl);
        }
        return result;
    }

    // ── Layout seeding ────────────────────────────────────────────────────────

    /**
     * Carries the seeded layout data between helpers.
     *
     * @param boundsByBlockId maps each layout_block UUID to its bounding box
     *                        {@code [minLon, maxLon, minLat, maxLat]} for GIS
     *                        seeding.
     */
    private record LayoutSeedData(
            WarehouseLayout layout,
            boolean layoutActive,
            List<LayoutBlock> allBlocks,
            List<LayoutBlock> leafBlocks,
            Map<UUID, double[]> boundsByBlockId) {
    }

    private LayoutSeedData seedLayout(
            Map<String, BlockTemplate> templates,
            WarehouseLocationKind storageKind) {

        // Only set isActive=true when no other layout is already active.
        boolean makeActive = !layoutRepo.existsByIsActiveTrue();

        WarehouseLayout layout = layoutRepo.save(WarehouseLayout.builder()
                .name("Demo Warehouse")
                .description("Auto-seeded demo layout. Safe to delete via DELETE /{tenantSlug}/dev/seed.")
                .isActive(makeActive)
                .build());

        BlockTemplate aisleT = templates.get("Aisle");
        BlockTemplate bayT = templates.get("Bay");
        BlockTemplate shelfT = templates.get("Shelf");

        String[] AISLES = { "A", "B", "C" };
        int[] BAY_NUMS = { 1, 2 };
        String[] SIDES = { "L", "R" };

        List<LayoutBlock> allBlocks = new ArrayList<>();
        List<LayoutBlock> leafBlocks = new ArrayList<>();
        Map<UUID, double[]> bounds = new LinkedHashMap<>();

        for (int ai = 0; ai < AISLES.length; ai++) {
            String aisleCode = AISLES[ai];

            // Aisle bounding box: 3 shelf-columns wide, 4 bay-rows tall.
            double aisleMinLon = BASE_LON + (ai * 3) * (CELL_LON + GAP_LON);
            double aisleMaxLon = BASE_LON + (ai * 3 + 2) * (CELL_LON + GAP_LON) + CELL_LON;
            double aisleMinLat = BASE_LAT;
            double aisleMaxLat = BASE_LAT + 3 * (CELL_LAT + GAP_LAT) + CELL_LAT;

            LayoutBlock aisleBlock = blockRepo.save(LayoutBlock.builder()
                    .layoutId(layout.getId())
                    .blockTemplateId(aisleT.getId())
                    .parentId(null)
                    .position(ai)
                    .locationKind(storageKind)
                    .fullCode(aisleCode)
                    .build());
            allBlocks.add(aisleBlock);
            bounds.put(aisleBlock.getId(),
                    new double[] { aisleMinLon, aisleMaxLon, aisleMinLat, aisleMaxLat });

            for (int bi = 0; bi < BAY_NUMS.length; bi++) {
                for (int si = 0; si < SIDES.length; si++) {
                    String side = SIDES[si];
                    int row = bi * 2 + si; // 0-3 within the aisle
                    int bayPosition = row; // 0-based within parent

                    // Bay bounding box: full aisle width, one shelf-row tall.
                    double bayMinLon = aisleMinLon;
                    double bayMaxLon = aisleMaxLon;
                    double bayMinLat = BASE_LAT + row * (CELL_LAT + GAP_LAT);
                    double bayMaxLat = bayMinLat + CELL_LAT;

                    String bayCode = String.format("%s-%02d-%s", aisleCode, BAY_NUMS[bi], side);

                    LayoutBlock bayBlock = blockRepo.save(LayoutBlock.builder()
                            .layoutId(layout.getId())
                            .blockTemplateId(bayT.getId())
                            .parentId(aisleBlock.getId())
                            .position(bayPosition)
                            .side(side)
                            .locationKind(storageKind)
                            .fullCode(bayCode)
                            .build());
                    allBlocks.add(bayBlock);
                    bounds.put(bayBlock.getId(),
                            new double[] { bayMinLon, bayMaxLon, bayMinLat, bayMaxLat });

                    // 3 shelves — spread across 3 consecutive columns.
                    for (int sh = 0; sh < 3; sh++) {
                        int col = ai * 3 + sh;
                        double minLon = BASE_LON + col * (CELL_LON + GAP_LON);
                        double maxLon = minLon + CELL_LON;

                        String shelfCode = String.format("%s-%02d", bayCode, sh + 1);

                        LayoutBlock shelfBlock = blockRepo.save(LayoutBlock.builder()
                                .layoutId(layout.getId())
                                .blockTemplateId(shelfT.getId())
                                .parentId(bayBlock.getId())
                                .position(sh)
                                .locationKind(storageKind)
                                .fullCode(shelfCode)
                                .scanCode(shelfCode)
                                .build());
                        allBlocks.add(shelfBlock);
                        leafBlocks.add(shelfBlock);
                        bounds.put(shelfBlock.getId(),
                                new double[] { minLon, maxLon, bayMinLat, bayMaxLat });
                    }
                }
            }
        }

        return new LayoutSeedData(layout, makeActive, allBlocks, leafBlocks, bounds);
    }

    // ── GIS block seeding ─────────────────────────────────────────────────────

    /**
     * Creates one {@code gis_block} per layout block (aisles, bays, and shelves).
     * Every row gets a {@code centroid_geom} so the GIS viewer can render all
     * layers consistently.
     *
     * <p>
     * Two options are exposed at the API level:
     * <ul>
     * <li>{@code withGis=true} — calls this method and seeds GIS blocks.</li>
     * <li>{@code withGis=false} — skips this method; useful when you only want
     * to test layout / inventory features without the GIS viewer.</li>
     * </ul>
     */
    private int seedGisBlocks(LayoutSeedData data) {
        // Depth inference: allBlocks is ordered parents-before-children
        // (aisles added first, then their bays, then shelves).
        Map<UUID, Integer> depthByBlockId = new LinkedHashMap<>();
        for (LayoutBlock b : data.allBlocks()) {
            if (b.getParentId() == null) {
                depthByBlockId.put(b.getId(), 0);
            } else {
                int parentDepth = depthByBlockId.getOrDefault(b.getParentId(), 0);
                depthByBlockId.put(b.getId(), parentDepth + 1);
            }
        }

        List<GisBlock> gisBlocks = new ArrayList<>();

        for (LayoutBlock block : data.allBlocks()) {
            double[] bb = data.boundsByBlockId().get(block.getId());
            if (bb == null)
                continue;

            double minLon = bb[0], maxLon = bb[1], minLat = bb[2], maxLat = bb[3];

            // SW → SE → NE → NW → SW (closed ring, EPSG:4326)
            Polygon polygon = geomFactory.createPolygon(new Coordinate[] {
                    new Coordinate(minLon, minLat),
                    new Coordinate(maxLon, minLat),
                    new Coordinate(maxLon, maxLat),
                    new Coordinate(minLon, maxLat),
                    new Coordinate(minLon, minLat)
            });
            Point centroid = polygon.getCentroid();

            int depth = depthByBlockId.getOrDefault(block.getId(), 0);
            String templateName = switch (depth) {
                case 0 -> "Aisle";
                case 1 -> "Bay";
                default -> "Shelf";
            };
            String label = block.getFullCode() != null
                    ? block.getFullCode()
                    : block.getId().toString();

            gisBlocks.add(GisBlock.builder()
                    .layoutBlockId(block.getId())
                    .templateName(templateName)
                    .label(label)
                    .positionPath(label)
                    .depth(depth)
                    .geometry(polygon)
                    .centroidGeom(centroid)
                    .build());
        }

        gisBlockRepo.saveAll(gisBlocks);
        return gisBlocks.size();
    }

    private void applyShowcaseProductFlags(Map<String, Product> productsBySku, Instant now) {
        List<Product> updatedProducts = new ArrayList<>();
        for (String sku : List.of("DEV-PRD-001", "DEV-PRD-002", "DEV-PRD-003", "DEV-PRD-004", "DEV-PRD-005")) {
            Product product = productsBySku.get(sku);
            if (product != null) {
                product.setTrackLot(true);
                product.setTrackExpiry(true);
                updatedProducts.add(product);
            }
        }

        Product inactive = productsBySku.get("DEV-STD-004");
        if (inactive != null) {
            inactive.setActive(false);
            inactive.setDeactivatedAt(now.minus(Duration.ofDays(5)));
            updatedProducts.add(inactive);
        }

        if (!updatedProducts.isEmpty()) {
            productRepo.saveAll(updatedProducts.stream().distinct().toList());
        }
    }

    private SupplierSeedResult seedShowcaseSuppliers(List<Product> products) {
        record SupplierSpec(String code, String name, String email) {
        }

        List<SupplierSpec> specs = List.of(
                new SupplierSpec("SHOWCASE-SUP-001", "Showcase Industrial Supply", "ops-a@showcase.dev"),
                new SupplierSpec("SHOWCASE-SUP-002", "Showcase Cold Chain Partners", "ops-b@showcase.dev"),
                new SupplierSpec("SHOWCASE-SUP-003", "Showcase Retail Goods", "ops-c@showcase.dev"));

        List<Supplier> suppliers = new ArrayList<>();
        for (SupplierSpec spec : specs) {
            Supplier supplier = supplierRepo.findByCodeIgnoreCase(spec.code())
                    .orElseGet(() -> supplierRepo.save(Supplier.builder()
                            .code(spec.code())
                            .name(spec.name())
                            .contactName(spec.name() + " Ops")
                            .contactEmail(spec.email())
                            .contactPhone("+1-555-0100")
                            .notes("Dashboard showcase supplier")
                            .build()));
            suppliers.add(supplier);
        }

        List<Product> sortedProducts = products.stream()
                .sorted(Comparator.comparing(Product::getSku))
                .toList();
        List<ProductSupplier> links = new ArrayList<>();
        int limit = Math.min(12, sortedProducts.size());
        for (int index = 0; index < limit; index++) {
            Product product = sortedProducts.get(index);
            Supplier supplier = suppliers.get(index % suppliers.size());
            links.add(ProductSupplier.builder()
                    .id(new ProductSupplierId(product.getId(), supplier.getId()))
                    .product(product)
                    .supplier(supplier)
                    .primary(true)
                    .build());
        }

        productSupplierRepo.saveAll(links);
        return new SupplierSeedResult(suppliers.size(), links.size());
    }

    private int seedShowcaseReceipts(List<LayoutBlock> leafBlocks, Map<String, Product> productsBySku, Instant now) {
        List<Supplier> suppliers = supplierRepo.findAll().stream()
                .filter(supplier -> supplier.getCode() != null && supplier.getCode().startsWith("SHOWCASE-"))
                .sorted(Comparator.comparing(Supplier::getCode))
                .toList();
        if (suppliers.isEmpty() || leafBlocks.isEmpty()) {
            return 0;
        }

        List<ReceiptDocument> documents = List.of(
                buildReceiptDocument("SHOWCASE-RCV-001", suppliers.get(0), ReceiptStatus.POSTED, now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(3))),
                buildReceiptDocument("SHOWCASE-RCV-002", suppliers.get(1), ReceiptStatus.POSTED, now.minus(Duration.ofHours(4)), now.minus(Duration.ofHours(5))),
                buildReceiptDocument("SHOWCASE-RCV-003", suppliers.get(2), ReceiptStatus.POSTED, now.minus(Duration.ofHours(7)), now.minus(Duration.ofHours(8))),
                buildReceiptDocument("SHOWCASE-RCV-004", suppliers.get(0), ReceiptStatus.POSTED, now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(1)).minus(Duration.ofHours(1))),
                buildReceiptDocument("SHOWCASE-RCV-005", suppliers.get(1), ReceiptStatus.DRAFT, null, now.minus(Duration.ofHours(6))),
                buildReceiptDocument("SHOWCASE-RCV-006", suppliers.get(2), ReceiptStatus.DRAFT, null, now.minus(Duration.ofHours(1))));
        receiptDocumentRepo.saveAll(documents);

        List<ReceiptLine> lines = new ArrayList<>();
        Product milk = productsBySku.get("DEV-PRD-001");
        Product drill = productsBySku.get("DEV-TLS-002");
        Product pens = productsBySku.get("DEV-STD-001");
        Product cable = productsBySku.get("DEV-ELX-004");
        Product yogurt = productsBySku.get("DEV-PRD-004");
        List<Product> lineProducts = List.of(milk, drill, pens, cable, yogurt).stream().filter(java.util.Objects::nonNull).toList();
        for (int index = 0; index < Math.min(documents.size(), lineProducts.size()); index++) {
            ReceiptDocument document = documents.get(index);
            Product product = lineProducts.get(index);
            LayoutBlock location = leafBlocks.get(index % leafBlocks.size());
            lines.add(ReceiptLine.builder()
                    .receiptId(document.getId())
                    .productId(product.getId())
                    .destinationLocationId(location.getId())
                    .qty(BigDecimal.valueOf(12 + index * 4L))
                    .lotNumber(product.getTrackLot() ? "SHOW-LOT-" + (index + 1) : null)
                    .expiryDate(product.getTrackExpiry() ? LocalDate.now().plusDays(4 + index) : null)
                    .notes("Showcase inbound line")
                    .position(index)
                    .build());
        }
        receiptLineRepo.saveAll(lines);
        return documents.size();
    }

    private ReceiptDocument buildReceiptDocument(
            String reference,
            Supplier supplier,
            ReceiptStatus status,
            Instant postedAt,
            Instant createdAt) {
        return ReceiptDocument.builder()
                .supplier(supplier)
                .reference(reference)
                .notes("Dashboard showcase receipt")
                .status(status)
                .createdBy("dev-showcase")
                .createdAt(createdAt)
                .postedAt(postedAt)
                .postedBy(postedAt != null ? "dev-showcase" : null)
                .build();
    }

    private int seedShowcaseDispatches(List<LayoutBlock> leafBlocks, Map<String, Product> productsBySku, Instant now) {
        if (leafBlocks.isEmpty()) {
            return 0;
        }

        List<DispatchDocument> documents = List.of(
                buildDispatchDocument("SHOWCASE-DSP-001", "North Hub", DispatchStatus.POSTED, now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(4))),
                buildDispatchDocument("SHOWCASE-DSP-002", "West Hub", DispatchStatus.POSTED, now.minus(Duration.ofHours(6)), now.minus(Duration.ofHours(7))),
                buildDispatchDocument("SHOWCASE-DSP-003", "Retail Outlet", DispatchStatus.POSTED, now.minus(Duration.ofHours(9)), now.minus(Duration.ofHours(10))),
                buildDispatchDocument("SHOWCASE-DSP-004", "Airport Depot", DispatchStatus.POSTED, now.minus(Duration.ofDays(1)).minus(Duration.ofHours(2)), now.minus(Duration.ofDays(1)).minus(Duration.ofHours(3))),
                buildDispatchDocument("SHOWCASE-DSP-005", "Overflow Yard", DispatchStatus.DRAFT, null, now.minus(Duration.ofHours(5))),
                buildDispatchDocument("SHOWCASE-DSP-006", "Maintenance Van", DispatchStatus.DRAFT, null, now.minus(Duration.ofHours(2))));
        dispatchDocumentRepo.saveAll(documents);

        List<DispatchLine> lines = new ArrayList<>();
        List<Product> lineProducts = List.of(
                productsBySku.get("DEV-ELX-001"),
                productsBySku.get("DEV-TLS-001"),
                productsBySku.get("DEV-PRD-002"),
                productsBySku.get("DEV-STD-003"),
                productsBySku.get("DEV-TLS-005")).stream().filter(java.util.Objects::nonNull).toList();
        for (int index = 0; index < Math.min(documents.size(), lineProducts.size()); index++) {
            DispatchDocument document = documents.get(index);
            Product product = lineProducts.get(index);
            LayoutBlock location = leafBlocks.get((index + 7) % leafBlocks.size());
            lines.add(DispatchLine.builder()
                    .dispatchId(document.getId())
                    .productId(product.getId())
                    .sourceLocationId(location.getId())
                    .qty(BigDecimal.valueOf(4 + index * 2L))
                    .lotNumber(product.getTrackLot() ? "SHOW-LOT-" + (index + 11) : null)
                    .notes("Showcase outbound line")
                    .position(index)
                    .build());
        }
        dispatchLineRepo.saveAll(lines);
        return documents.size();
    }

    private DispatchDocument buildDispatchDocument(
            String reference,
            String destination,
            DispatchStatus status,
            Instant postedAt,
            Instant createdAt) {
        return DispatchDocument.builder()
                .destination(destination)
                .reference(reference)
                .notes("Dashboard showcase dispatch")
                .status(status)
                .createdBy("dev-showcase")
                .createdAt(createdAt)
                .postedAt(postedAt)
                .postedBy(postedAt != null ? "dev-showcase" : null)
                .build();
    }

    private ZoneSeedResult seedShowcaseZones(Map<String, ProductCategory> categories) {
        List<GisZone> zones = List.of(
                GisZone.builder()
                        .name("Showcase Electronics Zone")
                        .description("Electronics-heavy picking and put-away zone.")
                        .geometry(gridPolygon(0, 2, 0, 1))
                        .violationAction("WARN")
                        .source("MANUAL")
                        .displayColor("#2563EB")
                        .build(),
                GisZone.builder()
                        .name("Showcase Tools Zone")
                        .description("Tools and equipment lane.")
                        .geometry(gridPolygon(3, 5, 0, 1))
                        .violationAction("WARN")
                        .source("MANUAL")
                        .displayColor("#F97316")
                        .build(),
                GisZone.builder()
                        .name("Showcase Perishables Zone")
                        .description("Cold-chain and expiry-sensitive storage.")
                        .geometry(gridPolygon(6, 8, 0, 1))
                        .violationAction("WARN")
                        .source("MANUAL")
                        .displayColor("#10B981")
                        .build(),
                GisZone.builder()
                        .name("Showcase Reserve Zone")
                        .description("Empty reserve footprint for dashboard empty-zone warnings.")
                        .geometry(boundsPolygon(
                                BASE_LON + 9 * (CELL_LON + GAP_LON),
                                BASE_LON + 10 * (CELL_LON + GAP_LON) + CELL_LON,
                                BASE_LAT,
                                BASE_LAT + CELL_LAT))
                        .violationAction("WARN")
                        .source("MANUAL")
                        .displayColor("#A855F7")
                        .build());
        gisZoneRepo.saveAll(zones);

        List<GisZoneCategoryRule> rules = new ArrayList<>();
        ProductCategory electronics = categories.get("DEV_ELECTRONICS");
        ProductCategory tools = categories.get("DEV_TOOLS");
        ProductCategory perishables = categories.get("PERISHABLE");
        if (electronics != null) {
            rules.add(GisZoneCategoryRule.builder()
                    .zone(zones.get(0))
                    .categoryId(electronics.getId())
                    .ruleType("ALLOWED")
                    .build());
        }
        if (tools != null) {
            rules.add(GisZoneCategoryRule.builder()
                    .zone(zones.get(1))
                    .categoryId(tools.getId())
                    .ruleType("ALLOWED")
                    .build());
        }
        if (perishables != null) {
            rules.add(GisZoneCategoryRule.builder()
                    .zone(zones.get(2))
                    .categoryId(perishables.getId())
                    .ruleType("ALLOWED")
                    .build());
        }
        gisZoneCategoryRuleRepo.saveAll(rules);
        return new ZoneSeedResult(zones.size(), rules.size());
    }

    private int seedShowcaseHazardBuffers(Map<String, HazardType> hazards, LayoutSeedData layoutData) {
        HazardType flammable = hazards.get("FLAMMABLE");
        if (flammable == null || layoutData.leafBlocks().isEmpty()) {
            return 0;
        }

        LayoutBlock target = layoutData.leafBlocks().getFirst();
        double[] bounds = layoutData.boundsByBlockId().get(target.getId());
        if (bounds == null) {
            return 0;
        }

        GisHazardBuffer buffer = GisHazardBuffer.builder()
                .name("Showcase Flammable Buffer")
                .source("ARCGIS_IMPORT")
                .geometry(boundsPolygon(bounds[0] - 0.000004, bounds[1] + 0.000004, bounds[2] - 0.000004, bounds[3] + 0.000004))
                .notes("Dashboard showcase hazard overlap")
                .sourceFilename("showcase.geojson")
                .restrictedHazardTypes(new ArrayList<>(List.of(flammable)))
                .build();
        gisHazardBufferRepo.save(buffer);
        return 1;
    }

    private CountSeedResult seedShowcaseCounts(List<LayoutBlock> leafBlocks, Map<String, Product> productsBySku, Instant now) {
        if (leafBlocks.size() < 6) {
            return new CountSeedResult(0, 0);
        }

        CountSession openStale = CountSession.builder()
                .name("SHOWCASE Cycle Count - Stale")
                .status(CountStatus.OPEN)
                .createdBy("dev-showcase")
                .createdAt(now.minus(Duration.ofDays(4)))
                .locationIds(new HashSet<>(Set.of(leafBlocks.get(0).getId(), leafBlocks.get(1).getId())))
                .build();
        CountSession openFresh = CountSession.builder()
                .name("SHOWCASE Cycle Count - Active")
                .status(CountStatus.OPEN)
                .createdBy("dev-showcase")
                .createdAt(now.minus(Duration.ofHours(10)))
                .locationIds(new HashSet<>(Set.of(leafBlocks.get(2).getId(), leafBlocks.get(3).getId())))
                .build();
        CountSession postedA = CountSession.builder()
                .name("SHOWCASE Cycle Count - Posted A")
                .status(CountStatus.POSTED)
                .createdBy("dev-showcase")
                .createdAt(now.minus(Duration.ofDays(6)))
                .postedAt(now.minus(Duration.ofDays(5)))
                .postedBy("dev-showcase")
                .locationIds(new HashSet<>(Set.of(leafBlocks.get(0).getId(), leafBlocks.get(1).getId(), leafBlocks.get(2).getId())))
                .build();
        CountSession postedB = CountSession.builder()
                .name("SHOWCASE Cycle Count - Posted B")
                .status(CountStatus.POSTED)
                .createdBy("dev-showcase")
                .createdAt(now.minus(Duration.ofDays(3)))
                .postedAt(now.minus(Duration.ofDays(2)))
                .postedBy("dev-showcase")
                .locationIds(new HashSet<>(Set.of(leafBlocks.get(3).getId(), leafBlocks.get(4).getId(), leafBlocks.get(5).getId())))
                .build();
        CountSession voided = CountSession.builder()
                .name("SHOWCASE Cycle Count - Void")
                .status(CountStatus.VOID)
                .createdBy("dev-showcase")
                .createdAt(now.minus(Duration.ofDays(2)))
                .voidedAt(now.minus(Duration.ofDays(1)))
                .voidedBy("dev-showcase")
                .locationIds(new HashSet<>(Set.of(leafBlocks.get(5).getId())))
                .build();

        countSessionRepo.saveAll(List.of(openStale, openFresh, postedA, postedB, voided));

        List<Product> countProducts = List.of(
                productsBySku.get("DEV-ELX-001"),
                productsBySku.get("DEV-TLS-001"),
                productsBySku.get("DEV-PRD-001"),
                productsBySku.get("DEV-STD-001"),
                productsBySku.get("DEV-TLS-005"),
                productsBySku.get("DEV-PRD-002")).stream().filter(java.util.Objects::nonNull).toList();

        List<CountLine> lines = new ArrayList<>();
        lines.add(buildCountLine(postedA, leafBlocks.get(0), countProducts.get(0), "SHOW-LOT-1", 120, 120));
        lines.add(buildCountLine(postedA, leafBlocks.get(1), countProducts.get(1), null, 75, 72));
        lines.add(buildCountLine(postedA, leafBlocks.get(2), countProducts.get(2), "SHOW-LOT-2", 58, 58));
        lines.add(buildCountLine(postedA, leafBlocks.get(0), countProducts.get(3), null, 96, 96));
        lines.add(buildCountLine(postedB, leafBlocks.get(3), countProducts.get(4), null, 30, 33));
        lines.add(buildCountLine(postedB, leafBlocks.get(4), countProducts.get(5), "SHOW-LOT-3", 42, 42));
        lines.add(buildCountLine(postedB, leafBlocks.get(5), countProducts.get(0), null, 64, 60));
        lines.add(buildCountLine(postedB, leafBlocks.get(4), countProducts.get(1), null, 27, 27));

        countLineRepo.saveAll(lines);
        return new CountSeedResult(5, lines.size());
    }

    private CountLine buildCountLine(
            CountSession session,
            LayoutBlock location,
            Product product,
            String lotNumber,
            long expectedQty,
            long countedQty) {
        return CountLine.builder()
                .sessionId(session.getId())
                .locationId(location.getId())
                .productId(product.getId())
                .lotNumber(lotNumber)
                .expectedQty(BigDecimal.valueOf(expectedQty))
                .countedQty(BigDecimal.valueOf(countedQty))
                .build();
    }

    private int seedShowcaseAuditLogs(String tenantSlug, Instant now) {
        List<AuditLog> logs = List.of(
                buildAuditLog(now.minus(Duration.ofHours(2)), "ops@showcase.dev", "POST", "RECEIPT_DOCUMENT", "SHOWCASE-RCV-001", tenantSlug, "/dev/showcase/receipts", "POST", "{\"reference\":\"SHOWCASE-RCV-001\"}"),
                buildAuditLog(now.minus(Duration.ofHours(4)), "warehouse@showcase.dev", "POST", "DISPATCH_DOCUMENT", "SHOWCASE-DSP-001", tenantSlug, "/dev/showcase/dispatches", "POST", "{\"reference\":\"SHOWCASE-DSP-001\"}"),
                buildAuditLog(now.minus(Duration.ofHours(6)), "safety@showcase.dev", "CREATE", "GIS_ZONE", "showcase-electronics", tenantSlug, "/dev/showcase/zones", "POST", "{\"name\":\"Showcase Electronics Zone\"}"),
                buildAuditLog(now.minus(Duration.ofHours(10)), "ops@showcase.dev", "UPDATE", "COUNT_SESSION", "SHOWCASE Cycle Count - Active", tenantSlug, "/dev/showcase/counting", "PATCH", "{\"status\":\"OPEN\"}"),
                buildAuditLog(now.minus(Duration.ofDays(1)), "planner@showcase.dev", "POST", "COUNT_SESSION", "SHOWCASE Cycle Count - Posted B", tenantSlug, "/dev/showcase/counting", "POST", "{\"status\":\"POSTED\"}"),
                buildAuditLog(now.minus(Duration.ofDays(1)).minus(Duration.ofHours(3)), "warehouse@showcase.dev", "ADJUST", "STOCK_MOVEMENT", "count-adjustment", tenantSlug, "/dev/showcase/movements", "POST", "{\"reason\":\"COUNT_ADJUSTMENT\"}"),
                buildAuditLog(now.minus(Duration.ofDays(2)), "ops@showcase.dev", "CREATE", "SUPPLIER", "SHOWCASE-SUP-001", tenantSlug, "/dev/showcase/suppliers", "POST", "{\"code\":\"SHOWCASE-SUP-001\"}"),
                buildAuditLog(now.minus(Duration.ofDays(2)).minus(Duration.ofHours(2)), "ops@showcase.dev", "LINK", "PRODUCT_SUPPLIER", "SHOWCASE-LINK-01", tenantSlug, "/dev/showcase/product-suppliers", "POST", "{\"primary\":true}"),
                buildAuditLog(now.minus(Duration.ofDays(3)), "planner@showcase.dev", "VOID", "COUNT_SESSION", "SHOWCASE Cycle Count - Void", tenantSlug, "/dev/showcase/counting", "POST", "{\"status\":\"VOID\"}"),
                buildAuditLog(now.minus(Duration.ofDays(3)).minus(Duration.ofHours(4)), "safety@showcase.dev", "CREATE", "HAZARD_BUFFER", "showcase-flammable-buffer", tenantSlug, "/dev/showcase/hazard-buffers", "POST", "{\"name\":\"Showcase Flammable Buffer\"}"),
                buildAuditLog(now.minus(Duration.ofDays(4)), "warehouse@showcase.dev", "PICK", "STOCK_MOVEMENT", "pick-01", tenantSlug, "/dev/showcase/movements", "POST", "{\"type\":\"PICK\"}"),
                buildAuditLog(now.minus(Duration.ofDays(4)).minus(Duration.ofHours(6)), "ops@showcase.dev", "TRANSFER", "STOCK_MOVEMENT", "transfer-01", tenantSlug, "/dev/showcase/movements", "POST", "{\"type\":\"TRANSFER\"}"),
                buildAuditLog(now.minus(Duration.ofDays(5)), "ops@showcase.dev", "UPDATE", "PRODUCT", "DEV-STD-004", tenantSlug, "/dev/showcase/products", "PATCH", "{\"active\":false}"),
                buildAuditLog(now.minus(Duration.ofDays(5)).minus(Duration.ofHours(2)), "planner@showcase.dev", "RECEIVE", "STOCK_MOVEMENT", "receive-01", tenantSlug, "/dev/showcase/movements", "POST", "{\"type\":\"RECEIVE\"}"),
                buildAuditLog(now.minus(Duration.ofDays(6)), "safety@showcase.dev", "WARN", "GIS_ZONE", "showcase-perishables", tenantSlug, "/dev/showcase/zones", "POST", "{\"ruleType\":\"ALLOWED\"}"),
                buildAuditLog(now.minus(Duration.ofDays(6)).minus(Duration.ofHours(3)), "warehouse@showcase.dev", "CREATE", "RECEIPT_DOCUMENT", "SHOWCASE-RCV-004", tenantSlug, "/dev/showcase/receipts", "POST", "{\"status\":\"POSTED\"}"));
        auditLogRepo.saveAll(logs);
        return logs.size();
    }

    private AuditLog buildAuditLog(
            Instant occurredAt,
            String actorEmail,
            String action,
            String entityType,
            String entityId,
            String tenantSlug,
            String requestPath,
            String requestMethod,
            String afterState) {
        return AuditLog.builder()
                .occurredAt(occurredAt)
                .actorEmail(actorEmail)
                .actorRoles("[\"TENANT_ADMIN\"]")
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .beforeState(null)
                .afterState(afterState)
                .tenantId(tenantSlug)
                .requestPath(requestPath)
                .requestMethod(requestMethod)
                .build();
    }

    private int seedShowcaseBaseStock(List<LayoutBlock> leafBlocks, List<Product> products) {
        List<StockMovement> movements = new ArrayList<>();
        Instant now = Instant.now();

        for (int i = 0; i < leafBlocks.size(); i++) {
            UUID locationId = leafBlocks.get(i).getId();
            int[] pidx = { i % products.size(), (i + 7) % products.size(), (i + 13) % products.size() };

            for (int slot = 0; slot < pidx.length; slot++) {
                int p = pidx[slot];
                int qty = 12 + (i * 17 + p * 11 + 43) % 260;
                int dayOffset = (i + slot * 3) % 14;

                movements.add(StockMovement.builder()
                        .locationId(locationId)
                        .productId(products.get(p).getId())
                        .qty(BigDecimal.valueOf(qty))
                        .type(MovementType.RECEIVE)
                        .notes("dev-showcase-base")
                        .createdBy("dev-showcase")
                        .createdAt(now.minus(Duration.ofDays(dayOffset)).minus(Duration.ofHours((i + slot) % 12)))
                        .build());
            }
        }

        movementRepo.saveAll(movements);
        return movements.size();
    }

    private int seedShowcaseMovements(List<LayoutBlock> leafBlocks, Map<String, Product> productsBySku, Instant now) {
        if (leafBlocks.size() < 8) {
            return 0;
        }

        List<StockMovement> movements = new ArrayList<>();
        Product perishMilk = productsBySku.get("DEV-PRD-001");
        Product perishCheese = productsBySku.get("DEV-PRD-002");
        Product electronics = productsBySku.get("DEV-ELX-001");
        Product tools = productsBySku.get("DEV-TLS-001");
        Product flammable = productsBySku.get("DEV-TLS-005");
        Product standard = productsBySku.get("DEV-STD-001");
        Product inactive = productsBySku.get("DEV-STD-004");

        for (int day = 0; day < 7; day++) {
            Instant createdAt = now.minus(Duration.ofDays(6L - day)).minus(Duration.ofHours(2));
            LayoutBlock receiveLocation = leafBlocks.get(day % leafBlocks.size());
            LayoutBlock pickLocation = leafBlocks.get((day + 2) % leafBlocks.size());
            LayoutBlock transferFrom = leafBlocks.get((day + 4) % leafBlocks.size());
            LayoutBlock transferTo = leafBlocks.get((day + 5) % leafBlocks.size());
            LayoutBlock adjustLocation = leafBlocks.get((day + 6) % leafBlocks.size());

            if (perishMilk != null) {
                movements.add(StockMovement.builder()
                        .locationId(receiveLocation.getId())
                        .productId(perishMilk.getId())
                        .qty(BigDecimal.valueOf(9 + day))
                        .type(MovementType.RECEIVE)
                        .lotNumber("SHOW-LOT-M" + day)
                        .expiryDate(LocalDate.now().plusDays(3 + day))
                        .notes("dev-showcase")
                        .createdBy("dev-showcase")
                        .createdAt(createdAt)
                        .build());
            }
            if (standard != null) {
                movements.add(StockMovement.builder()
                        .locationId(pickLocation.getId())
                        .productId(standard.getId())
                        .qty(BigDecimal.valueOf(-(4 + day)))
                        .type(MovementType.PICK)
                        .notes("dev-showcase")
                        .createdBy("dev-showcase")
                        .createdAt(createdAt.plus(Duration.ofMinutes(10)))
                        .build());
            }
            if (electronics != null) {
                UUID referenceId = UUID.randomUUID();
                BigDecimal qty = BigDecimal.valueOf(5 + day);
                movements.add(StockMovement.builder()
                        .locationId(transferFrom.getId())
                        .productId(electronics.getId())
                        .qty(qty.negate())
                        .type(MovementType.TRANSFER_OUT)
                        .referenceId(referenceId)
                        .notes("dev-showcase")
                        .createdBy("dev-showcase")
                        .createdAt(createdAt.plus(Duration.ofMinutes(20)))
                        .build());
                movements.add(StockMovement.builder()
                        .locationId(transferTo.getId())
                        .productId(electronics.getId())
                        .qty(qty)
                        .type(MovementType.TRANSFER_IN)
                        .referenceId(referenceId)
                        .notes("dev-showcase")
                        .createdBy("dev-showcase")
                        .createdAt(createdAt.plus(Duration.ofMinutes(21)))
                        .build());
            }
            if (tools != null) {
                movements.add(StockMovement.builder()
                        .locationId(adjustLocation.getId())
                        .productId(tools.getId())
                        .qty(BigDecimal.valueOf(day % 2 == 0 ? 2 : -3))
                        .type(MovementType.ADJUST)
                        .reasonCode("SHOWCASE_REVIEW")
                        .notes("dev-showcase")
                        .createdBy("dev-showcase")
                        .createdAt(createdAt.plus(Duration.ofMinutes(30)))
                        .build());
            }
            if (perishCheese != null) {
                movements.add(StockMovement.builder()
                        .locationId(adjustLocation.getId())
                        .productId(perishCheese.getId())
                        .qty(BigDecimal.valueOf(day % 2 == 0 ? 1 : -2))
                        .type(MovementType.ADJUST)
                        .reasonCode("COUNT_ADJUSTMENT")
                        .notes("dev-showcase")
                        .createdBy("dev-showcase")
                        .createdAt(createdAt.plus(Duration.ofMinutes(40)))
                        .build());
            }
        }

        LayoutBlock hazardLocation = leafBlocks.getFirst();
        if (flammable != null) {
            movements.add(StockMovement.builder()
                    .locationId(hazardLocation.getId())
                    .productId(flammable.getId())
                    .qty(BigDecimal.valueOf(18))
                    .type(MovementType.RECEIVE)
                    .notes("dev-showcase")
                    .createdBy("dev-showcase")
                    .createdAt(now.minus(Duration.ofDays(1)).minus(Duration.ofHours(1)))
                    .build());
        }
        if (inactive != null) {
            movements.add(StockMovement.builder()
                    .locationId(leafBlocks.get(1).getId())
                    .productId(inactive.getId())
                    .qty(BigDecimal.valueOf(7))
                    .type(MovementType.RECEIVE)
                    .notes("dev-showcase")
                    .createdBy("dev-showcase")
                    .createdAt(now.minus(Duration.ofDays(2)).minus(Duration.ofHours(2)))
                    .build());
        }

        movementRepo.saveAll(movements);
        return movements.size();
    }

    private Polygon gridPolygon(int startCol, int endColInclusive, int startRow, int endRowInclusive) {
        double minLon = BASE_LON + startCol * (CELL_LON + GAP_LON);
        double maxLon = BASE_LON + endColInclusive * (CELL_LON + GAP_LON) + CELL_LON;
        double minLat = BASE_LAT + startRow * (CELL_LAT + GAP_LAT);
        double maxLat = BASE_LAT + endRowInclusive * (CELL_LAT + GAP_LAT) + CELL_LAT;
        return boundsPolygon(minLon, maxLon, minLat, maxLat);
    }

    private Polygon boundsPolygon(double minLon, double maxLon, double minLat, double maxLat) {
        return geomFactory.createPolygon(new Coordinate[] {
                new Coordinate(minLon, minLat),
                new Coordinate(maxLon, minLat),
                new Coordinate(maxLon, maxLat),
                new Coordinate(minLon, maxLat),
                new Coordinate(minLon, minLat)
        });
    }

    // ── Stock seeding ─────────────────────────────────────────────────────────

    /**
     * Creates RECEIVE movements for every leaf location.
     *
     * <p>
     * Three products are assigned to each location using offsets that are
     * coprime to the product list size (20), so no two slots in a single location
     * receive the same product, and the distribution varies across locations to
     * produce visible variation across the seeded layout.
     */
    private int seedStock(List<LayoutBlock> leafBlocks, List<Product> products) {
        List<StockMovement> movements = new ArrayList<>();

        for (int i = 0; i < leafBlocks.size(); i++) {
            UUID locationId = leafBlocks.get(i).getId();

            // Three distinct product indices per location; offsets 7 and 13 are
            // coprime to 20, so no duplicates for any i in [0..19].
            int[] pidx = { i % 20, (i + 7) % 20, (i + 13) % 20 };

            for (int p : pidx) {
                // Deterministic, varied quantity in range [10, 299].
                int qty = 10 + (i * 17 + p * 11 + 43) % 290;

                movements.add(StockMovement.builder()
                        .locationId(locationId)
                        .productId(products.get(p).getId())
                        .qty(BigDecimal.valueOf(qty))
                        .type(MovementType.RECEIVE)
                        .notes("dev-seeder")
                        .createdBy("dev-seeder")
                        .build());
            }
        }

        movementRepo.saveAll(movements);
        return movements.size();
    }
}
