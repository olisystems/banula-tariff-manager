package com.banula.tariffmanager.service;

import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.banula.tariffmanager.model.OnChainTariff;

import java.time.LocalDateTime;
import java.util.List;

public interface TMTariffService {
    TariffDTO getTariff(String countryCode, String partyId, String tariffId);

    OnChainTariff saveTariff(TariffDTO tariffDTO);

    OnChainTariff getLatestTariffForPartyAt(String countryCode, String partyId, LocalDateTime datetime);

    OnChainTariff getTariffByHash(String hash);

    String hashTariff(TariffDTO tariffDTO);

    List<TariffDTO> findTariffsBetweenDates(LocalDateTime dateFrom, LocalDateTime dateTo, Integer offset,
            Integer limit);
}
