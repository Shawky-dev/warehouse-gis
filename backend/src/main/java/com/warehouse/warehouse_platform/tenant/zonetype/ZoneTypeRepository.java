package com.warehouse.warehouse_platform.tenant.zonetype;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ZoneTypeRepository extends JpaRepository<ZoneType, UUID> {

    Optional<ZoneType> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByIdNotAndCodeIgnoreCase(UUID id, String code);
}
