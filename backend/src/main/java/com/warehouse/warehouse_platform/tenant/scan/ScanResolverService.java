package com.warehouse.warehouse_platform.tenant.scan;

import com.warehouse.warehouse_platform.tenant.counting.CountSession;
import com.warehouse.warehouse_platform.tenant.counting.CountSessionRepository;
import com.warehouse.warehouse_platform.tenant.dispatch.DispatchDocument;
import com.warehouse.warehouse_platform.tenant.dispatch.DispatchDocumentRepository;
import com.warehouse.warehouse_platform.tenant.inventory.StockMovementRepository;
import com.warehouse.warehouse_platform.tenant.product.Product;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import com.warehouse.warehouse_platform.tenant.receipt.ReceiptDocument;
import com.warehouse.warehouse_platform.tenant.receipt.ReceiptDocumentRepository;
import com.warehouse.warehouse_platform.tenant.receipt.ReceiptLine;
import com.warehouse.warehouse_platform.tenant.receipt.ReceiptLineRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlock;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScanResolverService {

    private final ProductRepository productRepository;
    private final LayoutBlockRepository layoutBlockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ReceiptDocumentRepository receiptDocumentRepository;
    private final ReceiptLineRepository receiptLineRepository;
    private final DispatchDocumentRepository dispatchDocumentRepository;
    private final CountSessionRepository countSessionRepository;

    public ScanResolverService(
            ProductRepository productRepository,
            LayoutBlockRepository layoutBlockRepository,
            StockMovementRepository stockMovementRepository,
            ReceiptDocumentRepository receiptDocumentRepository,
            ReceiptLineRepository receiptLineRepository,
            DispatchDocumentRepository dispatchDocumentRepository,
            CountSessionRepository countSessionRepository) {
        this.productRepository = productRepository;
        this.layoutBlockRepository = layoutBlockRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.receiptDocumentRepository = receiptDocumentRepository;
        this.receiptLineRepository = receiptLineRepository;
        this.dispatchDocumentRepository = dispatchDocumentRepository;
        this.countSessionRepository = countSessionRepository;
    }

    @Transactional(readOnly = true)
    public ScanResolveResult resolve(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scan code must not be blank");
        }
        String code = rawCode.strip();

        // Step 1: exact SKU match (case-insensitive)
        Optional<Product> productOpt = productRepository.findBySkuIgnoreCase(code);
        if (productOpt.isPresent()) {
            Product p = productOpt.get();
            if (Boolean.TRUE.equals(p.getActive())) {
                return ScanResolveResult.builder()
                        .type(ScanType.PRODUCT)
                        .productId(p.getId())
                        .productSku(p.getSku())
                        .productName(p.getName())
                        .trackLot(p.getTrackLot())
                        .trackExpiry(p.getTrackExpiry())
                        .displayLabel(p.getName() + " (" + p.getSku() + ")")
                        .build();
            }
        }

        // Step 2: exact scan_code match on layout blocks
        Optional<LayoutBlock> blockOpt = layoutBlockRepository.findByScanCode(code);
        if (blockOpt.isPresent()) {
            LayoutBlock block = blockOpt.get();
            String kindName = block.getLocationKind() != null ? block.getLocationKind().getName() : null;
            String pathLabel = block.getFullCode() != null && !block.getFullCode().isBlank()
                    ? block.getFullCode()
                    : block.getScanCode();
            return ScanResolveResult.builder()
                    .type(ScanType.LOCATION)
                    .locationId(block.getId())
                    .locationPathLabel(pathLabel)
                    .locationKindName(kindName)
                    .scanCode(block.getScanCode())
                    .fullCode(block.getFullCode())
                    .displayLabel(pathLabel)
                    .build();
        }

        // Step 3: LOT pattern — "{sku}:{lotNumber}" with exactly one ':'
        int colonIndex = code.indexOf(':');
        if (colonIndex > 0 && colonIndex == code.lastIndexOf(':')) {
            String skuPart = code.substring(0, colonIndex);
            String lotPart = code.substring(colonIndex + 1);
            if (!skuPart.isBlank() && !lotPart.isBlank()) {
                Optional<Product> lotProductOpt = productRepository.findBySkuIgnoreCase(skuPart);
                if (lotProductOpt.isPresent()) {
                    Product p = lotProductOpt.get();
                    if (Boolean.TRUE.equals(p.getActive())
                            && stockMovementRepository.existsByProductIdAndLotNumber(p.getId(), lotPart)) {
                        return ScanResolveResult.builder()
                                .type(ScanType.LOT)
                                .productId(p.getId())
                                .productSku(p.getSku())
                                .productName(p.getName())
                                .lotNumber(lotPart)
                                .displayLabel(p.getName() + " / Lot " + lotPart)
                                .build();
                    }
                }
            }
        }

        // Step 3.5: STOCK_ROW:{locationId}:{productId}:{encodedLot}
        if (code.startsWith("STOCK_ROW:")) {
            String[] parts = code.split(":", 4);
            if (parts.length == 4) {
                UUID locationId = tryParseUuid(parts[1]);
                UUID productId = tryParseUuid(parts[2]);
                String encodedLot = parts[3];
                String lotNumber = "-".equals(encodedLot) ? null
                        : URLDecoder.decode(encodedLot, StandardCharsets.UTF_8);

                if (locationId != null && productId != null) {
                    BigDecimal liveQty = stockMovementRepository
                            .findStockQtyByLocationProductAndLot(locationId, productId, lotNumber)
                            .orElse(BigDecimal.ZERO);

                    if (liveQty.compareTo(BigDecimal.ZERO) > 0) {
                        Product product = productRepository.findById(productId).orElse(null);
                        LayoutBlock block = layoutBlockRepository.findById(locationId).orElse(null);
                        String locationLabel = block != null && block.getFullCode() != null
                                && !block.getFullCode().isBlank()
                                        ? block.getFullCode()
                                        : block != null ? block.getScanCode() : locationId.toString();
                        String productLabel = product != null
                                ? product.getName() + " (" + product.getSku() + ")"
                                : productId.toString();

                        return ScanResolveResult.builder()
                                .type(ScanType.STOCK_ROW)
                                .locationId(locationId)
                                .locationPathLabel(locationLabel)
                                .scanCode(block != null ? block.getScanCode() : null)
                                .fullCode(block != null ? block.getFullCode() : null)
                                .productId(productId)
                                .productSku(product != null ? product.getSku() : null)
                                .productName(product != null ? product.getName() : null)
                                .trackLot(product != null ? product.getTrackLot() : null)
                                .trackExpiry(product != null ? product.getTrackExpiry() : null)
                                .lotNumber(lotNumber)
                                .lineQty(liveQty)
                                .displayLabel(productLabel
                                        + (lotNumber != null ? " / Lot " + lotNumber : "")
                                        + " @ " + locationLabel)
                                .build();
                    }
                }
            }
        }

        // Step 4: RECEIPT_LINE:{uuid} — Stock Unit QR generated at receipt post
        if (code.startsWith("RECEIPT_LINE:")) {
            UUID id = tryParseUuid(code.substring(13));
            if (id != null) {
                Optional<ReceiptLine> lineOpt = receiptLineRepository.findById(id);
                if (lineOpt.isPresent()) {
                    ReceiptLine line = lineOpt.get();
                    UUID productId = line.getProductId();
                    UUID locationId = line.getDestinationLocationId();
                    Product product = productId != null ? productRepository.findById(productId).orElse(null) : null;
                    LayoutBlock block = locationId != null ? layoutBlockRepository.findById(locationId).orElse(null)
                            : null;
                    String productLabel = product != null
                            ? product.getName() + " (" + product.getSku() + ")"
                            : line.getProductId().toString();
                    String locationLabel = block != null && block.getFullCode() != null
                            && !block.getFullCode().isBlank()
                                    ? block.getFullCode()
                                    : block != null ? block.getScanCode() : line.getDestinationLocationId().toString();
                    String displayLabel = productLabel
                            + (line.getLotNumber() != null ? " / Lot " + line.getLotNumber() : "")
                            + " @ " + locationLabel;
                    return ScanResolveResult.builder()
                            .type(ScanType.RECEIPT_LINE)
                            .receiptLineId(id)
                            .receiptId(line.getReceiptId())
                            .productId(productId)
                            .productSku(product != null ? product.getSku() : null)
                            .productName(product != null ? product.getName() : null)
                            .locationId(locationId)
                            .locationPathLabel(locationLabel)
                            .lotNumber(line.getLotNumber())
                            .lineQty(line.getQty())
                            .displayLabel(displayLabel)
                            .build();
                }
            }
        }

        // Step 5: RECEIPT:{uuid}
        if (code.startsWith("RECEIPT:")) {
            UUID id = tryParseUuid(code.substring(8));
            if (id != null) {
                Optional<ReceiptDocument> receiptOpt = receiptDocumentRepository.findById(id);
                if (receiptOpt.isPresent()) {
                    ReceiptDocument receipt = receiptOpt.get();
                    String label = receipt.getReference() != null
                            ? "Receipt " + receipt.getReference()
                            : "Receipt " + id;
                    return ScanResolveResult.builder()
                            .type(ScanType.RECEIPT)
                            .receiptId(id)
                            .displayLabel(label)
                            .build();
                }
            }
        }

        // Step 6: DISPATCH:{uuid}
        if (code.startsWith("DISPATCH:")) {
            UUID id = tryParseUuid(code.substring(9));
            if (id != null) {
                Optional<DispatchDocument> dispatchOpt = dispatchDocumentRepository.findById(id);
                if (dispatchOpt.isPresent()) {
                    DispatchDocument dispatch = dispatchOpt.get();
                    String label = dispatch.getReference() != null
                            ? "Dispatch " + dispatch.getReference()
                            : "Dispatch " + id;
                    return ScanResolveResult.builder()
                            .type(ScanType.DISPATCH)
                            .dispatchId(id)
                            .displayLabel(label)
                            .build();
                }
            }
        }

        // Step 7: COUNT:{uuid}
        if (code.startsWith("COUNT:")) {
            UUID id = tryParseUuid(code.substring(6));
            if (id != null) {
                Optional<CountSession> sessionOpt = countSessionRepository.findById(id);
                if (sessionOpt.isPresent()) {
                    CountSession session = sessionOpt.get();
                    return ScanResolveResult.builder()
                            .type(ScanType.COUNT_SESSION)
                            .countSessionId(id)
                            .displayLabel("Count Session: " + session.getName())
                            .build();
                }
            }
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unresolvable scan code: " + code);
    }

    private UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
