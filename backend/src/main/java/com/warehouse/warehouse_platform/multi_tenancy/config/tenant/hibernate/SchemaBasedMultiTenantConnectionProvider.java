package com.warehouse.warehouse_platform.multi_tenancy.config.tenant.hibernate;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.warehouse.warehouse_platform.multi_tenancy.domain.entity.Tenant;
import com.warehouse.warehouse_platform.multi_tenancy.repository.TenantRepository;

import jakarta.annotation.PostConstruct;

@Component("schemaBasedMultiTenantConnectionProvider")
public class SchemaBasedMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final Logger log = LoggerFactory.getLogger(SchemaBasedMultiTenantConnectionProvider.class);
    private static final long serialVersionUID = 1L;
    private static final String BOOTSTRAP_TENANT = "BOOTSTRAP";

    private final DataSource dataSource;
    private final TenantRepository tenantRepository;
    private final long maximumSize;
    private final int expireAfterAccess;
    private final String bootstrapSchema;

    private final Map<String, CachedSchema> tenantSchemas = new ConcurrentHashMap<>();
    private volatile long expireAfterAccessMillis;

    public SchemaBasedMultiTenantConnectionProvider(
            DataSource dataSource,
            TenantRepository tenantRepository,
            @Value("${multitenancy.schema-cache.maximumSize:1000}") Long maximumSize,
            @Value("${multitenancy.schema-cache.expireAfterAccess:10}") Integer expireAfterAccess,
            @Value("${multitenancy.bootstrap-schema:public}") String bootstrapSchema) {
        this.dataSource = dataSource;
        this.tenantRepository = tenantRepository;
        this.maximumSize = maximumSize;
        this.expireAfterAccess = expireAfterAccess;
        this.bootstrapSchema = bootstrapSchema;
    }

    @PostConstruct
    void createCache() {
        this.expireAfterAccessMillis = TimeUnit.MINUTES.toMillis(expireAfterAccess);
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        String schema = resolveSchema(tenantIdentifier);
        Connection connection = getAnyConnection();
        connection.setSchema(schema);
        log.debug("Get connection for tenant {} using schema {}", tenantIdentifier, schema);
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        connection.setSchema(bootstrapSchema);
        releaseAnyConnection(connection);
        log.debug("Release connection for tenant {}", tenantIdentifier);
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class unwrapType) {
        return MultiTenantConnectionProvider.class.isAssignableFrom(unwrapType)
                || SchemaBasedMultiTenantConnectionProvider.class.isAssignableFrom(unwrapType);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) {
            return (T) this;
        }
        throw new UnknownUnwrapTypeException(unwrapType);
    }

    private String resolveSchema(String tenantIdentifier) {
        if (tenantIdentifier == null || tenantIdentifier.isBlank() || BOOTSTRAP_TENANT.equalsIgnoreCase(tenantIdentifier)) {
            return bootstrapSchema;
        }

        long now = System.currentTimeMillis();
        evictExpired(now);

        CachedSchema cached = tenantSchemas.compute(tenantIdentifier, (id, existing) -> {
            if (existing != null) {
                existing.touch(now);
                return existing;
            }

            Tenant tenant = tenantRepository.findByTenantId(id)
                    .orElseThrow(() -> new RuntimeException("No such tenant: " + id));
            return new CachedSchema(tenant.getSchema(), now);
        });

        evictOverflow();
        return cached.schema;
    }

    private void evictExpired(long now) {
        if (tenantSchemas.isEmpty()) {
            return;
        }

        long cutoff = now - expireAfterAccessMillis;
        tenantSchemas.entrySet().removeIf(entry -> entry.getValue().lastAccessEpochMillis < cutoff);
    }

    private synchronized void evictOverflow() {
        int overflow = (int) (tenantSchemas.size() - maximumSize);
        if (overflow <= 0) {
            return;
        }

        tenantSchemas.entrySet().stream()
                .sorted(Map.Entry.comparingByValue((a, b) -> Long.compare(a.lastAccessEpochMillis, b.lastAccessEpochMillis)))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(tenantSchemas::remove);
    }

    private static final class CachedSchema {
        private final String schema;
        private volatile long lastAccessEpochMillis;

        private CachedSchema(String schema, long lastAccessEpochMillis) {
            this.schema = schema;
            this.lastAccessEpochMillis = lastAccessEpochMillis;
        }

        private void touch(long now) {
            this.lastAccessEpochMillis = now;
        }
    }
}
