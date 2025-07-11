package com.banula.tariffmanager.factory;

import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.banula.tariffmanager.model.OnChainTariff;

public class TariffFactory {
    public static OnChainTariff dtoToTariff(TariffDTO tariffDTO) {
        if (tariffDTO == null)
            return null;
        return OnChainTariff
                .builder()
                .id(tariffDTO.getId())
                .countryCode(tariffDTO.getCountryCode())
                .endDateTime(tariffDTO.getEndDateTime())
                .tariffAltUrl(tariffDTO.getTariffAltUrl())
                .type(tariffDTO.getType())
                .energyMix(tariffDTO.getEnergyMix())
                .tariffAltText(tariffDTO.getTariffAltText())
                .lastUpdated(tariffDTO.getLastUpdated())
                .maxPrice(tariffDTO.getMaxPrice())
                .minPrice(tariffDTO.getMinPrice())
                .currency(tariffDTO.getCurrency())
                .elements(tariffDTO.getElements())
                .partyId(tariffDTO.getPartyId())
                .startDateTime(tariffDTO.getStartDateTime())
                .build();
    }

    public static TariffDTO tariffToDto(OnChainTariff onChainTariff) {
        if (onChainTariff == null)
            return null;

        TariffDTO tariffDTO =  TariffDTO
                                .builder()
                                .id(onChainTariff.getId())
                                .countryCode(onChainTariff.getCountryCode())
                                .endDateTime(onChainTariff.getEndDateTime())
                                .tariffAltUrl(onChainTariff.getTariffAltUrl())
                                .type(onChainTariff.getType())
                                .energyMix(onChainTariff.getEnergyMix())
                                .tariffAltText(onChainTariff.getTariffAltText())
                                .lastUpdated(onChainTariff.getLastUpdated())
                                .maxPrice(onChainTariff.getMaxPrice())
                                .minPrice(onChainTariff.getMinPrice())
                                .currency(onChainTariff.getCurrency())
                                .elements(onChainTariff.getElements())
                                .partyId(onChainTariff.getPartyId())
                                .startDateTime(onChainTariff.getStartDateTime())
                                .build();

        //support for old CDRs
        if(tariffDTO.getId() == null) {
            tariffDTO.setId(onChainTariff.getHashTag());
        }

        return tariffDTO;

    }

}
