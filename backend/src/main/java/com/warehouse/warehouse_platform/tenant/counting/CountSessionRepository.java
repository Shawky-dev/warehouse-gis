package com.warehouse.warehouse_platform.tenant.counting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface CountSessionRepository
        extends JpaRepository<CountSession, UUID>, JpaSpecificationExecutor<CountSession> {

    Page<CountSession> findByStatus(CountStatus status, Pageable pageable);
}