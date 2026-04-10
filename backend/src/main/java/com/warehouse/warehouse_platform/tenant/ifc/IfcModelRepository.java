package com.warehouse.warehouse_platform.tenant.ifc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IfcModelRepository extends JpaRepository<IfcModel, UUID> {

    List<IfcModel> findAllByOrderByUploadedAtDesc();
}
