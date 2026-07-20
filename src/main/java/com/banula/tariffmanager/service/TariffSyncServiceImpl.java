package com.banula.tariffmanager.service;

import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.banula.openlib.ocpi.model.enums.ConnectionStatus;
import com.banula.openlib.ocpi.model.enums.Role;
import com.banula.tariffmanager.client.TmPlatformClient;
import com.banula.tariffmanager.config.ApplicationConfiguration;
import com.banula.tariffmanager.model.dto.HubClientInfoDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@AllArgsConstructor
public class TariffSyncServiceImpl implements TariffSyncService {

    private final TmPlatformClient tmPlatformClient;
    private final TMTariffService tariffService;
    private final HubClientInfoService hubClientInfoService;
    private final ApplicationConfiguration applicationConfiguration;

    @Override
    public void welcomeParty(HubClientInfoDTO party) {
        if (party == null || party.getRole() != Role.CPO) {
            return;
        }
        if (party.getStatus() != ConnectionStatus.CONNECTED) {
            return;
        }
        if (isSelf(party.getCountryCode(), party.getPartyId())) {
            return;
        }

        log.info("Welcome ceremony: pulling tariffs from CPO {}/{}", party.getCountryCode(), party.getPartyId());
        LocalDateTime to = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime from = to.minusDays(applicationConfiguration.getTariffSyncWelcomeLookbackDays());
        pullStoreAndBroadcast(party.getCountryCode(), party.getPartyId(), from, to);
    }

    @Override
    public void syncRecentTariffs() {
        LocalDateTime to = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime from = to.minusHours(applicationConfiguration.getTariffSyncLookbackHours());

        List<HubClientInfoDTO> cpos = hubClientInfoService
                .getHubClientInfosByStatus(List.of(ConnectionStatus.CONNECTED))
                .stream()
                .filter(p -> p.getRole() == Role.CPO)
                .filter(p -> !isSelf(p.getCountryCode(), p.getPartyId()))
                .toList();

        log.info("Hourly tariff sync: {} connected CPO(s), window {} -> {}", cpos.size(), from, to);
        for (HubClientInfoDTO cpo : cpos) {
            try {
                pullStoreAndBroadcast(cpo.getCountryCode(), cpo.getPartyId(), from, to);
            } catch (Exception e) {
                log.warn("Tariff sync failed for {}/{}: {}", cpo.getCountryCode(), cpo.getPartyId(), e.getMessage());
            }
        }
    }

    @Override
    public void pullStoreAndBroadcast(String countryCode, String partyId, LocalDateTime dateFrom,
            LocalDateTime dateTo) {
        List<TariffDTO> tariffs = tmPlatformClient.getTariffs(countryCode, partyId, dateFrom, dateTo);
        if (tariffs == null || tariffs.isEmpty()) {
            log.info("No tariffs returned from {}/{} for window {} -> {}", countryCode, partyId, dateFrom, dateTo);
            return;
        }

        log.info("Pulled {} tariff(s) from {}/{}; storing locally then PUT to hub for OCN broadcast", tariffs.size(),
                countryCode, partyId);
        for (TariffDTO tariff : tariffs) {
            ensureOwner(tariff, countryCode, partyId);
            try {
                tariffService.saveTariff(tariff);
            } catch (Exception e) {
                log.warn("Failed to store tariff {} from {}/{}: {}", tariff.getId(), countryCode, partyId,
                        e.getMessage());
                continue;
            }
            try {
                // OCPI-to = hub (DE/BAN) → node broadcasts via ModuleNotificationService
                tmPlatformClient.putTariffToHub(tariff);
            } catch (Exception e) {
                log.warn("Failed to put tariff {} to hub from {}/{}: {}", tariff.getId(), countryCode, partyId,
                        e.getMessage());
            }
        }

        log.info("Finished pull/store/hub-put for {} tariff(s) from {}/{}", tariffs.size(), countryCode, partyId);
    }

    private void ensureOwner(TariffDTO tariff, String countryCode, String partyId) {
        if (tariff.getCountryCode() == null || tariff.getCountryCode().isBlank()) {
            tariff.setCountryCode(countryCode);
        }
        if (tariff.getPartyId() == null || tariff.getPartyId().isBlank()) {
            tariff.setPartyId(partyId);
        }
    }

    private boolean isSelf(String countryCode, String partyId) {
        return sameParty(countryCode, partyId, applicationConfiguration.getCountryCode(),
                applicationConfiguration.getPartyId());
    }

    private boolean sameParty(String countryA, String partyA, String countryB, String partyB) {
        return Objects.equals(normalize(countryA), normalize(countryB))
                && Objects.equals(normalize(partyA), normalize(partyB));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }
}
