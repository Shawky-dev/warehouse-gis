package com.warehouse.warehouse_platform.multi_tenancy.config.tenant.liquibase;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import com.warehouse.warehouse_platform.multi_tenancy.domain.entity.Tenant;
import com.warehouse.warehouse_platform.multi_tenancy.repository.TenantRepository;

import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;

public class DynamicSchemaBasedMultiTenantSpringLiquibase implements InitializingBean, ResourceLoaderAware {

    private static final Logger log = LoggerFactory.getLogger(DynamicSchemaBasedMultiTenantSpringLiquibase.class);

    private final TenantRepository tenantRepository;
    private final DataSource dataSource;
    private final LiquibaseProperties liquibaseProperties;

    private ResourceLoader resourceLoader;

    public DynamicSchemaBasedMultiTenantSpringLiquibase(
            TenantRepository tenantRepository,
            DataSource dataSource,
            @Qualifier("tenantLiquibaseProperties") LiquibaseProperties liquibaseProperties) {
        this.tenantRepository = tenantRepository;
        this.dataSource = dataSource;
        this.liquibaseProperties = liquibaseProperties;
    }

    @Override
    public void afterPropertiesSet() {
        log.info("Schema based multitenancy enabled");
        try {
            runOnAllSchemas(dataSource, tenantRepository.findAll());
        } catch (LiquibaseException exception) {
            throw new IllegalStateException("Failed running Liquibase on tenant schemas", exception);
        }
    }

    protected void runOnAllSchemas(DataSource dataSource, Collection<Tenant> tenants) throws LiquibaseException {
        for (Tenant tenant : tenants) {
            log.info("Initializing Liquibase for tenant {}", tenant.getTenantId());
            SpringLiquibase liquibase = getSpringLiquibase(dataSource, tenant.getSchema());
            liquibase.afterPropertiesSet();
            log.info("Liquibase ran for tenant {}", tenant.getTenantId());
        }
    }

    protected SpringLiquibase getSpringLiquibase(DataSource dataSource, String schema) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setResourceLoader(getResourceLoader());
        liquibase.setDataSource(schemaAwareDataSource(dataSource, schema));
        liquibase.setChangeLog(liquibaseProperties.getChangeLog());
        liquibase.setContexts(joinContexts());
        liquibase.setLiquibaseSchema(liquibaseProperties.getLiquibaseSchema());
        liquibase.setLiquibaseTablespace(liquibaseProperties.getLiquibaseTablespace());
        liquibase.setDatabaseChangeLogTable(liquibaseProperties.getDatabaseChangeLogTable());
        liquibase.setDatabaseChangeLogLockTable(liquibaseProperties.getDatabaseChangeLogLockTable());
        liquibase.setDropFirst(liquibaseProperties.isDropFirst());
        liquibase.setShouldRun(liquibaseProperties.isEnabled());
        liquibase.setChangeLogParameters(liquibaseProperties.getParameters());
        liquibase.setRollbackFile(liquibaseProperties.getRollbackFile());
        liquibase.setTestRollbackOnUpdate(liquibaseProperties.isTestRollbackOnUpdate());
        liquibase.setTag(liquibaseProperties.getTag());
        return liquibase;
    }

    private static DataSource schemaAwareDataSource(DataSource delegate, String schema) {
        return new DelegatingDataSource(delegate) {
            @Override
            public Connection getConnection() throws SQLException {
                Connection c = delegate.getConnection();
                try (Statement s = c.createStatement()) {
                    s.execute("SET search_path TO " + schema + ", public");
                }
                return c;
            }
        };
    }

    private String joinContexts() {
        if (liquibaseProperties.getContexts() == null || liquibaseProperties.getContexts().isEmpty()) {
            return null;
        }
        return String.join(",", liquibaseProperties.getContexts());
    }

    public ResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}
