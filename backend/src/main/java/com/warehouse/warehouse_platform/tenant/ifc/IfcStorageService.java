package com.warehouse.warehouse_platform.tenant.ifc;

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
public class IfcStorageService {

    private static final Path BASE_DIR = Paths.get("data", "ifc");

    private Path tenantDir(String tenantSlug) {
        return BASE_DIR.resolve(tenantSlug);
    }

    public String store(String tenantSlug, MultipartFile file) {
        String original = file.getOriginalFilename();
        String safeName = (original != null ? original.replaceAll("[^a-zA-Z0-9._\\-]", "_") : "model.ifc");
        String fileName = UUID.randomUUID() + "_" + safeName;
        Path dest = tenantDir(tenantSlug).resolve(fileName);
        try {
            Files.createDirectories(dest.getParent());
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store IFC file for tenant: " + tenantSlug, e);
        }
        return fileName;
    }

    public void delete(String tenantSlug, String fileName) {
        Path file = tenantDir(tenantSlug).resolve(fileName);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete IFC file for tenant: " + tenantSlug, e);
        }
    }

    public Path resolveAbsolutePath(String tenantSlug, String fileName) {
        return tenantDir(tenantSlug).resolve(fileName).toAbsolutePath();
    }
}
