package com.banula.tariffmanager.config;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.TimeZone;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import com.banula.openlib.ocpi.util.InfoUtils;

@Component
@AllArgsConstructor
@Slf4j
public class StartupApplicationListener implements ApplicationListener<ApplicationReadyEvent> {
    private final ApplicationConfiguration applicationConfiguration;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            log.info("Changed default time zone to  {} ", TimeZone.getDefault().getDisplayName());
            log.info("Open library version: {}", InfoUtils.getLibVersion("com.my-oli", "banula-open-library"));
            log.info("My OCPI URL: {}{} | port: {}", applicationConfiguration.getBackendUrl(),
                    applicationConfiguration.getApiPrefix(),
                    event.getApplicationContext().getEnvironment().getProperty("server.port"));
            log.info("My Non-OCPI URL: {}{} | port: {}", applicationConfiguration.getBackendUrl(),
                    applicationConfiguration.getApiNonOcpiPrefix(),
                    event.getApplicationContext().getEnvironment().getProperty("server.port"));
        } catch (Exception ex) {
            log.error(String.format("Error on application startup: %s", ex.getLocalizedMessage()));
            for (StackTraceElement ste : ex.getStackTrace()) {
                log.error(ste.toString());
            }
        }
    }
}
