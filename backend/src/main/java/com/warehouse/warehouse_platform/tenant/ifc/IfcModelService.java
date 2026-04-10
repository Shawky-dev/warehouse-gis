package com.warehouse.warehouse_platform.tenant.ifc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IfcModelService {

    private static final Logger log = LoggerFactory.getLogger(IfcModelService.class);

    private final IfcModelRepository repository;
    private final IfcStorageService storageService;

    public IfcModelService(IfcModelRepository repository, IfcStorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public List<ModelSummary> listAll() {
        return repository.findAllByOrderByUploadedAtDesc()
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public FileRef resolveFile(UUID id, String tenantSlug) {
        IfcModel model = repository.findById(id)
                .orElseThrow(() -> new IfcModelNotFoundException("IFC model not found: " + id));
        Path path = storageService.resolveAbsolutePath(tenantSlug, model.getStoredFileName());
        return new FileRef(path, model.getOriginalName());
    }

    @Transactional
    public ModelSummary upload(String tenantSlug, MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("File name must not be blank");
        }
        if (!originalName.toLowerCase().endsWith(".ifc")) {
            throw new IllegalArgumentException("Only .ifc files are accepted");
        }

        String storedFileName = storageService.store(tenantSlug, file);

        IfcModel model = IfcModel.builder()
                .originalName(originalName.trim())
                .storedFileName(storedFileName)
                .build();
        model = repository.save(model);
        log.info("IFC model uploaded: {} for tenant {}", originalName, tenantSlug);
        return toSummary(model);
    }

    @Transactional
    public void delete(UUID id, String tenantSlug) {
        IfcModel model = repository.findById(id)
                .orElseThrow(() -> new IfcModelNotFoundException("IFC model not found: " + id));
        repository.delete(model);
        storageService.delete(tenantSlug, model.getStoredFileName());
        log.info("IFC model deleted: {} for tenant {}", model.getOriginalName(), tenantSlug);
    }

    private ModelSummary toSummary(IfcModel m) {
        return new ModelSummary(m.getId(), m.getOriginalName(), m.getUploadedAt());
    }

    public record ModelSummary(UUID id, String originalName, Instant uploadedAt) {}
    public record FileRef(Path path, String originalName) {}
}
