package com.warehouse.warehouse_platform.multi_tenancy.geoserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class GeoServerClient {

    private static final Logger log = LoggerFactory.getLogger(GeoServerClient.class);

    private final RestTemplate geoServerRestTemplate;
    private final GeoServerProperties props;

    public GeoServerClient(
            @Qualifier("geoServerRestTemplate") RestTemplate geoServerRestTemplate,
            GeoServerProperties props) {
        this.geoServerRestTemplate = geoServerRestTemplate;
        this.props = props;
    }

    public void provisionTenant(String tenantId, String schema) {
        String workspaceName = "wh_" + tenantId;
        String datastoreName = workspaceName + "_store";
        createWorkspace(workspaceName);
        createPostGISDataStore(workspaceName, datastoreName, schema);
    }

    private void createWorkspace(String workspaceName) {
        try {
            String url = props.url() + "/rest/workspaces";
            String body = """
                    { "workspace": { "name": "%s" } }
                    """.formatted(workspaceName);
            geoServerRestTemplate.postForEntity(url, body, Void.class);
            log.info("GeoServer workspace created: {}", workspaceName);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatusCode.valueOf(409)) {
                log.debug("GeoServer workspace already exists, skipping: {}", workspaceName);
            } else {
                throw e;
            }
        }
    }

    private void createPostGISDataStore(String workspaceName, String datastoreName, String schema) {
        try {
            String url = props.url() + "/rest/workspaces/" + workspaceName + "/datastores";
            geoServerRestTemplate.postForEntity(url, buildDataStoreJson(datastoreName, schema), Void.class);
            log.info("GeoServer PostGIS datastore created: {}/{}", workspaceName, datastoreName);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatusCode.valueOf(409)) {
                log.debug("GeoServer datastore already exists, skipping: {}/{}", workspaceName, datastoreName);
            } else {
                throw e;
            }
        }
    }

    private String buildDataStoreJson(String datastoreName, String schema) {
        return """
                {
                  "dataStore": {
                    "name": "%s",
                    "type": "PostGIS",
                    "connectionParameters": {
                      "entry": [
                        { "@key": "dbtype",   "$": "postgis" },
                        { "@key": "host",     "$": "%s" },
                        { "@key": "port",     "$": "%d" },
                        { "@key": "database", "$": "%s" },
                        { "@key": "schema",   "$": "%s" },
                        { "@key": "user",     "$": "%s" },
                        { "@key": "passwd",   "$": "%s" }
                      ]
                    }
                  }
                }
                """.formatted(
                datastoreName,
                props.dbHost(),
                props.dbPort(),
                props.dbName(),
                schema,
                props.dbUser(),
                props.dbPassword()
        );
    }
}
