package com.warehouse.warehouse_platform.multi_tenancy.config.tenant.liquibase;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import com.warehouse.warehouse_platform.multi_tenancy.repository.TenantRepository;

@Configuration
@ConditionalOnProperty(name = "multitenancy.tenant.liquibase.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LiquibaseProperties.class)
public class TenantLiquibaseConfig {

    @Bean
    @ConfigurationProperties("multitenancy.tenant.liquibase")
    public LiquibaseProperties tenantLiquibaseProperties() {
        return new LiquibaseProperties();
    }

    @Bean
    @DependsOn("masterLiquibase")
    public DynamicSchemaBasedMultiTenantSpringLiquibase tenantLiquibase(
            TenantRepository tenantRepository,
            DataSource dataSource) {
        return new DynamicSchemaBasedMultiTenantSpringLiquibase(
                tenantRepository,
                dataSource,
                tenantLiquibaseProperties());
    }
}
