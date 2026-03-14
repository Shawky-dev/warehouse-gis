package com.warehouse.warehouse_platform.tenant.warehouse.locationkind;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WarehouseLocationKindService {

    private final WarehouseLocationKindRepository warehouseLocationKindRepository;
    private final LayoutBlockRepository layoutBlockRepository;
    private final TenantAuditService tenantAuditService;

    public WarehouseLocationKindService(
            WarehouseLocationKindRepository warehouseLocationKindRepository,
            LayoutBlockRepository layoutBlockRepository,
            TenantAuditService tenantAuditService) {
        this.warehouseLocationKindRepository = warehouseLocationKindRepository;
        this.layoutBlockRepository = layoutBlockRepository;
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public List<LocationKindResult> listLocationKinds() {
        return warehouseLocationKindRepository.findAllOrdered().stream()
                .map(this::toResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public WarehouseLocationKind getDefaultLocationKind() {
        return warehouseLocationKindRepository.findFirstByOrderBySortOrderAscIdAsc()
                .orElseThrow(() -> WarehouseManagementException.conflict("At least one location kind must exist"));
    }

    @Transactional(readOnly = true)
    public WarehouseLocationKind getRequired(UUID id) {
        if (id == null) {
            throw WarehouseManagementException.badRequest("locationKindId must not be null");
        }
        return warehouseLocationKindRepository.findById(id)
                .orElseThrow(() -> WarehouseManagementException.badRequest("Location kind not found: " + id));
    }

    @Transactional
    public LocationKindResult createLocationKind(String name) {
        String normalizedName = normalizeName(name);
        warehouseLocationKindRepository.findByNameIgnoreCase(normalizedName)
                .ifPresent(existing -> {
                    throw WarehouseManagementException.conflict("Location kind already exists: " + normalizedName);
                });

        WarehouseLocationKind locationKind = WarehouseLocationKind.builder()
                .name(normalizedName)
                .sortOrder(warehouseLocationKindRepository.findMaxSortOrder() + 1)
                .build();

        WarehouseLocationKind saved = warehouseLocationKindRepository.save(locationKind);
        LocationKindResult result = toResult(saved);
        tenantAuditService.record("WAREHOUSE_LOCATION_KIND_CREATE", "WAREHOUSE_LOCATION_KIND", result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public LocationKindResult updateLocationKind(UUID id, String name) {
        WarehouseLocationKind existing = getRequired(id);
        LocationKindResult before = toResult(existing);
        String normalizedName = normalizeName(name);

        warehouseLocationKindRepository.findByNameIgnoreCase(normalizedName)
                .filter(found -> !found.getId().equals(id))
                .ifPresent(found -> {
                    throw WarehouseManagementException.conflict("Location kind already exists: " + normalizedName);
                });

        existing.setName(normalizedName);
        WarehouseLocationKind saved = warehouseLocationKindRepository.save(existing);
        LocationKindResult after = toResult(saved);
        tenantAuditService.record("WAREHOUSE_LOCATION_KIND_UPDATE", "WAREHOUSE_LOCATION_KIND", id.toString(), before, after);
        return after;
    }

    @Transactional
    public void deleteLocationKind(UUID id) {
        WarehouseLocationKind existing = getRequired(id);

        if (warehouseLocationKindRepository.count() <= 1) {
            throw WarehouseManagementException.forbidden("At least one location kind must remain");
        }

        long blockReferences = layoutBlockRepository.countByLocationKind_Id(id);
        if (blockReferences > 0) {
            throw WarehouseManagementException.conflict("Location kind cannot be deleted while referenced by layout blocks");
        }

        LocationKindResult before = toResult(existing);
        warehouseLocationKindRepository.delete(existing);
        tenantAuditService.record("WAREHOUSE_LOCATION_KIND_DELETE", "WAREHOUSE_LOCATION_KIND", id.toString(), before, null);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw WarehouseManagementException.badRequest("name must not be blank");
        }
        String normalized = name.trim();
        if (normalized.length() > 80) {
            throw WarehouseManagementException.badRequest("name must be at most 80 characters");
        }
        return normalized;
    }

    private LocationKindResult toResult(WarehouseLocationKind locationKind) {
        return new LocationKindResult(
                locationKind.getId(),
                locationKind.getName(),
                locationKind.getSortOrder(),
                locationKind.getCreatedAt(),
                locationKind.getUpdatedAt());
    }

    public record LocationKindResult(
            UUID id,
            String name,
            int sortOrder,
            Instant createdAt,
            Instant updatedAt) {
    }
}
