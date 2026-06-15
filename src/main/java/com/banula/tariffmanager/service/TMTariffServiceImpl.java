package com.banula.tariffmanager.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.banula.openlib.ocpi.exception.OCPICustomException;
import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.banula.openlib.ocpi.model.vo.EnergyMix;
import com.banula.tariffmanager.client.HashingClient;
import com.banula.tariffmanager.config.ApplicationConfiguration;
import com.banula.tariffmanager.factory.TariffFactory;
import com.banula.tariffmanager.model.OnChainTariff;
import com.banula.tariffmanager.repository.TariffRepository;
import com.banula.tariffmanager.util.TariffUtility;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class TMTariffServiceImpl implements TMTariffService {

    private static final int ENERGY_MIX_VALIDATION_ERROR_CODE = 2002;

    private final TariffRepository tariffRepository;
    private final TariffUtility tariffUtility;

    private final HashingClient hashingClient;

    private final ApplicationConfiguration applicationConfiguration;

    @Override
    public TariffDTO getTariff(String countryCode, String partyId, String tariffId) {
        try {
            return TariffFactory
                    .tariffToDto(tariffRepository.findByCompositeKey(countryCode, partyId, tariffId)
                            .orElse(null));
        } catch (Exception e) {
            String errorMessage = "Error happened while fetching tariffs, error message: " + e.getLocalizedMessage();
            log.error(errorMessage);
            throw new OCPICustomException(errorMessage);
        }
    }

    @Override
    public OnChainTariff saveTariff(TariffDTO tariffDTO) {
        try {
            assertEnergyMixRequirementsIfEnabled(tariffDTO);
            OnChainTariff tariff = TariffFactory.dtoToTariff(tariffDTO);
            String tariffTag = tariffDTO.getCountryCode() + "-" + tariffDTO.getPartyId() + "-" + tariffDTO.getId();

            // Commenting these lines out, since hashing API is not working at the moment.
            // PublishedHash publishedHash = hashingClient.hashAndPublish(tariff,
            // tariffTag);
            // tariff.setHash(publishedHash.getHash());
            // tariff.setTransactionId(publishedHash.getTransactionHash());

            tariff.setHash(UUID.randomUUID().toString());
            tariff.setTransactionId(UUID.randomUUID().toString());
            tariff.setPublishedAt(LocalDateTime.now());
            tariff.setHashTag(tariffTag);

            tariffRepository.save(tariff);
            return tariff;
        } catch (Exception e) {
            String errorMessage = "Error happened while saving tariffs, error message: " + e.getLocalizedMessage();
            log.error(errorMessage);
            throw new OCPICustomException(errorMessage);
        }
    }

    @Override
    public OnChainTariff getLatestTariffForPartyAt(String countryCode, String partyId, LocalDateTime datetime) {
        List<OnChainTariff> tariffs = tariffRepository.findLatestTariffForPartyAtOrderByPublishedAt(partyId,
                countryCode, datetime);

        if (tariffs.size() == 0)
            return null;

        OnChainTariff latestOnChainTariff = tariffs.get(0);

        //support for old tariffs
        if(latestOnChainTariff.getId() == null) {
            latestOnChainTariff.setId(latestOnChainTariff.getHashTag());
        }

        return latestOnChainTariff;
    }

    @Override
    public OnChainTariff getTariffByHash(String hash) {
        return tariffRepository.findByHash(hash).orElse(null);
    }

    @Override
    public String hashTariff(TariffDTO tariffDTO) {
        OnChainTariff tariff = TariffFactory.dtoToTariff(tariffDTO);
        return hashingClient.hash(tariff);
    }

    @Override
    public List<TariffDTO> findTariffsBetweenDates(
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            Integer offset,
            Integer limit) {

        try {
            List<OnChainTariff> tariffs = tariffUtility.findTariffs(dateFrom, dateTo, offset, limit);

            return tariffs.stream()
                    .map(TariffFactory::tariffToDto)
                    .toList();

        } catch (Exception e) {
            String errorMessage = "Error happened while fetching tariffs, error message: " + e.getLocalizedMessage();
            log.error(errorMessage);
            throw new OCPICustomException(errorMessage);
        }
    }

    private void assertEnergyMixRequirementsIfEnabled(TariffDTO tariffDTO) {
        if (!applicationConfiguration.isRequireEnergyMix()) {
            return;
        }
        EnergyMix mix = tariffDTO.getEnergyMix();
        if (mix == null) {
            throw new OCPICustomException(
                    "energy_mix is required when feature-flags.require-energy-mix is enabled",
                    ENERGY_MIX_VALIDATION_ERROR_CODE);
        }
        if (!mix.isGreenEnergy()) {
            throw new OCPICustomException(
                    "energy_mix.is_green_energy must be true when feature-flags.require-energy-mix is enabled",
                    ENERGY_MIX_VALIDATION_ERROR_CODE);
        }
        String expected = applicationConfiguration.getEnergyProductName();
        String productName = mix.getEnergyProductName();
        if (productName == null || !expected.equals(productName)) {
            throw new OCPICustomException(
                    "energy_mix.energy_product_name must be \"" + expected
                            + "\" when feature-flags.require-energy-mix is enabled",
                    ENERGY_MIX_VALIDATION_ERROR_CODE);
        }
    }

}
