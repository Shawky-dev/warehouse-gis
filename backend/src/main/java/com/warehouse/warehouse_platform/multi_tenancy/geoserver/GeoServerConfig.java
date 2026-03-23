package com.warehouse.warehouse_platform.multi_tenancy.geoserver;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(GeoServerProperties.class)
public class GeoServerConfig {

    @Bean
    RestTemplate geoServerRestTemplate(GeoServerProperties props) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            String credentials = props.adminUser() + ":" + props.adminPassword();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
            MediaType contentType = request.getHeaders().getContentType();
            if (contentType == null || MediaType.TEXT_PLAIN.isCompatibleWith(contentType)) {
                request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            }
            return execution.execute(request, body);
        });
        return restTemplate;
    }
}
