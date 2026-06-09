package com.banula.tariffmanager.config;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.banula.openlib.ocn.model.OcnVersionDetails;
import com.banula.openlib.ocpi.model.enums.Role;
import com.banula.openlib.ocpi.platform.PlatformConfiguration;

@Configuration
@EnableConfigurationProperties
@Getter
@Setter
public class ApplicationConfiguration implements PlatformConfiguration {
    @Value("${api.url}")
    private String backendUrl;

    @Value("${api.role}")
    private Role role;

    // Hashing service
    @Value("${hashing-service.url}")
    private String HashingServiceUrl;

    @Value("${api.collection-prefix}")
    private String collectionPrefix;

    @Value("${api.log-curl-command}")
    private boolean logCurlCommand;

    @Value("${feature-flags.require-energy-mix:false}")
    private boolean requireEnergyMix;

    @Value("${feature-flags.energy-product-name:BANULA_CPO_TARIFF}")
    private String energyProductName;

    public boolean isToLogCurlCommands() {
        return logCurlCommand;
    }

    @Value("${platform.url}")
    private String platformUrl;

    @Value("${platform.party-id}")
    private String partyId;

    @Value("${platform.country-code}")
    private String countryCode;

    private HashMap<String, OcnVersionDetails> ocnVersionDetails;

    @Override
    public void setOcnVersionDetails(String tenantId, OcnVersionDetails _ocnVersionDetails) {
        if (this.ocnVersionDetails == null) {
            this.ocnVersionDetails = new HashMap<String, OcnVersionDetails>();
        }
        this.ocnVersionDetails.put(tenantId, _ocnVersionDetails);
    }

}
