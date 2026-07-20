package com.banula.tariffmanager.config;

import com.banula.tariffmanager.tasks.TariffSyncTask;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@EnableScheduling
@AllArgsConstructor
public class SchedulerConfig {

    private final TariffSyncTask tariffSyncTask;
    private final ApplicationConfiguration applicationConfiguration;

    @Scheduled(fixedRateString = "${tariff-sync.interval:3600000}")
    public void scheduleTariffSync() {
        if (!Boolean.TRUE.equals(applicationConfiguration.getTariffSyncEnabled())) {
            log.debug("Tariff sync is disabled");
            return;
        }

        log.debug("Executing scheduled tariff sync");
        try {
            tariffSyncTask.run();
        } catch (Exception e) {
            log.error("Error executing scheduled tariff sync: {}", e.getMessage(), e);
        }
    }
}
