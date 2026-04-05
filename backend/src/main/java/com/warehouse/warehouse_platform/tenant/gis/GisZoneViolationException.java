package com.warehouse.warehouse_platform.tenant.gis;

import com.warehouse.warehouse_platform.tenant.gis.model.GisBlock;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

public class GisZoneViolationException extends RuntimeException {

    public record ZoneSummary(UUID id, String label, String zoneType) {
    }

    private final HttpStatus status;
    private final String code;
    private final ZoneSummary violatedZone;
    private final List<ZoneSummary> suggestedZones;

    private GisZoneViolationException(
            HttpStatus status,
            String code,
            String message,
            ZoneSummary violatedZone,
            List<ZoneSummary> suggestedZones) {
        super(message);
        this.status = status;
        this.code = code;
        this.violatedZone = violatedZone;
        this.suggestedZones = suggestedZones;
    }

    public static GisZoneViolationException categoryNotAllowed(
            GisBlock zone,
            List<GisBlock> suggestedZones) {
        return new GisZoneViolationException(
                HttpStatus.CONFLICT,
                "ZONE_VIOLATION",
                "Product category is not allowed in this zone",
                new ZoneSummary(zone.getId(), zone.getLabel(), zone.getZoneType()),
                suggestedZones.stream()
                        .map(z -> new ZoneSummary(z.getId(), z.getLabel(), z.getZoneType()))
                        .toList());
    }

    public static GisZoneViolationException bufferZoneViolation(String bufferLabel, String materialType) {
        return new GisZoneViolationException(
                HttpStatus.FORBIDDEN,
                "BUFFER_ZONE_VIOLATION",
                "Location is within a restricted buffer zone for material: " + materialType
                        + (bufferLabel != null ? " (" + bufferLabel + ")" : ""),
                null,
                List.of());
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public ZoneSummary getViolatedZone() {
        return violatedZone;
    }

    public List<ZoneSummary> getSuggestedZones() {
        return suggestedZones;
    }
}
