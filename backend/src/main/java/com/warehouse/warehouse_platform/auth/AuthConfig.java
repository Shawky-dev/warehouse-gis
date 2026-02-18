package com.warehouse.warehouse_platform.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RefreshCookieProperties.class)
public class AuthConfig {
}
