package com.warehouse.warehouse_platform.tenant.gis.repository;

import com.warehouse.warehouse_platform.tenant.gis.model.GisZoneCategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GisZoneCategoryRuleRepository extends JpaRepository<GisZoneCategoryRule, UUID> {

    List<GisZoneCategoryRule> findByZoneId(UUID zoneId);

    Optional<GisZoneCategoryRule> findByZoneIdAndCategoryId(UUID zoneId, UUID categoryId);

    List<GisZoneCategoryRule> findByCategoryIdAndRuleType(UUID categoryId, String ruleType);
}
