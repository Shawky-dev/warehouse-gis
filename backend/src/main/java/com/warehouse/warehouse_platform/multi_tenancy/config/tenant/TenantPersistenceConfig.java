package com.warehouse.warehouse_platform.multi_tenancy.config.tenant;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.hibernate5.SpringBeanContainer;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import com.warehouse.warehouse_platform.auth.session.RefreshTokenSessionRepository;
import com.warehouse.warehouse_platform.tenant.audit.AuditLogRepository;
import com.warehouse.warehouse_platform.tenant.category.ProductCategoryRepository;
import com.warehouse.warehouse_platform.tenant.product.ProductRepository;
import com.warehouse.warehouse_platform.tenant.product.ProductSupplierRepository;
import com.warehouse.warehouse_platform.tenant.supplier.SupplierRepository;
import com.warehouse.warehouse_platform.tenant.uom.UnitOfMeasureRepository;
import com.warehouse.warehouse_platform.user.UserRepository;

import jakarta.persistence.EntityManagerFactory;

@Configuration
@EnableJpaRepositories(
        basePackageClasses = {
                UserRepository.class,
                RefreshTokenSessionRepository.class,
                UnitOfMeasureRepository.class,
                SupplierRepository.class,
                ProductRepository.class,
                ProductSupplierRepository.class,
                ProductCategoryRepository.class,
                AuditLogRepository.class
        },
        entityManagerFactoryRef = "tenantEntityManagerFactory",
        transactionManagerRef = "tenantTransactionManager")
@EnableConfigurationProperties(JpaProperties.class)
public class TenantPersistenceConfig {

    private final ConfigurableListableBeanFactory beanFactory;
    private final JpaProperties jpaProperties;
    private final String[] entityPackages;

    public TenantPersistenceConfig(
            ConfigurableListableBeanFactory beanFactory,
            JpaProperties jpaProperties,
            @Value("${multitenancy.tenant.entityManager.packages}") String[] entityPackages) {
        this.beanFactory = beanFactory;
        this.jpaProperties = jpaProperties;
        this.entityPackages = entityPackages;
    }

    @Primary
    @Bean
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(
            @Qualifier("schemaBasedMultiTenantConnectionProvider") MultiTenantConnectionProvider<?> connectionProvider,
            @Qualifier("currentTenantIdentifierResolver") CurrentTenantIdentifierResolver<?> tenantResolver) {
        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setPersistenceUnitName("tenant-persistence-unit");
        emfBean.setPackagesToScan(entityPackages);

        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        emfBean.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> properties = new HashMap<>(this.jpaProperties.getProperties());
        properties.put(AvailableSettings.PHYSICAL_NAMING_STRATEGY,
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        properties.put(AvailableSettings.IMPLICIT_NAMING_STRATEGY,
                "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");
        properties.put(AvailableSettings.BEAN_CONTAINER, new SpringBeanContainer(this.beanFactory));
        properties.remove(AvailableSettings.DEFAULT_SCHEMA);
        properties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        emfBean.setJpaPropertyMap(properties);

        return emfBean;
    }

    @Primary
    @Bean
    public JpaTransactionManager tenantTransactionManager(
            @Qualifier("tenantEntityManagerFactory") EntityManagerFactory emf) {
        JpaTransactionManager tenantTransactionManager = new JpaTransactionManager();
        tenantTransactionManager.setEntityManagerFactory(emf);
        return tenantTransactionManager;
    }
}
