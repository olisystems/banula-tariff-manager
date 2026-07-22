package com.banula.tariffmanager.config;

import com.banula.openlib.ocpi.util.InfoUtils;
import com.banula.tariffmanager.service.HubClientInfoService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.TimeZone;

@Component
@AllArgsConstructor
@Slf4j
public class StartupApplicationListener implements ApplicationListener<ApplicationReadyEvent> {
    private final ApplicationConfiguration applicationConfiguration;
    private final HubClientInfoService hubClientInfoService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            log.info("Changed default time zone to  {} ", TimeZone.getDefault().getDisplayName());
            log.info("Open library version: {}", InfoUtils.getLibVersion("com.my-oli", "banula-open-library"));
            log.info("My OCPI endpoints: {}/api/v1/internal/ocpi/2.2.1/* | port: {}",
                    applicationConfiguration.getBackendUrl(),
                    event.getApplicationContext().getEnvironment().getProperty("server.port"));
            log.info("Health endpoint: {}/health | port: {}",
                    applicationConfiguration.getBackendUrl(),
                    event.getApplicationContext().getEnvironment().getProperty("server.port"));

            log.info("Sync of hubclientinfo from OCN Node started...");
            hubClientInfoService.syncAllHubClientInfoParties();
            log.info("Completed sync of hubclientinfo");
        } catch (Exception ex) {
            log.error(String.format("Error on application startup: %s", ex.getLocalizedMessage()));
            for (StackTraceElement ste : ex.getStackTrace()) {
                log.error(ste.toString());
            }
        }
    }
}
