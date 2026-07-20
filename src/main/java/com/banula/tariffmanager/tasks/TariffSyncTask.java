package com.banula.tariffmanager.tasks;

import com.banula.tariffmanager.service.TariffSyncService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class TariffSyncTask implements Runnable {

    private final TariffSyncService tariffSyncService;

    @Override
    public void run() {
        log.info("Starting scheduled tariff sync");
        try {
            tariffSyncService.syncRecentTariffs();
            log.info("Scheduled tariff sync completed");
        } catch (Exception e) {
            log.error("Scheduled tariff sync failed: {}", e.getMessage(), e);
        }
    }
}
