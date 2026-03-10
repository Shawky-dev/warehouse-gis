package com.warehouse.warehouse_platform.tenant.warehouse.block;

import com.warehouse.warehouse_platform.tenant.audit.TenantAuditService;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class BlockTemplateService {

    private final BlockTemplateRepository templateRepository;
    private final LayoutBlockRepository layoutBlockRepository;
    private final TenantAuditService tenantAuditService;

    public BlockTemplateService(
            BlockTemplateRepository templateRepository,
            LayoutBlockRepository layoutBlockRepository,
            TenantAuditService tenantAuditService) {
        this.templateRepository = templateRepository;
        this.layoutBlockRepository = layoutBlockRepository;
        this.tenantAuditService = tenantAuditService;
    }

    @Transactional(readOnly = true)
    public TemplatePageResult listTemplates(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        Page<BlockTemplate> result = templateRepository.findAll(buildSpecification(search), pageable);
        return new TemplatePageResult(
                result.getContent().stream().map(this::toResult).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public TemplateResult getTemplate(UUID id) {
        return toResult(loadTemplate(id));
    }

    @Transactional
    public TemplateResult createTemplate(
            String name,
            BlockTemplate.IdentifierFormat identifierFormat,
            BlockTemplate.SideConfig sideConfig,
            List<String> sideOptions,
            boolean required,
            String description) {
        String normalizedName = normalizeName(name);

        templateRepository.findByNameIgnoreCase(normalizedName)
                .ifPresent(existing -> {
                    throw WarehouseManagementException.conflict("Block template name already exists: " + normalizedName);
                });

        String sideOptionsStr = normalizeSideOptions(sideConfig, sideOptions);

        BlockTemplate template = BlockTemplate.builder()
                .name(normalizedName)
                .identifierFormat(identifierFormat)
                .sideConfig(sideConfig != null ? sideConfig : BlockTemplate.SideConfig.NONE)
                .sideOptions(sideOptionsStr)
                .required(required)
                .description(normalizeOptional(description, 500, "description"))
                .build();

        BlockTemplate saved = templateRepository.save(template);
        TemplateResult result = toResult(saved);
        tenantAuditService.record("BLOCK_TEMPLATE_CREATE", "BLOCK_TEMPLATE", result.id().toString(), null, result);
        return result;
    }

    @Transactional
    public TemplateResult updateTemplate(
            UUID id,
            String name,
            BlockTemplate.IdentifierFormat identifierFormat,
            BlockTemplate.SideConfig sideConfig,
            List<String> sideOptions,
            boolean required,
            String description) {
        BlockTemplate existing = loadTemplate(id);
        TemplateResult before = toResult(existing);

        String normalizedName = normalizeName(name);

        templateRepository.findByNameIgnoreCase(normalizedName)
                .filter(found -> !found.getId().equals(id))
                .ifPresent(found -> {
                    throw WarehouseManagementException.conflict("Block template name already exists: " + normalizedName);
                });

        String sideOptionsStr = normalizeSideOptions(sideConfig, sideOptions);

        existing.setName(normalizedName);
        existing.setIdentifierFormat(identifierFormat);
        existing.setSideConfig(sideConfig != null ? sideConfig : BlockTemplate.SideConfig.NONE);
        existing.setSideOptions(sideOptionsStr);
        existing.setRequired(required);
        existing.setDescription(normalizeOptional(description, 500, "description"));

        BlockTemplate saved = templateRepository.save(existing);
        TemplateResult after = toResult(saved);
        tenantAuditService.record("BLOCK_TEMPLATE_UPDATE", "BLOCK_TEMPLATE", after.id().toString(), before, after);
        return after;
    }

    @Transactional
    public void deleteTemplate(UUID id) {
        BlockTemplate template = loadTemplate(id);

        long usageCount = layoutBlockRepository.countByBlockTemplateId(id);
        if (usageCount > 0) {
            throw WarehouseManagementException.conflict(
                    "Block template is in use by " + usageCount + " layout block(s) and cannot be deleted.");
        }

        TemplateResult before = toResult(template);
        templateRepository.delete(template);
        tenantAuditService.record("BLOCK_TEMPLATE_DELETE", "BLOCK_TEMPLATE", id.toString(), before, null);
    }

    private BlockTemplate loadTemplate(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> WarehouseManagementException.notFound("Block template not found: " + id));
    }

    private String normalizeSideOptions(BlockTemplate.SideConfig sideConfig, List<String> sideOptions) {
        if (sideConfig != BlockTemplate.SideConfig.CUSTOM) {
            return null;
        }
        if (sideOptions == null || sideOptions.isEmpty()) {
            throw WarehouseManagementException.badRequest(
                    "sideOptions are required when sideConfig is CUSTOM");
        }
        List<String> cleaned = sideOptions.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (cleaned.size() < 2) {
            throw WarehouseManagementException.badRequest(
                    "sideOptions must contain at least 2 distinct non-empty values");
        }
        String joined = String.join(",", cleaned);
        if (joined.length() > 500) {
            throw WarehouseManagementException.badRequest("sideOptions exceeds maximum length");
        }
        return joined;
    }

    private Specification<BlockTemplate> buildSpecification(String search) {
        String normalizedSearch = (search == null) ? null : search.trim().isEmpty() ? null : search.trim();
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
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
        if (normalized.length() > 100) {
            throw WarehouseManagementException.badRequest("name must be at most 100 characters");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength, String field) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > maxLength) {
            throw WarehouseManagementException.badRequest(field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private TemplateResult toResult(BlockTemplate t) {
        List<String> parsedOptions = null;
        if (t.getSideOptions() != null && !t.getSideOptions().isBlank()) {
            parsedOptions = Arrays.asList(t.getSideOptions().split(","));
        }
        return new TemplateResult(
                t.getId(),
                t.getName(),
                t.getIdentifierFormat(),
                t.getSideConfig(),
                parsedOptions,
                Boolean.TRUE.equals(t.getRequired()),
                t.getDescription(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }

    public record TemplateResult(
            UUID id,
            String name,
            BlockTemplate.IdentifierFormat identifierFormat,
            BlockTemplate.SideConfig sideConfig,
            List<String> sideOptions,
            boolean required,
            String description,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record TemplatePageResult(
            List<TemplateResult> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
