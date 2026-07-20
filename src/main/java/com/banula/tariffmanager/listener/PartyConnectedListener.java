package com.banula.tariffmanager.listener;

import com.banula.tariffmanager.config.ApplicationConfiguration;
import com.banula.tariffmanager.event.PartyConnectedEvent;
import com.banula.tariffmanager.service.TariffSyncService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
public class PartyConnectedListener {

    private final TariffSyncService tariffSyncService;
    private final ApplicationConfiguration applicationConfiguration;

    @Async
    @EventListener
    public void onPartyConnected(PartyConnectedEvent event) {
        if (!Boolean.TRUE.equals(applicationConfiguration.getTariffSyncEnabled())) {
            return;
        }
        try {
            tariffSyncService.welcomeParty(event.getParty());
        } catch (Exception e) {
            log.warn("Welcome ceremony failed for {}/{}: {}", event.getParty().getCountryCode(),
                    event.getParty().getPartyId(), e.getMessage(), e);
        }
    }
}
