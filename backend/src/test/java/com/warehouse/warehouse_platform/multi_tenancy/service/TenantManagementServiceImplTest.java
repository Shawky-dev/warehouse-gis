package com.warehouse.warehouse_platform.multi_tenancy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.warehouse.warehouse_platform.multi_tenancy.domain.entity.Tenant;
import com.warehouse.warehouse_platform.multi_tenancy.repository.TenantRepository;

import liquibase.integration.spring.SpringLiquibase;

@ExtendWith(MockitoExtension.class)
class TenantManagementServiceImplTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void createTenant_shouldSeedTenantAdminInTenantSchema() {
        when(tenantRepository.findByTenantId("acme")).thenReturn(Optional.empty());
        when(tenantRepository.findBySchema("acme")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("admin1234")).thenReturn("encoded-admin-password");

        TenantManagementServiceImpl service = createService();

        service.createTenant("acme", "acme", "admin@acme.local", "admin1234");

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertEquals("acme", tenantCaptor.getValue().getTenantId());
        assertEquals("acme", tenantCaptor.getValue().getSchema());

        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.eq("INSERT INTO acme.users (id, email, password, role) VALUES (?, ?, ?, ?)"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("admin@acme.local"),
                org.mockito.ArgumentMatchers.eq("encoded-admin-password"),
                org.mockito.ArgumentMatchers.eq("ADMIN"));
    }

    private TenantManagementServiceImpl createService() {
        DataSource dataSource = mock(DataSource.class);
        LiquibaseProperties liquibaseProperties = new LiquibaseProperties();
        liquibaseProperties.setEnabled(true);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        return new TenantManagementServiceImpl(
                dataSource,
                jdbcTemplate,
                liquibaseProperties,
                resourceLoader,
                tenantRepository,
                passwordEncoder) {
            @Override
            protected SpringLiquibase getSpringLiquibase(DataSource ds, String schema) {
                return mock(SpringLiquibase.class);
            }
        };
    }
}
