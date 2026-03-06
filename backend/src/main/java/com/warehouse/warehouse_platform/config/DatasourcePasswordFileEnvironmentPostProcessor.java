package com.warehouse.warehouse_platform.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Supports local runs with Docker-style secret file env vars.
 * If spring.datasource.password is not explicitly set and a datasource password file is provided,
 * this processor reads that file and injects spring.datasource.password before auto-configuration.
 */
public class DatasourcePasswordFileEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String DATASOURCE_PASSWORD_KEY = "spring.datasource.password";
    private static final String DATASOURCE_PASSWORD_FILE_KEY = "spring.datasource.password-file";
    private static final String DATASOURCE_PASSWORD_FILE_ENV_KEY = "SPRING_DATASOURCE_PASSWORD_FILE";
    private static final String POSTGRES_PASSWORD_FILE_ENV_KEY = "POSTGRES_PASSWORD_FILE";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (StringUtils.hasText(environment.getProperty(DATASOURCE_PASSWORD_KEY))) {
            return;
        }

        String passwordFile = environment.getProperty(DATASOURCE_PASSWORD_FILE_KEY);
        if (!StringUtils.hasText(passwordFile)) {
            passwordFile = environment.getProperty(DATASOURCE_PASSWORD_FILE_ENV_KEY);
        }
        if (!StringUtils.hasText(passwordFile)) {
            passwordFile = environment.getProperty(POSTGRES_PASSWORD_FILE_ENV_KEY);
        }
        if (!StringUtils.hasText(passwordFile)) {
            return;
        }

        Path passwordFilePath = Path.of(passwordFile);
        if (!Files.isRegularFile(passwordFilePath) || !Files.isReadable(passwordFilePath)) {
            throw new IllegalStateException("Datasource password file is not readable: " + passwordFilePath);
        }

        String password = readPassword(passwordFilePath);
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException("Datasource password file is empty: " + passwordFilePath);
        }

        MapPropertySource propertySource = new MapPropertySource(
                "datasourcePasswordFromFile",
                Map.of(DATASOURCE_PASSWORD_KEY, password));
        environment.getPropertySources().addFirst(propertySource);
    }

    private String readPassword(Path passwordFilePath) {
        try {
            return Files.readString(passwordFilePath, StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read datasource password file: " + passwordFilePath, exception);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
