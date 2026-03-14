package com.warehouse.warehouse_platform.tenant.receipt;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ReceiptDocumentRepository
        extends JpaRepository<ReceiptDocument, UUID>, JpaSpecificationExecutor<ReceiptDocument> {

    Page<ReceiptDocument> findByStatus(ReceiptStatus status, Pageable pageable);
}
