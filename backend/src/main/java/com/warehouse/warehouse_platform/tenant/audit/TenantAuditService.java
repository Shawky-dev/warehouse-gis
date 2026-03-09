package com.warehouse.warehouse_platform.tenant.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class TenantAuditService {

    private final AuditLogRepository auditLogRepository;
    private final TenantAuditContextProvider tenantAuditContextProvider;
    private final ObjectMapper objectMapper;

    public TenantAuditService(
            AuditLogRepository auditLogRepository,
            TenantAuditContextProvider tenantAuditContextProvider,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.tenantAuditContextProvider = tenantAuditContextProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(
            String action,
            String entityType,
            String entityId,
            Object beforeState,
            Object afterState) {
        TenantAuditContextProvider.AuditContext context = tenantAuditContextProvider.currentContext();

        AuditLog auditLog = AuditLog.builder()
                .actorEmail(normalizeActor(context.actorEmail()))
                .actorRoles(toJson(context.actorRoles()))
                .action(normalizeRequired(action, "action"))
                .entityType(normalizeRequired(entityType, "entityType"))
                .entityId(normalizeRequired(entityId, "entityId"))
                .beforeState(toJson(beforeState))
                .afterState(toJson(afterState))
                .tenantId(normalizeRequired(context.tenantId(), "tenantId"))
                .requestPath(context.requestPath())
                .requestMethod(context.requestMethod())
                .build();

        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public AuditPageResult listAuditLogs(
            int page,
            int size,
            String actorEmail,
            String action,
            String entityType,
            String entityId,
            LocalDate fromDate,
            LocalDate toDate) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));

        Instant fromInstant = fromDate == null ? null : fromDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstantExclusive = toDate == null ? null : toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        Page<AuditLog> logs = auditLogRepository.findAll(
                buildSpecification(actorEmail, action, entityType, entityId, fromInstant, toInstantExclusive),
                pageable);

        List<AuditLogItem> content = logs.getContent().stream()
                .map(this::toItem)
                .toList();

        return new AuditPageResult(
                content,
                logs.getNumber(),
                logs.getSize(),
                logs.getTotalElements(),
                logs.getTotalPages());
    }

    private Specification<AuditLog> buildSpecification(
            String actorEmail,
            String action,
            String entityType,
            String entityId,
            Instant fromInstant,
            Instant toInstantExclusive) {
        return (root, query, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (actorEmail != null && !actorEmail.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("actorEmail")),
                        "%" + actorEmail.trim().toLowerCase() + "%"));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("action"), action.trim()));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("entityType"), entityType.trim()));
            }
            if (entityId != null && !entityId.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("entityId"), entityId.trim()));
            }
            if (fromInstant != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), fromInstant));
            }
            if (toInstantExclusive != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("occurredAt"), toInstantExclusive));
            }

            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private AuditLogItem toItem(AuditLog log) {
        return new AuditLogItem(
                log.getId(),
                log.getOccurredAt(),
                log.getActorEmail(),
                log.getActorRoles(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getBeforeState(),
                log.getAfterState(),
                log.getTenantId(),
                log.getRequestPath(),
                log.getRequestMethod());
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw TenantAuditException.badRequest("Failed to serialize audit payload");
        }
    }

    private String normalizeActor(String actorEmail) {
        if (actorEmail == null || actorEmail.isBlank()) {
            return "system";
        }
        return actorEmail.trim().toLowerCase();
    }

    private String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw TenantAuditException.badRequest(field + " must not be blank");
        }
        return value.trim();
    }

    public record AuditLogItem(
            java.util.UUID id,
            Instant occurredAt,
            String actorEmail,
            String actorRoles,
            String action,
            String entityType,
            String entityId,
            String beforeState,
            String afterState,
            String tenantId,
            String requestPath,
            String requestMethod) {
    }

    public record AuditPageResult(
            List<AuditLogItem> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
