package com.banula.tariffmanager.service;

import com.banula.tariffmanager.model.dto.HubClientInfoDTO;

import java.time.LocalDateTime;

public interface TariffSyncService {

    void welcomeParty(HubClientInfoDTO party);

    void syncRecentTariffs();

    void pullStoreAndBroadcast(String countryCode, String partyId, LocalDateTime dateFrom, LocalDateTime dateTo);
}
