package com.banula.tariffmanager.config;

import lombok.AllArgsConstructor;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.banula.openlib.ocn.client.OcnClient;
import com.banula.openlib.ocn.client.OcnClientBuilder;

@Configuration
@AllArgsConstructor
public class OcnClientConfig {
    private final MongoCollectionMapper mongoCollectionMapper;
    private final MongoTemplate mongoTemplate;
    private final ApplicationConfiguration applicationConfiguration;

    @Bean
    public OcnClient myOcnClientConfig() {
        String backendUrl = applicationConfiguration.getBackendUrl() + applicationConfiguration.getApiPrefix()
                + "/2.2.1/versions";
        return new OcnClientBuilder()
                .setFrom(applicationConfiguration.getCountryCode(), applicationConfiguration.getPartyId())
                .setTo(applicationConfiguration.getCountryCode(), applicationConfiguration.getPartyId())
                .setNodeUrl(applicationConfiguration.getPlatformUrl())
                .setOcpiRoles(List.of(applicationConfiguration.getRole()))
                .setPartyBackendUrl(backendUrl)
                .build();
    }
}
