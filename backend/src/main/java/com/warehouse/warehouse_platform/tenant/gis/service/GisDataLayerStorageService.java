package com.warehouse.warehouse_platform.tenant.gis.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class GisDataLayerStorageService {

    private static final Path BASE_DIR = Paths.get("data", "datalayer");

    private Path tenantDir(String tenantSlug) {
        return BASE_DIR.resolve(tenantSlug);
    }

    /**
     * Stores the uploaded image file and returns the stored file name.
     * The file name is prefixed with a UUID to avoid collisions.
     */
    public String store(String tenantSlug, MultipartFile file) {
        String original = file.getOriginalFilename();
        String safeName = (original != null ? original.replaceAll("[^a-zA-Z0-9._\\-]", "_") : "layer.tif");
        String fileName = UUID.randomUUID() + "_" + safeName;
        Path dest = tenantDir(tenantSlug).resolve(fileName);
        try {
            Files.createDirectories(dest.getParent());
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store GeoTIFF for tenant: " + tenantSlug, e);
        }
        return fileName;
    }

    /**
     * Deletes the stored image file. Silently ignores missing files.
     */
    public void delete(String tenantSlug, String fileName) {
        Path file = tenantDir(tenantSlug).resolve(fileName);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete GeoTIFF for tenant: " + tenantSlug, e);
        }
    }

    /**
     * Returns the absolute path to the stored file.
     */
    public Path resolveAbsolutePath(String tenantSlug, String fileName) {
        return tenantDir(tenantSlug).resolve(fileName).toAbsolutePath();
    }
}
