package com.banula.tariffmanager.mapper;

import com.banula.tariffmanager.model.MongoClientInfo;
import com.banula.tariffmanager.model.dto.HubClientInfoDTO;

public final class ClientInfoMapper {

    private ClientInfoMapper() {
    }

    public static HubClientInfoDTO toHubClientInfoDTO(MongoClientInfo mongoClientInfo) {
        if (mongoClientInfo == null) {
            return null;
        }
        return HubClientInfoDTO.builder()
                .id(mongoClientInfo.getMongoId())
                .partyId(mongoClientInfo.getPartyId())
                .countryCode(mongoClientInfo.getCountryCode())
                .role(mongoClientInfo.getRole())
                .status(mongoClientInfo.getStatus())
                .lastUpdated(mongoClientInfo.getLastUpdated())
                .build();
    }
}
