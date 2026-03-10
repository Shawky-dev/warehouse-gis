package com.warehouse.warehouse_platform.tenant.warehouse.block;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface BlockTemplateRepository
        extends JpaRepository<BlockTemplate, UUID>, JpaSpecificationExecutor<BlockTemplate> {

    Optional<BlockTemplate> findByNameIgnoreCase(String name);
}
