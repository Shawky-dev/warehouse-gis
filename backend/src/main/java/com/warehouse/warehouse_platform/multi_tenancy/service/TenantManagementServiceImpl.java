package com.warehouse.warehouse_platform.multi_tenancy.service;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.StatementCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.warehouse.warehouse_platform.multi_tenancy.domain.entity.Tenant;
import com.warehouse.warehouse_platform.multi_tenancy.repository.TenantRepository;

import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;

@Service
public class TenantManagementServiceImpl implements TenantManagementService {

    private static final String VALID_SCHEMA_NAME_REGEXP = "[A-Za-z0-9_]*";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final LiquibaseProperties liquibaseProperties;
    private final ResourceLoader resourceLoader;
    private final TenantRepository tenantRepository;

    public TenantManagementServiceImpl(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            @Qualifier("tenantLiquibaseProperties") LiquibaseProperties liquibaseProperties,
            ResourceLoader resourceLoader,
            TenantRepository tenantRepository) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.liquibaseProperties = liquibaseProperties;
        this.resourceLoader = resourceLoader;
        this.tenantRepository = tenantRepository;
    }

    @Override
    public void createTenant(String tenantId, String schema) {
        validateInput(tenantId, schema);

        String normalizedSchema = schema.toLowerCase();

        if (tenantRepository.findByTenantId(tenantId).isPresent()) {
            throw new TenantCreationException("Tenant already exists: " + tenantId);
        }
        if (tenantRepository.findBySchema(normalizedSchema).isPresent()) {
            throw new TenantCreationException("Schema already mapped to another tenant: " + schema);
        }

        try {
            createSchema(normalizedSchema);
            runLiquibase(dataSource, normalizedSchema);
        } catch (DataAccessException e) {
            throw new TenantCreationException("Error when creating schema: " + normalizedSchema, e);
        } catch (LiquibaseException e) {
            throw new TenantCreationException("Error when populating schema: " + normalizedSchema, e);
        }

        Tenant tenant = Tenant.builder()
                .tenantId(tenantId)
                .schema(normalizedSchema)
                .build();
        tenantRepository.save(tenant);
    }

    private void validateInput(String tenantId, String schema) {
        if (!StringUtils.hasText(tenantId)) {
            throw new TenantCreationException("tenantId must not be blank");
        }
        if (!StringUtils.hasText(schema) || !schema.matches(VALID_SCHEMA_NAME_REGEXP)) {
            throw new TenantCreationException("Invalid schema name: " + schema);
        }
    }

    private void createSchema(String schema) {
        jdbcTemplate.execute((StatementCallback<Boolean>) stmt -> stmt.execute("CREATE SCHEMA " + schema));
    }

    private void runLiquibase(DataSource dataSource, String schema) throws LiquibaseException {
        SpringLiquibase liquibase = getSpringLiquibase(dataSource, schema);
        liquibase.afterPropertiesSet();
    }

    protected SpringLiquibase getSpringLiquibase(DataSource dataSource, String schema) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setResourceLoader(resourceLoader);
        liquibase.setDataSource(dataSource);
        liquibase.setDefaultSchema(schema);
        liquibase.setChangeLog(liquibaseProperties.getChangeLog());
        liquibase.setContexts(joinContexts());
        liquibase.setDropFirst(liquibaseProperties.isDropFirst());
        liquibase.setShouldRun(liquibaseProperties.isEnabled());
        liquibase.setChangeLogParameters(liquibaseProperties.getParameters());
        return liquibase;
    }

    private String joinContexts() {
        if (liquibaseProperties.getContexts() == null || liquibaseProperties.getContexts().isEmpty()) {
            return null;
        }
        return String.join(",", liquibaseProperties.getContexts());
    }
}
