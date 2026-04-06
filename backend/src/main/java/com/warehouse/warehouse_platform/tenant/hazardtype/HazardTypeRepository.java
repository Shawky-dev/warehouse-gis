package com.warehouse.warehouse_platform.tenant.hazardtype;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HazardTypeRepository extends JpaRepository<HazardType, UUID> {

    Optional<HazardType> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByIdNotAndCodeIgnoreCase(UUID id, String code);
}
