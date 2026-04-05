package com.warehouse.warehouse_platform.tenant.gis;

import com.warehouse.warehouse_platform.tenant.gis.model.GisZone;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

public class GisZoneViolationException extends RuntimeException {

    public record ZoneSummary(UUID id, String name, String violationAction) {
    }

    private final HttpStatus status;
    private final String code;
    private final String violationAction;
    private final ZoneSummary violatedZone;
    private final List<ZoneSummary> suggestedZones;

    private GisZoneViolationException(
            HttpStatus status,
            String code,
            String violationAction,
            String message,
            ZoneSummary violatedZone,
            List<ZoneSummary> suggestedZones) {
        super(message);
        this.status = status;
        this.code = code;
        this.violationAction = violationAction;
        this.violatedZone = violatedZone;
        this.suggestedZones = suggestedZones;
    }

    public static GisZoneViolationException categoryProhibited(
            GisZone zone,
            List<GisZone> suggestedZones) {
        return new GisZoneViolationException(
                HttpStatus.CONFLICT,
                "ZONE_VIOLATION",
                zone.getViolationAction(),
                "Product category is prohibited in zone: " + zone.getName(),
                new ZoneSummary(zone.getId(), zone.getName(), zone.getViolationAction()),
                suggestedZones.stream()
                        .map(z -> new ZoneSummary(z.getId(), z.getName(), z.getViolationAction()))
                        .toList());
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getViolationAction() {
        return violationAction;
    }

    public ZoneSummary getViolatedZone() {
        return violatedZone;
    }

    public List<ZoneSummary> getSuggestedZones() {
        return suggestedZones;
    }
}
