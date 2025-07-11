package com.banula.tariffmanager.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.banula.openlib.ocn.model.OcnVersionDetails;
import com.banula.openlib.ocpi.model.enums.Role;
import com.banula.openlib.ocpi.platform.PlatformConfiguration;

@Configuration
@EnableConfigurationProperties
@Getter
public class ApplicationConfiguration implements PlatformConfiguration {
    @Value("${party.url}")
    private String backendUrl;

    @Value("${party.api-prefix}")
    private String apiPrefix;

    @Value("${party.api-non-ocpi-prefix}")
    private String apiNonOcpiPrefix;

    @Value("${party.role}")
    private Role role;

    // Hashing service
    @Value("${hashing-service.url}")
    private String HashingServiceUrl;

    @Value("${party.collection-prefix}")
    private String collectionPrefix;

    @Value("${platform.url}")
    private String platformUrl;

    @Value("${platform.party-id}")
    private String partyId;

    @Value("${platform.country-code}")
    private String countryCode;

    private OcnVersionDetails ocnVersionDetails;

    public void setOcnVersionDetails(OcnVersionDetails ocnVersionDetails) {
        this.ocnVersionDetails = ocnVersionDetails;
    }

}
