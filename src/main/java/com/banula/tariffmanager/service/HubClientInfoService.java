package com.banula.tariffmanager.service;

import com.banula.openlib.ocpi.model.enums.ConnectionStatus;
import com.banula.tariffmanager.model.dto.HubClientInfoDTO;

import java.util.List;

public interface HubClientInfoService {

    HubClientInfoDTO updateHubClientInfoByPartyIdAndCountryCode(String partyId, String countryCode,
            HubClientInfoDTO clientInfoDTO);

    List<HubClientInfoDTO> getHubClientInfosByStatus(List<ConnectionStatus> statuses);
}
