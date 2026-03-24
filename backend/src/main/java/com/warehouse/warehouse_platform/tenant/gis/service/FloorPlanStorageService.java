package com.warehouse.warehouse_platform.tenant.gis.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@Service
public class FloorPlanStorageService {

    private static final Path BASE_DIR = Paths.get("data", "floorplans");

    private Path resolveFile(String tenantSlug) {
        return BASE_DIR.resolve(tenantSlug + ".svg");
    }

    public boolean exists(String tenantSlug) {
        return Files.exists(resolveFile(tenantSlug));
    }

    public Optional<Resource> load(String tenantSlug) {
        Path file = resolveFile(tenantSlug);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(file));
    }

    public void store(String tenantSlug, MultipartFile file) {
        try {
            Files.createDirectories(BASE_DIR);
            Files.copy(file.getInputStream(), resolveFile(tenantSlug), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to store floor plan for tenant: " + tenantSlug, exception);
        }
    }

    public void delete(String tenantSlug) {
        try {
            Files.deleteIfExists(resolveFile(tenantSlug));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to delete floor plan for tenant: " + tenantSlug, exception);
        }
    }
}
