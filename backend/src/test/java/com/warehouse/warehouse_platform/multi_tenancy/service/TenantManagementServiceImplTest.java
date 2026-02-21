package com.warehouse.warehouse_platform.multi_tenancy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;

import com.warehouse.warehouse_platform.multi_tenancy.domain.entity.Tenant;
import com.warehouse.warehouse_platform.multi_tenancy.repository.TenantRepository;

@ExtendWith(MockitoExtension.class)
class TenantManagementServiceImplTest {

    @Mock
    private TenantRepository tenantRepository;

    @Test
    void getTenants_shouldReturnSortedTenantSummaries() {
        Tenant first = Tenant.builder().tenantId("beta").schema("beta_schema").build();
        Tenant second = Tenant.builder().tenantId("acme").schema("acme_schema").build();

        when(tenantRepository.findAll()).thenReturn(List.of(first, second));

        TenantManagementServiceImpl service = createService();

        List<TenantSummary> result = service.getTenants();

        assertEquals(2, result.size());
        assertEquals("acme", result.get(0).tenantId());
        assertEquals("acme_schema", result.get(0).schema());
        assertEquals("beta", result.get(1).tenantId());
        assertEquals("beta_schema", result.get(1).schema());
    }

    private TenantManagementServiceImpl createService() {
        DataSource dataSource = mock(DataSource.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        LiquibaseProperties liquibaseProperties = new LiquibaseProperties();
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        return new TenantManagementServiceImpl(
                dataSource,
                jdbcTemplate,
                liquibaseProperties,
                resourceLoader,
                tenantRepository);
    }
}
