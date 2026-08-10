package com.banula.tariffmanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.banula.openlib.ocpi.model.enums.Role;
import com.banula.openlib.ocpi.model.enums.VersionNumber;
import com.banula.openlib.ocpi.platform.PlatformConfiguration;

import lombok.Data;

@Configuration
@EnableConfigurationProperties
@Data
public class ApplicationConfiguration implements PlatformConfiguration {

    @Value("${api.url}")
    private String backendUrl;

    @Value("${api.role}")
    private Role ocpiRole;

    @Value("${api.ocpi-version}")
    private String ocpiVersion;

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

    @Value("${platform.url}")
    private String platformUrl;

    @Override
    public VersionNumber getOcpiVersion() {
        return VersionNumber.fromValue(ocpiVersion);
    }

    public boolean isToLogCurlCommands() {
        return logCurlCommand;
    }

}

