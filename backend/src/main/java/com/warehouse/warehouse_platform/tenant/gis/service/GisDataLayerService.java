package com.warehouse.warehouse_platform.tenant.gis.service;

import com.warehouse.warehouse_platform.tenant.gis.GisException;
import com.warehouse.warehouse_platform.tenant.gis.model.GisDataLayer;
import com.warehouse.warehouse_platform.tenant.gis.repository.GisDataLayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class GisDataLayerService {

    private static final Logger log = LoggerFactory.getLogger(GisDataLayerService.class);

    private final GisDataLayerRepository repository;
    private final GisDataLayerStorageService storageService;

    public GisDataLayerService(
            GisDataLayerRepository repository,
            GisDataLayerStorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }

    // ── read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DataLayerSummary> listAll() {
        return repository.findAllByOrderByNameAscIdAsc()
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ImageData serveImage(UUID id, String tenantSlug) {
        GisDataLayer entity = repository.findById(id)
                .orElseThrow(() -> GisException.notFound("Data layer not found: " + id));
        var path = storageService.resolveAbsolutePath(tenantSlug, entity.getFileName());
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read data layer image", e);
        }
        MediaType mediaType = guessMediaType(entity.getFileName());
        return new ImageData(bytes, mediaType);
    }

    // ── write ─────────────────────────────────────────────────────────────────

    @Transactional
    public DataLayerSummary upload(String tenantSlug, String name, MultipartFile file) {
        if (name == null || name.isBlank()) {
            throw GisException.badRequest("Display name must not be blank");
        }

        assertValidImage(file);

        String fileName = storageService.store(tenantSlug, file);

        GisDataLayer entity = GisDataLayer.builder()
                .name(name.trim())
                .fileName(fileName)
                .build();
        entity = repository.save(entity);
        log.info("Data layer uploaded: {} ({})", entity.getName(), fileName);
        return toSummary(entity);
    }

    @Transactional
    public DataLayerSummary rename(UUID id, String newName) {
        if (newName == null || newName.isBlank()) {
            throw GisException.badRequest("Name must not be blank");
        }
        GisDataLayer entity = repository.findById(id)
                .orElseThrow(() -> GisException.notFound("Data layer not found: " + id));
        entity.setName(newName.trim());
        return toSummary(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id, String tenantSlug) {
        GisDataLayer entity = repository.findById(id)
                .orElseThrow(() -> GisException.notFound("Data layer not found: " + id));

        repository.delete(entity);

        try {
            storageService.delete(tenantSlug, entity.getFileName());
        } catch (Exception e) {
            log.warn("Image file deletion failed (non-fatal): {}", e.getMessage());
        }
    }

    // ── validation ────────────────────────────────────────────────────────────

    private static void assertValidImage(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw GisException.badRequest("Uploaded file has no name.");
        }
        String lower = originalFilename.toLowerCase();
        if (!lower.endsWith(".png") && !lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) {
            throw GisException.badRequest(
                    "Unsupported file type. Please upload a PNG or JPEG image exported from ArcGIS Pro.");
        }
        byte[] header;
        try (var is = file.getInputStream()) {
            header = is.readNBytes(4);
        } catch (IOException e) {
            throw GisException.badRequest("Cannot read uploaded file: " + e.getMessage());
        }
        if (header.length < 3) {
            throw GisException.badRequest("Uploaded file is too small to be a valid image.");
        }
        boolean isPng  = header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E;
        boolean isJpeg = header[0] == (byte) 0xFF && header[1] == (byte) 0xD8;
        if (!isPng && !isJpeg) {
            throw GisException.badRequest(
                    "Uploaded file does not appear to be a valid PNG or JPEG.");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static MediaType guessMediaType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        return MediaType.IMAGE_PNG;
    }

    // ── mapping ───────────────────────────────────────────────────────────────

    private DataLayerSummary toSummary(GisDataLayer entity) {
        return new DataLayerSummary(
                entity.getId(),
                entity.getName(),
                entity.getFileName(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    // ── result types ──────────────────────────────────────────────────────────

    public record DataLayerSummary(
            UUID id,
            String name,
            String fileName,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ImageData(byte[] bytes, MediaType mediaType) {}
}
