package com.banula.tariffmanager.service;

import com.banula.openlib.ocpi.exception.OCPICustomException;
import com.banula.openlib.ocpi.model.enums.ConnectionStatus;
import com.banula.openlib.ocpi.util.Constants;
import com.banula.tariffmanager.client.TmPlatformClient;
import com.banula.tariffmanager.config.MongoCollectionMapper;
import com.banula.tariffmanager.event.PartyConnectedEvent;
import com.banula.tariffmanager.mapper.ClientInfoMapper;
import com.banula.tariffmanager.model.MongoClientInfo;
import com.banula.tariffmanager.model.dto.HubClientInfoDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class HubClientInfoServiceImpl implements HubClientInfoService {

    private final MongoTemplate mongoTemplate;
    private final MongoCollectionMapper mongoCollectionMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final TmPlatformClient tmPlatformClient;

    @Override
    public HubClientInfoDTO updateHubClientInfoByPartyIdAndCountryCode(String partyId, String countryCode,
            HubClientInfoDTO clientInfoDTO) {
        // status is a required field of the OCPI ClientInfo object: without this guard a body
        // carrying only a role would overwrite a stored CONNECTED party with a null status.
        if (clientInfoDTO == null || clientInfoDTO.getRole() == null || clientInfoDTO.getStatus() == null) {
            throw new OCPICustomException("Client info role and status are required",
                    Constants.STATUS_CODE_INVALID_OR_MISSING_PARAMETERS);
        }

        LocalDateTime lastUpdated = LocalDateTime.now(ZoneOffset.UTC);
        Query query = Query.query(Criteria.where("partyId").is(partyId)
                .and("countryCode").is(countryCode)
                .and("role").is(clientInfoDTO.getRole()));
        Update update = new Update()
                .set("status", clientInfoDTO.getStatus())
                .set("lastUpdated", lastUpdated)
                .setOnInsert("partyId", partyId)
                .setOnInsert("countryCode", countryCode)
                .setOnInsert("role", clientInfoDTO.getRole());
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(false);

        MongoClientInfo previous = mongoTemplate.findAndModify(query, update, options, MongoClientInfo.class,
                mongoCollectionMapper.getHubClientInfoCollectionName());
        ConnectionStatus previousStatus = previous != null ? previous.getStatus() : null;

        MongoClientInfo current = previous != null ? previous : new MongoClientInfo();
        if (previous == null) {
            current.setPartyId(partyId);
            current.setCountryCode(countryCode);
            current.setRole(clientInfoDTO.getRole());
        }
        current.setStatus(clientInfoDTO.getStatus());
        current.setLastUpdated(lastUpdated);

        HubClientInfoDTO saved = ClientInfoMapper.toHubClientInfoDTO(current);
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
                try {
                    updateHubClientInfoByPartyIdAndCountryCode(party.getPartyId(), party.getCountryCode(), party);
                } catch (Exception e) {
                    log.warn("Skipping unusable hub client info record {}/{}: {}", party.getCountryCode(),
                            party.getPartyId(), e.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("Initial HubClientInfo sync failed, tariff-manager will learn parties dynamically: {}",
                    ex.getLocalizedMessage());
        }
    }
}
