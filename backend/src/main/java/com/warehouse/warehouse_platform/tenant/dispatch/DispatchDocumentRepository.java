package com.warehouse.warehouse_platform.tenant.dispatch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface DispatchDocumentRepository
        extends JpaRepository<DispatchDocument, UUID>, JpaSpecificationExecutor<DispatchDocument> {

    Page<DispatchDocument> findByStatus(DispatchStatus status, Pageable pageable);
}
