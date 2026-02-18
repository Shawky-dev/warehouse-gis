package com.warehouse.warehouse_platform.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "security.auth.refresh-cookie")
public record RefreshCookieProperties(
        @NotBlank String name,
        @NotBlank String path,
        @NotBlank String sameSite,
        boolean secure,
        String domain
) {
}
