package com.warehouse.warehouse_platform.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasourcePasswordFileEnvironmentPostProcessorTest {

    private final DatasourcePasswordFileEnvironmentPostProcessor processor =
            new DatasourcePasswordFileEnvironmentPostProcessor();

    @TempDir
    Path tempDir;

    @Test
    void shouldInjectDatasourcePasswordFromPasswordFile() throws Exception {
        Path passwordFile = tempDir.resolve("postgres_password.txt");
        Files.writeString(passwordFile, "super-secret\n");

        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "SPRING_DATASOURCE_PASSWORD_FILE", passwordFile.toString()
        )));

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertEquals("super-secret", environment.getProperty("spring.datasource.password"));
    }

    @Test
    void shouldInjectDatasourcePasswordFromPostgresPasswordFileFallback() throws Exception {
        Path passwordFile = tempDir.resolve("postgres_password.txt");
        Files.writeString(passwordFile, "super-secret\n");

        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "POSTGRES_PASSWORD_FILE", passwordFile.toString()
        )));

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertEquals("super-secret", environment.getProperty("spring.datasource.password"));
    }

    @Test
    void shouldPreferExplicitDatasourcePasswordOverPasswordFile() throws Exception {
        Path passwordFile = tempDir.resolve("postgres_password.txt");
        Files.writeString(passwordFile, "super-secret\n");

        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.datasource.password", "explicit-password",
                "SPRING_DATASOURCE_PASSWORD_FILE", passwordFile.toString()
        )));

        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertEquals("explicit-password", environment.getProperty("spring.datasource.password"));
    }

    @Test
    void shouldFailWhenPasswordFileDoesNotExist() {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "SPRING_DATASOURCE_PASSWORD_FILE", tempDir.resolve("missing.txt").toString()
        )));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> processor.postProcessEnvironment(environment, new SpringApplication(Object.class)));

        assertTrue(exception.getMessage().contains("Datasource password file is not readable"));
    }
}
