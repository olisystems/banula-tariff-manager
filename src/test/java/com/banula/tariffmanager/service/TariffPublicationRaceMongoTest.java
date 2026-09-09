package com.banula.tariffmanager.service;

import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.banula.tariffmanager.client.TmPlatformClient;
import com.banula.tariffmanager.config.ApplicationConfiguration;
import com.banula.tariffmanager.config.MongoCollectionMapper;
import com.banula.tariffmanager.model.MongoTariffPublicationOutbox;
import com.banula.tariffmanager.model.TariffPublicationStatus;
import com.banula.tariffmanager.repository.TariffPublicationOutboxRepository;
import com.mongodb.client.MongoClients;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="TARIFF_SYNC_MONGO_URI", matches=".+")
class TariffPublicationRaceMongoTest {
    @ParameterizedTest
    @ValueSource(booleans={true, false})
    void lateCompletionPreservesNewerFailedRevision(boolean olderSucceeds) {
        try (var client = MongoClients.create(System.getenv("TARIFF_SYNC_MONGO_URI"))) {
            var mongo = new MongoTemplate(client, "tariff_sync_" + UUID.randomUUID().toString().replace("-", ""));
            try {
                var platform = mock(TmPlatformClient.class);
                var outbox = mock(TariffPublicationOutboxRepository.class);
                var config = mock(ApplicationConfiguration.class);
                var collections = mock(MongoCollectionMapper.class);
                when(collections.getTariffPublicationOutboxCollectionName()).thenReturn("outbox");
                when(config.getTariffPublicationMaxAttempts()).thenReturn(3);
                when(config.getTariffPublicationBackoffSeconds()).thenReturn(3600L);
                when(outbox.findByCountryCodeAndPartyIdAndTariffId(anyString(), anyString(), anyString()))
                    .thenAnswer(call -> Optional.ofNullable(mongo.findOne(
                        Query.query(Criteria.where("countryCode").is(call.getArgument(0))
                            .and("partyId").is(call.getArgument(1)).and("tariffId").is(call.getArgument(2))),
                        MongoTariffPublicationOutbox.class, "outbox")));
                when(outbox.save(any(MongoTariffPublicationOutbox.class)))
                    .thenAnswer(call -> mongo.save(call.getArgument(0), "outbox"));
                var service = new TariffSyncServiceImpl(platform, mock(TMTariffService.class),
                    mock(HubClientInfoService.class), config, mongo, collections, outbox);
                var older = tariff(1);
                var newer = tariff(2);
                when(platform.getTariffs("DE", "OLI", null, null))
                    .thenReturn(List.of(older), List.of(newer));
                doAnswer(call -> {
                    TariffDTO sent = call.getArgument(0);
                    if (sent == older) {
                        // Reproduce the ordering of overlapping PUTs without timing-dependent sleeps.
                        service.pullStoreAndBroadcast("DE", "OLI", null, null);
                        if (olderSucceeds) return null;
                        throw new IllegalStateException("older failure");
                    }
                    throw new IllegalStateException("newer failure");
                }).when(platform).putTariffToHub(any());
                service.pullStoreAndBroadcast("DE", "OLI", null, null);

                var pending = mongo.findAll(MongoTariffPublicationOutbox.class, "outbox");
                assertEquals(1, pending.size());
                var record = pending.get(0);
                assertEquals(newer.getLastUpdated(), record.getTariffLastUpdated());
                assertEquals("newer failure", record.getLastError());
                assertEquals(TariffPublicationStatus.PENDING, record.getStatus());
                assertEquals(1, record.getAttempts());
                assertNotNull(record.getAttemptId());
                assertEquals(record.getLastAttemptAt().plusHours(1), record.getNextAttemptAt());
            } finally {
                mongo.getDb().drop();
            }
        }
    }

    private TariffDTO tariff(int day) {
        var tariff = new TariffDTO();
        tariff.setCountryCode("DE");
        tariff.setPartyId("OLI");
        tariff.setId("same-tariff");
        tariff.setLastUpdated(LocalDateTime.of(2026, 1, day, 0, 0));
        return tariff;
    }
}
