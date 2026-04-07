package com.warehouse.warehouse_platform.dev;

import com.warehouse.warehouse_platform.tenant.access.TenantAccessPolicy;
import com.warehouse.warehouse_platform.tenant.category.ProductCategory;
import com.warehouse.warehouse_platform.tenant.category.ProductCategoryRepository;
import com.warehouse.warehouse_platform.tenant.gis.model.GisBlock;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisBlockRepository;
import com.warehouse.warehouse_platform.tenant.hazardtype.HazardType;
import com.warehouse.warehouse_platform.tenant.hazardtype.HazardTypeRepository;
import com.warehouse.warehouse_platform.tenant.inventory.MovementType;
import com.warehouse.warehouse_platform.tenant.inventory.StockMovement;
import com.warehouse.warehouse_platform.tenant.inventory.StockMovementRepository;
import com.warehouse.warehouse_platform.tenant.product.Product;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *   ?withGis=false — skips gis_blocks generation (heatmap will not work, but
 *                    all other features still can be tested)
 *
 * DELETE /{tenantSlug}/dev/seed
 *   Wipes only demo data (stock movements for demo locations, the Demo Warehouse
 *   layout, block templates, DEV- products, DEV_ categories). UOMs, hazard
 *   types, location kinds, and permissions are never touched.
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
 *   GIS floor-plan blocks so the heatmap overlays correctly in the viewer.
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
        if (exists) {
            doWipe();
        }

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
        int movCount = seedStock(layoutData.leafBlocks(), products);

        return ResponseEntity.ok(new SeedResult(
                uoms.size(),
                categories.size(),
                products.size(),
                templates.size(),
                layoutData.allBlocks().size(),
                layoutData.leafBlocks().size(),
                gisCount,
                movCount,
                layoutData.layoutActive(),
                false));
    }

    @DeleteMapping("/seed")
    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> wipe(
            @PathVariable String tenantSlug,
            Authentication authentication) {

        tenantAccessPolicy.assertTenantAccess(authentication, tenantSlug);
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
     * Every row gets a {@code centroid_geom} so the heatmap query resolves
     * correctly for leaves and the GIS viewer can render all layers.
     *
     * <p>
     * Two options are exposed at the API level:
     * <ul>
     * <li>{@code withGis=true} — calls this method; heatmap works immediately.</li>
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

    // ── Stock seeding ─────────────────────────────────────────────────────────

    /**
     * Creates RECEIVE movements for every leaf location.
     *
     * <p>
     * Three products are assigned to each location using offsets that are
     * coprime to the product list size (20), so no two slots in a single location
     * receive the same product, and the distribution varies across locations to
     * produce a visible gradient in the heatmap.
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
