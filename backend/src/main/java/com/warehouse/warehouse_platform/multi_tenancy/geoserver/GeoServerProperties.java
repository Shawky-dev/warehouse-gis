package com.warehouse.warehouse_platform.multi_tenancy.geoserver;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "geoserver")
public record GeoServerProperties(
        @NotBlank String url,
        @NotBlank String adminUser,
        @NotBlank String adminPassword,
        @NotBlank String dbHost,
        int dbPort,
        @NotBlank String dbName,
        @NotBlank String dbUser,
        @NotBlank String dbPassword
) {
}
