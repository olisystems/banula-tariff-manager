package com.banula.tariffmanager.service;

import com.banula.openlib.ocpi.exception.OCPICustomException;
import com.banula.openlib.ocpi.model.enums.ConnectionStatus;
import com.banula.tariffmanager.client.TmPlatformClient;
import com.banula.tariffmanager.config.MongoCollectionMapper;
import com.banula.tariffmanager.event.PartyConnectedEvent;
import com.banula.tariffmanager.mapper.ClientInfoMapper;
import com.banula.tariffmanager.model.MongoClientInfo;
import com.banula.tariffmanager.model.dto.HubClientInfoDTO;
import com.banula.tariffmanager.repository.HubClientInfoRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class HubClientInfoServiceImpl implements HubClientInfoService {

    private final HubClientInfoRepository hubClientInfoRepository;
    private final MongoTemplate mongoTemplate;
    private final MongoCollectionMapper mongoCollectionMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TmPlatformClient tmPlatformClient;

    @Override
    public HubClientInfoDTO updateHubClientInfoByPartyIdAndCountryCode(String partyId, String countryCode,
            HubClientInfoDTO clientInfoDTO) {
        MongoClientInfo mongoClientInfo = hubClientInfoRepository
                .findByPartyIdAndCountryCodeAndRole(partyId, countryCode, clientInfoDTO.getRole()).orElse(null);
        ConnectionStatus previousStatus = mongoClientInfo != null ? mongoClientInfo.getStatus() : null;
        if (mongoClientInfo == null) {
            mongoClientInfo = new MongoClientInfo();
            mongoClientInfo.setPartyId(partyId);
            mongoClientInfo.setCountryCode(countryCode);
            mongoClientInfo.setRole(clientInfoDTO.getRole());
        }
        mongoClientInfo.setStatus(clientInfoDTO.getStatus());
        mongoClientInfo.setLastUpdated(LocalDateTime.now(ZoneOffset.UTC));
        HubClientInfoDTO saved = ClientInfoMapper.toHubClientInfoDTO(hubClientInfoRepository.save(mongoClientInfo));
        publishConnectedIfTransition(previousStatus, saved);
        return saved;
    }

    @Override
    public List<HubClientInfoDTO> getHubClientInfosByStatus(List<ConnectionStatus> statuses) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where("status").in(statuses));
            query.with(Sort.by(Sort.Direction.DESC, "lastUpdated"));
            return mongoTemplate.find(query, MongoClientInfo.class,
                    mongoCollectionMapper.getHubClientInfoCollectionName())
                    .stream()
                    .map(ClientInfoMapper::toHubClientInfoDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new OCPICustomException(
                    "Error occurred while fetching hub client infos by status: " + e.getLocalizedMessage());
        }
    }

    private void publishConnectedIfTransition(ConnectionStatus previousStatus, HubClientInfoDTO saved) {
        if (saved.getStatus() != ConnectionStatus.CONNECTED) {
            return;
        }
        if (previousStatus == ConnectionStatus.CONNECTED) {
            return;
        }
        log.info("Party {}/{} ({}) became CONNECTED; publishing welcome event", saved.getCountryCode(),
                saved.getPartyId(), saved.getRole());
        eventPublisher.publishEvent(new PartyConnectedEvent(this, saved));
    }

    @Override
    public void syncAllHubClientInfoParties() {
        try {
            List<HubClientInfoDTO> parties = tmPlatformClient.getHubClientInfos();
            log.info("HubClientInfo sync pulled {} party record(s) from hub", parties.size());
            for (HubClientInfoDTO party : parties) {
                updateHubClientInfoByPartyIdAndCountryCode(party.getPartyId(), party.getCountryCode(), party);
            }
        } catch (Exception ex) {
            log.warn("Initial HubClientInfo sync failed, tariff-manager will learn parties dynamically: {}",
                    ex.getLocalizedMessage());
        }
    }
}
