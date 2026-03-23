package com.warehouse.warehouse_platform.tenant.gis.config;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "warehouse.gis")
@Validated
@Data
public class WarehouseGisProperties {
    private double anchorLat = 0.0;
    private double anchorLon = 0.0;
    @Positive private double widthMeters = 100.0;
    @Positive private double lengthMeters = 60.0;
}
