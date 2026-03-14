package com.warehouse.warehouse_platform.tenant.warehouse.layout;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplate;
import com.warehouse.warehouse_platform.tenant.warehouse.block.BlockTemplateRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockService;
import com.warehouse.warehouse_platform.tenant.warehouse.block.LayoutBlockRepository;
import com.warehouse.warehouse_platform.tenant.warehouse.common.WarehouseManagementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class WarehouseLayoutService {

    private final WarehouseLayoutRepository layoutRepository;
    private final LayoutBlockRepository layoutBlockRepository;
    private final BlockTemplateRepository blockTemplateRepository;
    private final LayoutBlockService layoutBlockService;
    private final TenantAuditService tenantAuditService;

    public WarehouseLayoutService(
            WarehouseLayoutRepository layoutRepository,
            LayoutBlockRepository layoutBlockRepository,
            BlockTemplateRepository blockTemplateRepository,
            LayoutBlockService layoutBlockService,
            TenantAuditService tenantAuditService) {
        this.layoutRepository = layoutRepository;
        this.layoutBlockRepository = layoutBlockRepository;
        this.blockTemplateRepository = blockTemplateRepository;
        this.layoutBlockService = layoutBlockService;
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public LayoutPageResult listLayouts(int page, int size, String search, Boolean active) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        Page<WarehouseLayout> result = layoutRepository.findAll(buildSpecification(search, active), pageable);
        return new LayoutPageResult(
                result.getContent().stream().map(this::toResult).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public LayoutResult getLayout(UUID id) {
        return toResult(loadLayout(id));
    }

    @Transactional
    public LayoutResult createLayout(String name, String description) {
        String normalizedName = normalizeName(name);
        String normalizedDesc = normalizeOptional(description, 500, "description");

        layoutRepository.findByNameIgnoreCase(normalizedName)
                .ifPresent(existing -> {
                    throw WarehouseManagementException.conflict("Layout name already exists: " + normalizedName);
                });

        WarehouseLayout layout = WarehouseLayout.builder()
                .name(normalizedName)
                .description(normalizedDesc)
                .isActive(false)
                .build();

        WarehouseLayout saved = saveLayout(layout);
        LayoutResult result = toResult(saved);
        tenantAuditService.record("WAREHOUSE_LAYOUT_CREATE", "WAREHOUSE_LAYOUT", result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public LayoutResult createClassicPreset(String name, String description, boolean activate) {
        WarehouseLayout layout = createLayoutEntity(name, description);
        WarehouseLayout savedLayout = saveLayout(layout);
        UUID savedLayoutId = savedLayout.getId();

        UUID aisleTemplateId = resolveClassicTemplate("Aisle", BlockTemplate.IdentifierFormat.ALPHA,
                BlockTemplate.SideConfig.NONE, true, "Primary warehouse aisle segment", "AlignJustify").getId();
        UUID bayTemplateId = resolveClassicTemplate("Bay", BlockTemplate.IdentifierFormat.NUMERIC,
                BlockTemplate.SideConfig.LR, true, "Numbered bay segment on a left or right aisle side",
                "Layers").getId();
        UUID levelTemplateId = resolveClassicTemplate("Level", BlockTemplate.IdentifierFormat.NUMERIC,
                BlockTemplate.SideConfig.NONE, true, "Vertical bay level", "List").getId();
        UUID shelfTemplateId = resolveClassicTemplate("Shelf", BlockTemplate.IdentifierFormat.NUMERIC,
                BlockTemplate.SideConfig.NONE, true, "Shelf within a level", "MapPin").getId();

        LayoutBlockService.BlockResult aisleBlock = layoutBlockService.addBlock(savedLayoutId, aisleTemplateId, null, 0,
                null);
        LayoutBlockService.BlockResult bayBlock = layoutBlockService.addBlock(savedLayoutId, bayTemplateId,
                aisleBlock.id(), 0, null);
        LayoutBlockService.BlockResult levelBlock = layoutBlockService.addBlock(savedLayoutId, levelTemplateId,
                bayBlock.id(), 0, null);
        layoutBlockService.addBlock(savedLayoutId, shelfTemplateId, levelBlock.id(), 0, null);

        if (activate) {
            layoutRepository.findByIsActiveTrue()
                    .filter(current -> !current.getId().equals(savedLayoutId))
                    .ifPresent(current -> {
                        current.setIsActive(false);
                        saveLayout(current);
                    });
            savedLayout.setIsActive(true);
            savedLayout = saveLayout(savedLayout);
        }

        LayoutResult result = toResult(savedLayout);
        tenantAuditService.record("WAREHOUSE_LAYOUT_CREATE_CLASSIC_PRESET", "WAREHOUSE_LAYOUT",
                result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public LayoutResult updateLayout(UUID id, String name, String description) {
        WarehouseLayout existing = loadLayout(id);
        LayoutResult before = toResult(existing);

        String normalizedName = normalizeName(name);
        String normalizedDesc = normalizeOptional(description, 500, "description");

        layoutRepository.findByNameIgnoreCase(normalizedName)
                .filter(found -> !found.getId().equals(id))
                .ifPresent(found -> {
                    throw WarehouseManagementException.conflict("Layout name already exists: " + normalizedName);
                });

        existing.setName(normalizedName);
        existing.setDescription(normalizedDesc);

        WarehouseLayout saved = saveLayout(existing);
        LayoutResult after = toResult(saved);
        tenantAuditService.record("WAREHOUSE_LAYOUT_UPDATE", "WAREHOUSE_LAYOUT", after.id().toString(), before, after);
        return after;
    }

    @Transactional
    public void activateLayout(UUID id) {
        WarehouseLayout layout = loadLayout(id);
        LayoutResult before = toResult(layout);

        if (Boolean.TRUE.equals(layout.getIsActive())) {
            return;
        }

        // Deactivate the currently active layout, if any
        layoutRepository.findByIsActiveTrue().ifPresent(current -> {
            current.setIsActive(false);
            saveLayout(current);
        });

        layout.setIsActive(true);
        saveLayout(layout);

        tenantAuditService.record("WAREHOUSE_LAYOUT_ACTIVATE", "WAREHOUSE_LAYOUT", id.toString(), before,
                toResult(layout));
    }

    @Transactional
    public void deactivateLayout(UUID id) {
        WarehouseLayout layout = loadLayout(id);
        LayoutResult before = toResult(layout);

        if (!Boolean.TRUE.equals(layout.getIsActive())) {
            return;
        }

        layout.setIsActive(false);
        saveLayout(layout);

        tenantAuditService.record("WAREHOUSE_LAYOUT_DEACTIVATE", "WAREHOUSE_LAYOUT", id.toString(), before,
                toResult(layout));
    }

    @Transactional
    public void deleteLayout(UUID id) {
        WarehouseLayout layout = loadLayout(id);

        if (Boolean.TRUE.equals(layout.getIsActive())) {
            throw WarehouseManagementException.forbidden("Active layout cannot be deleted. Deactivate it first.");
        }

        long blockCount = layoutBlockRepository.countByLayoutId(id);
        if (blockCount > 0) {
            throw WarehouseManagementException.conflict(
                    "Layout cannot be deleted while it has " + blockCount + " block(s). Remove all blocks first.");
        }

        LayoutResult before = toResult(layout);
        layoutRepository.delete(layout);
        tenantAuditService.record("WAREHOUSE_LAYOUT_DELETE", "WAREHOUSE_LAYOUT", id.toString(), before, null);
    }

    private WarehouseLayout loadLayout(UUID id) {
        return layoutRepository.findById(Objects.requireNonNull(id, "id must not be null"))
                .orElseThrow(() -> WarehouseManagementException.notFound("Layout not found: " + id));
    }

    @SuppressWarnings("null")
    private WarehouseLayout saveLayout(WarehouseLayout layout) {
        return Objects.requireNonNull(layoutRepository.save(layout), "layoutRepository.save returned null");
    }

    private WarehouseLayout createLayoutEntity(String name, String description) {
        String normalizedName = normalizeName(name);
        String normalizedDesc = normalizeOptional(description, 500, "description");

        layoutRepository.findByNameIgnoreCase(normalizedName)
                .ifPresent(existing -> {
                    throw WarehouseManagementException.conflict("Layout name already exists: " + normalizedName);
                });

        return WarehouseLayout.builder()
                .name(normalizedName)
                .description(normalizedDesc)
                .isActive(false)
                .build();
    }

    private BlockTemplate resolveClassicTemplate(
            String name,
            BlockTemplate.IdentifierFormat identifierFormat,
            BlockTemplate.SideConfig sideConfig,
            boolean required,
            String description,
            String iconName) {
        Optional<BlockTemplate> existing = blockTemplateRepository.findByNameIgnoreCase(name);
        if (existing.isPresent()) {
            BlockTemplate template = existing.get();
            if (template.getIconName() == null || template.getIconName().isBlank()) {
                template.setIconName(iconName);
                return saveTemplate(template);
            }
            return template;
        }

        BlockTemplate template = BlockTemplate.builder()
                .name(name)
                .identifierFormat(identifierFormat)
                .sideConfig(sideConfig)
                .required(required)
                .description(description)
                .iconName(iconName)
                .build();
        return saveTemplate(template);
    }

    @SuppressWarnings("null")
    private BlockTemplate saveTemplate(BlockTemplate template) {
        return Objects.requireNonNull(blockTemplateRepository.save(template),
                "blockTemplateRepository.save returned null");
    }

    private Specification<WarehouseLayout> buildSpecification(String search, Boolean active) {
        String normalizedSearch = (search == null) ? null : search.trim().isEmpty() ? null : search.trim();
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (active != null) {
                predicates.add(cb.equal(root.get("isActive"), active));
            }

            if (normalizedSearch != null) {
                String value = "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), value));
            }

            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw WarehouseManagementException.badRequest("name must not be blank");
        }
        String normalized = name.trim();
        if (normalized.length() > 120) {
            throw WarehouseManagementException.badRequest("name must be at most 120 characters");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String field) {
        if (value == null)
            return null;
        String normalized = value.trim();
        if (normalized.isEmpty())
            return null;
        if (normalized.length() > maxLength) {
            throw WarehouseManagementException.badRequest(field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private LayoutResult toResult(WarehouseLayout layout) {
        return new LayoutResult(
                layout.getId(),
                layout.getName(),
                layout.getDescription(),
                Boolean.TRUE.equals(layout.getIsActive()),
                layout.getCreatedAt(),
                layout.getUpdatedAt());
    }

    public record LayoutResult(
            UUID id,
            String name,
            String description,
            boolean isActive,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record LayoutPageResult(
            List<LayoutResult> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
