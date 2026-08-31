package com.banula.tariffmanager.service;

import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.banula.openlib.ocpi.model.enums.ConnectionStatus;
import com.banula.openlib.ocpi.model.enums.Role;
import com.banula.tariffmanager.client.TmPlatformClient;
import com.banula.tariffmanager.config.ApplicationConfiguration;
import com.banula.tariffmanager.config.MongoCollectionMapper;
import com.banula.tariffmanager.model.MongoTariffPublicationOutbox;
import com.banula.tariffmanager.model.TariffPublicationStatus;
import com.banula.tariffmanager.model.dto.HubClientInfoDTO;
import com.banula.tariffmanager.repository.TariffPublicationOutboxRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@AllArgsConstructor
public class TariffSyncServiceImpl implements TariffSyncService {

    private final TmPlatformClient tmPlatformClient;
    private final TMTariffService tariffService;
    private final HubClientInfoService hubClientInfoService;
    private final ApplicationConfiguration applicationConfiguration;
    private final MongoTemplate mongoTemplate;
    private final MongoCollectionMapper mongoCollectionMapper;
    private final TariffPublicationOutboxRepository tariffPublicationOutboxRepository;

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
        retryPendingHubPublications();

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
            String attemptId = markPublicationPending(tariff, null);
            try {
                // OCPI-to = hub (DE/BAN) → node broadcasts via ModuleNotificationService
                tmPlatformClient.putTariffToHub(tariff);
                markPublicationDelivered(tariff, attemptId);
            } catch (Exception e) {
                log.warn("Failed to put tariff {} to hub from {}/{}; will retry: {}", tariff.getId(), countryCode,
                        partyId, e.getMessage());
                // A fresh attempt token is written on purpose: it fences any older PUT for the same
                // tariff that is still in flight, so that one can no longer report DELIVERED over
                // this failure.
                markPublicationPending(tariff, e.getMessage());
            }
        }

        log.info("Finished pull/store/hub-put for {} tariff(s) from {}/{}", tariffs.size(), countryCode, partyId);
    }

    private void retryPendingHubPublications() {
        List<MongoTariffPublicationOutbox> pending = tariffPublicationOutboxRepository
                .findByStatus(TariffPublicationStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Retrying {} pending hub tariff publication(s)", pending.size());
        for (MongoTariffPublicationOutbox record : pending) {
            try {
                TariffDTO tariff = tariffService.getTariff(record.getCountryCode(), record.getPartyId(),
                        record.getTariffId());
                if (tariff == null) {
                    log.warn("Dropping pending publication for missing tariff {}/{}/{}", record.getCountryCode(),
                            record.getPartyId(), record.getTariffId());
                    tariffPublicationOutboxRepository.delete(record);
                    continue;
                }
                tmPlatformClient.putTariffToHub(tariff);
                markPublicationDelivered(tariff, record.getAttemptId());
            } catch (Exception e) {
                log.warn("Retry PUT failed for tariff {}/{}/{}: {}", record.getCountryCode(), record.getPartyId(),
                        record.getTariffId(), e.getMessage());
                record.setLastAttemptAt(LocalDateTime.now(ZoneOffset.UTC));
                record.setLastError(e.getMessage());
                tariffPublicationOutboxRepository.save(record);
            }
        }
    }

    /**
     * Marks the tariff PENDING and returns the token identifying <em>this</em> attempt. Publication
     * is not serialized per tariff (the welcome ceremony runs async while the hourly sync runs on
     * the scheduler thread), so the token is what lets
     * {@link #markPublicationDelivered(TariffDTO, String)} tell its own attempt from a newer one.
     */
    private String markPublicationPending(TariffDTO tariff, String error) {
        String attemptId = UUID.randomUUID().toString();
        Query query = Query.query(Criteria.where("countryCode").is(tariff.getCountryCode())
                .and("partyId").is(tariff.getPartyId())
                .and("tariffId").is(tariff.getId()));
        Update update = new Update()
                .set("status", TariffPublicationStatus.PENDING)
                .set("attemptId", attemptId)
                .set("lastAttemptAt", LocalDateTime.now(ZoneOffset.UTC))
                .set("lastError", error)
                .setOnInsert("countryCode", tariff.getCountryCode())
                .setOnInsert("partyId", tariff.getPartyId())
                .setOnInsert("tariffId", tariff.getId());
        mongoTemplate.upsert(query, update, MongoTariffPublicationOutbox.class,
                mongoCollectionMapper.getTariffPublicationOutboxCollectionName());
        return attemptId;
    }

    /**
     * Conditional transition to DELIVERED: it applies only while the record still carries the token
     * of the attempt that is reporting success. A slow PUT whose tariff has since been re-published
     * (and failed) therefore leaves the record PENDING for the retry loop instead of marking the
     * newer, undelivered publication as delivered.
     */
    private void markPublicationDelivered(TariffDTO tariff, String attemptId) {
        Query query = Query.query(Criteria.where("countryCode").is(tariff.getCountryCode())
                .and("partyId").is(tariff.getPartyId())
                .and("tariffId").is(tariff.getId())
                .and("attemptId").is(attemptId));
        Update update = new Update()
                .set("status", TariffPublicationStatus.DELIVERED)
                .set("lastAttemptAt", LocalDateTime.now(ZoneOffset.UTC))
                .unset("lastError");
        mongoTemplate.updateFirst(query, update, MongoTariffPublicationOutbox.class,
                mongoCollectionMapper.getTariffPublicationOutboxCollectionName());
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
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
