package com.warehouse.warehouse_platform.tenant.gis;

import com.warehouse.warehouse_platform.tenant.gis.model.GisHazardBuffer;
import com.warehouse.warehouse_platform.tenant.gis.model.GisZone;
import com.warehouse.warehouse_platform.tenant.hazardtype.HazardType;
import com.warehouse.warehouse_platform.tenant.zonetype.ZoneType;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

/**
 * Unified storage rule violation covering three rule types:
 * <ul>
 * <li>HAZARD_BUFFER – product's hazard type is restricted by a buffer; always
 * BLOCK</li>
 * <li>ZONE – product's category is prohibited in the containing zone (BLOCK or
 * WARN)</li>
 * <li>REQUIRED_ZONE – product's category requires a zone type not present at
 * location; always WARN</li>
 * </ul>
 */
public class StorageRuleViolationException extends RuntimeException {

    public enum RuleType {
        HAZARD_BUFFER, ZONE, REQUIRED_ZONE
    }

    // ── Summary records ───────────────────────────────────────────────────

    public record ZoneSummary(UUID id, String name, String violationAction,
            UUID zoneTypeId, String zoneTypeCode, String displayColor) {

        public static ZoneSummary of(GisZone z) {
            ZoneType zt = z.getZoneType();
            return new ZoneSummary(
                    z.getId(),
                    z.getName(),
                    z.getViolationAction(),
                    zt != null ? zt.getId() : null,
                    zt != null ? zt.getCode() : null,
                    z.getDisplayColor());
        }
    }

    public record HazardBufferSummary(UUID id, String name) {

        public static HazardBufferSummary of(GisHazardBuffer b) {
            return new HazardBufferSummary(b.getId(), b.getName());
        }
    }

    public record HazardTypeSummary(UUID id, String code, String displayName) {

        public static HazardTypeSummary of(HazardType h) {
            return new HazardTypeSummary(h.getId(), h.getCode(), h.getDisplayName());
        }
    }

    public record ZoneTypeSummary(UUID id, String code, String displayName) {

        public static ZoneTypeSummary of(ZoneType zt) {
            return new ZoneTypeSummary(zt.getId(), zt.getCode(), zt.getDisplayName());
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────

    private final HttpStatus status;
    private final String code;
    private final RuleType ruleType;
    private final String violationAction;

    /** Set for ZONE violations. */
    private final ZoneSummary violatedZone;

    /** Set for HAZARD_BUFFER violations. */
    private final HazardBufferSummary violatedBuffer;

    /** Set for HAZARD_BUFFER violations: the hazard types that are restricted. */
    private final List<HazardTypeSummary> restrictedHazardTypes;

    /** Set for REQUIRED_ZONE violations: the zone type that is missing. */
    private final ZoneTypeSummary requiredZoneType;

    /**
     * Set for ZONE and REQUIRED_ZONE: suggested zones where the operation could
     * succeed.
     */
    private final List<ZoneSummary> suggestedZones;

    private StorageRuleViolationException(
            HttpStatus status,
            String code,
            RuleType ruleType,
            String violationAction,
            String message,
            ZoneSummary violatedZone,
            HazardBufferSummary violatedBuffer,
            List<HazardTypeSummary> restrictedHazardTypes,
            ZoneTypeSummary requiredZoneType,
            List<ZoneSummary> suggestedZones) {
        super(message);
        this.status = status;
        this.code = code;
        this.ruleType = ruleType;
        this.violationAction = violationAction;
        this.violatedZone = violatedZone;
        this.violatedBuffer = violatedBuffer;
        this.restrictedHazardTypes = restrictedHazardTypes;
        this.requiredZoneType = requiredZoneType;
        this.suggestedZones = suggestedZones;
    }

    // ── Factory methods ───────────────────────────────────────────────────

    /**
     * HAZARD_BUFFER: product's hazard type is restricted by the given buffer(s).
     * Always a BLOCK (HTTP 409 Conflict).
     */
    public static StorageRuleViolationException hazardBufferBlock(
            GisHazardBuffer matchedBuffer,
            List<HazardType> restrictedTypes) {
        return new StorageRuleViolationException(
                HttpStatus.CONFLICT,
                "HAZARD_BUFFER_VIOLATION",
                RuleType.HAZARD_BUFFER,
                "BLOCK",
                "Product's hazard type is restricted in hazard buffer: " + matchedBuffer.getName(),
                null,
                HazardBufferSummary.of(matchedBuffer),
                restrictedTypes.stream().map(HazardTypeSummary::of).toList(),
                null,
                List.of());
    }

    /**
     * ZONE: product's category is prohibited in the specified zone.
     */
    public static StorageRuleViolationException zoneViolation(
            GisZone zone,
            List<GisZone> suggested) {
        HttpStatus httpStatus = "BLOCK".equals(zone.getViolationAction())
                ? HttpStatus.CONFLICT
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return new StorageRuleViolationException(
                httpStatus,
                "ZONE_VIOLATION",
                RuleType.ZONE,
                zone.getViolationAction(),
                "Product category is prohibited in zone: " + zone.getName(),
                ZoneSummary.of(zone),
                null,
                List.of(),
                null,
                suggested.stream().map(ZoneSummary::of).toList());
    }

    /**
     * REQUIRED_ZONE: product's category requires a specific zone type that does
     * not contain the target location. Always HTTP 422 (WARN).
     */
    public static StorageRuleViolationException requiredZoneWarn(
            ZoneType requiredType,
            List<GisZone> suggestedZones) {
        return new StorageRuleViolationException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "REQUIRED_ZONE_VIOLATION",
                RuleType.REQUIRED_ZONE,
                "WARN",
                "Product category requires zone type: " + requiredType.getCode(),
                null,
                null,
                List.of(),
                ZoneTypeSummary.of(requiredType),
                suggestedZones.stream().map(ZoneSummary::of).toList());
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public String getViolationAction() {
        return violationAction;
    }

    public ZoneSummary getViolatedZone() {
        return violatedZone;
    }

    public HazardBufferSummary getViolatedBuffer() {
        return violatedBuffer;
    }

    public List<HazardTypeSummary> getRestrictedHazardTypes() {
        return restrictedHazardTypes;
    }

    public ZoneTypeSummary getRequiredZoneType() {
        return requiredZoneType;
    }

    public List<ZoneSummary> getSuggestedZones() {
        return suggestedZones;
    }
}
