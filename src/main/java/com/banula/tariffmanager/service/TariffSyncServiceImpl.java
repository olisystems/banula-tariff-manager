package com.banula.tariffmanager.service;

import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.banula.openlib.ocpi.exception.OCPICustomException;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    public SyncResult pullStoreAndBroadcast(String countryCode, String partyId, LocalDateTime dateFrom,
            LocalDateTime dateTo) {
        if (isSelf(countryCode, partyId)) {
            throw new OCPICustomException("Select a CPO party instead of the hub itself");
        }
        List<TariffDTO> tariffs = tmPlatformClient.getTariffs(countryCode, partyId, dateFrom, dateTo);
        if (tariffs == null || tariffs.isEmpty()) {
            log.info("No tariffs returned from {}/{} for window {} -> {}", countryCode, partyId, dateFrom, dateTo);
            return new SyncResult(0, 0, 0, 0);
        }

        int synced = 0, failed = 0, pending = 0;
        for (TariffDTO tariff : tariffs) {
            if (tariff == null || tariff.getId() == null || tariff.getId().isBlank()
                    || (tariff.getCountryCode() != null && !tariff.getCountryCode().isBlank() && !countryCode.equalsIgnoreCase(tariff.getCountryCode()))
                    || (tariff.getPartyId() != null && !tariff.getPartyId().isBlank() && !partyId.equalsIgnoreCase(tariff.getPartyId()))) {
                throw new OCPICustomException("CPO returned a tariff with an invalid owner or ID");
            }
        }
        log.info("Pulled {} tariff(s) from {}/{}; storing locally then PUT to hub for OCN broadcast", tariffs.size(),
                countryCode, partyId);
        for (TariffDTO tariff : tariffs) {
            ensureOwner(tariff, countryCode, partyId);
            try {
                tariffService.saveTariff(tariff);
                synced++;
            } catch (Exception e) {
                log.warn("Failed to store tariff {} from {}/{}: {}", tariff.getId(), countryCode, partyId,
                        e.getMessage());
                failed++;
                continue;
            }
            MongoTariffPublicationOutbox record = preparePublication(tariff);
            if (!attemptPublication(record, tariff)) pending++;

        }

        log.info("Finished pull/store/hub-put for {} tariff(s) from {}/{}", tariffs.size(), countryCode, partyId);
        return new SyncResult(tariffs.size(), synced, failed, pending);
    }

    private void retryPendingHubPublications() {
        List<MongoTariffPublicationOutbox> pending = tariffPublicationOutboxRepository.findDue(
                LocalDateTime.now(ZoneOffset.UTC), PageRequest.of(0,
                        Math.max(1, applicationConfiguration.getTariffPublicationBatchSize()),
                        Sort.by("nextAttemptAt").ascending().and(Sort.by("mongoId"))));
        for (MongoTariffPublicationOutbox record : pending) {
            try {
                TariffDTO tariff = tariffService.getTariff(record.getCountryCode(), record.getPartyId(), record.getTariffId());
                if (tariff == null) {
                    mongoTemplate.remove(publicationQuery(record), MongoTariffPublicationOutbox.class,
                            mongoCollectionMapper.getTariffPublicationOutboxCollectionName());
                    continue;
                }
                attemptPublication(record, tariff);
            } catch (Exception e) {
                recordFailure(record, e, true);
            }
        }
    }

    private MongoTariffPublicationOutbox preparePublication(TariffDTO tariff) {
        var existing = tariffPublicationOutboxRepository.findByCountryCodeAndPartyIdAndTariffId(
                tariff.getCountryCode(), tariff.getPartyId(), tariff.getId());
        var record = existing.orElseGet(MongoTariffPublicationOutbox::new);
        // Repeated pulls must not reset the retry budget of the same revision.
        if (existing.isEmpty() || !Objects.equals(record.getTariffLastUpdated(), tariff.getLastUpdated())) {
            Query previousRevision = existing.isPresent() ? publicationQuery(record) : null;
            record.setAttemptId(UUID.randomUUID().toString());
            record.setCountryCode(tariff.getCountryCode());
            record.setPartyId(tariff.getPartyId());
            record.setTariffId(tariff.getId());
            record.setTariffLastUpdated(tariff.getLastUpdated());
            record.setAttempts(0);
            record.setStatus(TariffPublicationStatus.PENDING);
            record.setLastAttemptAt(null);
            record.setNextAttemptAt(null);
            record.setLastError(null);
            if (existing.isEmpty()) {
                tariffPublicationOutboxRepository.save(record);
            } else {
                // Reset only the revision we read; a concurrent pull may already have replaced it.
                Update reset = new Update().set("attemptId", record.getAttemptId())
                        .set("tariffLastUpdated", record.getTariffLastUpdated())
                        .set("attempts", 0).set("status", TariffPublicationStatus.PENDING)
                        .unset("lastAttemptAt").unset("nextAttemptAt").unset("lastError");
                if (mongoTemplate.updateFirst(previousRevision, reset, MongoTariffPublicationOutbox.class,
                        mongoCollectionMapper.getTariffPublicationOutboxCollectionName()).getMatchedCount() == 0) {
                    return null;
                }
            }
        }
        return record;
    }

    private boolean attemptPublication(MongoTariffPublicationOutbox record, TariffDTO tariff) {
        if (record == null) return false; // Another pull replaced the revision before we could claim it.
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (record.getStatus() == TariffPublicationStatus.FAILED
                || (record.getNextAttemptAt() != null && record.getNextAttemptAt().isAfter(now))) return false;
        if (record.getAttempts() >= maxAttempts()) {
            record.setStatus(TariffPublicationStatus.FAILED);
            record.setNextAttemptAt(null);
            mongoTemplate.updateFirst(publicationQuery(record),
                    new Update().set("status", TariffPublicationStatus.FAILED).unset("nextAttemptAt"),
                    MongoTariffPublicationOutbox.class, mongoCollectionMapper.getTariffPublicationOutboxCollectionName());
            return false;
        }
        Query claim = publicationQuery(record);
        // Legacy records have no attempts field; accept both missing and explicit zero.
        if (record.getAttempts() == 0) {
            claim.addCriteria(new Criteria().orOperator(
                    Criteria.where("attempts").is(null), Criteria.where("attempts").is(0)));
        } else {
            claim.addCriteria(Criteria.where("attempts").is(record.getAttempts()));
        }
        claim.addCriteria(Criteria.where("nextAttemptAt").is(record.getNextAttemptAt()));
        record.setAttemptId(UUID.randomUUID().toString());
        record.setAttempts(record.getAttempts() + 1);
        record.setLastAttemptAt(now);
        record.setNextAttemptAt(now.plusSeconds(backoffSeconds(record.getAttempts())));
        Update attempt = new Update().set("attemptId", record.getAttemptId())
                .set("attempts", record.getAttempts()).set("lastAttemptAt", record.getLastAttemptAt())
                .set("nextAttemptAt", record.getNextAttemptAt()).set("status", TariffPublicationStatus.PENDING);
        if (mongoTemplate.updateFirst(claim, attempt, MongoTariffPublicationOutbox.class,
                mongoCollectionMapper.getTariffPublicationOutboxCollectionName()).getMatchedCount() == 0) {
            return false; // A different worker or revision owns publication now.
        }
        try {
            tmPlatformClient.putTariffToHub(tariff);
            markPublicationDelivered(record);
            return true;
        } catch (Exception e) {
            recordFailure(record, e, false);
            return false;
        }
    }

    private int maxAttempts() { return Math.max(1, applicationConfiguration.getTariffPublicationMaxAttempts()); }

    private long backoffSeconds(int attempts) {
        long base = Math.max(1, Math.min(86400, applicationConfiguration.getTariffPublicationBackoffSeconds()));
        return Math.min(86400, base * (1L << Math.min(16, Math.max(0, attempts - 1))));
    }

    private void recordFailure(MongoTariffPublicationOutbox record, Exception error, boolean incrementAttempt) {
        // Lookup failures also consume the budget instead of retrying forever.
        if (incrementAttempt) {
            record.setAttempts(record.getAttempts() + 1);
            record.setLastAttemptAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        record.setLastError(error.getMessage());
        record.setStatus(record.getAttempts() >= maxAttempts() ? TariffPublicationStatus.FAILED : TariffPublicationStatus.PENDING);
        record.setNextAttemptAt(record.getStatus() == TariffPublicationStatus.FAILED ? null
                : record.getLastAttemptAt().plusSeconds(backoffSeconds(record.getAttempts())));
        Update failure = new Update().set("attempts", record.getAttempts())
                .set("lastAttemptAt", record.getLastAttemptAt()).set("lastError", record.getLastError())
                .set("status", record.getStatus()).set("nextAttemptAt", record.getNextAttemptAt());
        mongoTemplate.updateFirst(publicationQuery(record), failure, MongoTariffPublicationOutbox.class,
                mongoCollectionMapper.getTariffPublicationOutboxCollectionName());
    }

    private Query publicationQuery(MongoTariffPublicationOutbox record) {
        return Query.query(Criteria.where("countryCode").is(record.getCountryCode())
                .and("partyId").is(record.getPartyId()).and("tariffId").is(record.getTariffId())
                .and("attemptId").is(record.getAttemptId())
                .and("tariffLastUpdated").is(record.getTariffLastUpdated()));
    }

    private void markPublicationDelivered(MongoTariffPublicationOutbox record) {
        // An old PUT must never delete a newer revision's pending retry.
        mongoTemplate.remove(publicationQuery(record), MongoTariffPublicationOutbox.class,
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
