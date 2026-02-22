package com.warehouse.warehouse_platform.multi_tenancy.service;

import java.util.Comparator;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.StatementCallback;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.warehouse.warehouse_platform.multi_tenancy.util.TenantContext;
import com.warehouse.warehouse_platform.multi_tenancy.domain.entity.Tenant;
import com.warehouse.warehouse_platform.multi_tenancy.repository.TenantRepository;
import com.warehouse.warehouse_platform.user.User;
import com.warehouse.warehouse_platform.user.UserRepository;

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
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TenantManagementServiceImpl(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            @Qualifier("tenantLiquibaseProperties") LiquibaseProperties liquibaseProperties,
            ResourceLoader resourceLoader,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.liquibaseProperties = liquibaseProperties;
        this.resourceLoader = resourceLoader;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void createTenant(String tenantId, String schema, String adminEmail, String adminPassword) {
        validateInput(tenantId, schema, adminEmail, adminPassword);

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

        try {
            createTenantAdmin(tenantId, adminEmail, adminPassword);
        } catch (DataAccessException exception) {
            throw new TenantCreationException("Error when creating tenant admin for tenant: " + tenantId, exception);
        }
    }

    @Override
    public List<TenantSummary> getTenants() {
        return tenantRepository.findAll().stream()
                .map(tenant -> new TenantSummary(tenant.getTenantId(), tenant.getSchema()))
                .sorted(Comparator.comparing(TenantSummary::tenantId))
                .toList();
    }

    private void validateInput(String tenantId, String schema, String adminEmail, String adminPassword) {
        if (!StringUtils.hasText(tenantId)) {
            throw new TenantCreationException("tenantId must not be blank");
        }
        if (!StringUtils.hasText(schema) || !schema.matches(VALID_SCHEMA_NAME_REGEXP)) {
            throw new TenantCreationException("Invalid schema name: " + schema);
        }
        if (!StringUtils.hasText(adminEmail)) {
            throw new TenantCreationException("adminEmail must not be blank");
        }
        if (!StringUtils.hasText(adminPassword)) {
            throw new TenantCreationException("adminPassword must not be blank");
        }
    }

    private void createSchema(String schema) {
        jdbcTemplate.execute((StatementCallback<Boolean>) stmt -> stmt.execute("CREATE SCHEMA " + schema));
    }

    private void createTenantAdmin(String tenantId, String adminEmail, String adminPassword) {
        String previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(tenantId);
        try {
            User admin = User.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .role("ADMIN")
                    .build();
            userRepository.save(admin);
        } finally {
            if (previousTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenant);
            }
        }
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
