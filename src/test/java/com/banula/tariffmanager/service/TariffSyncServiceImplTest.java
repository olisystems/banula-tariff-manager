package com.banula.tariffmanager.service;

import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.banula.tariffmanager.client.TmPlatformClient;
import com.banula.tariffmanager.config.ApplicationConfiguration;
import com.banula.tariffmanager.config.MongoCollectionMapper;
import com.banula.tariffmanager.model.MongoTariffPublicationOutbox;
import com.banula.tariffmanager.model.TariffPublicationStatus;
import com.banula.tariffmanager.repository.TariffPublicationOutboxRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the publication outbox transitions of {@link TariffSyncServiceImpl}, in particular that a
 * PUT which succeeds late cannot mark a newer, failed publication as DELIVERED.
 */
class TariffSyncServiceImplTest {

    private static final String COUNTRY = "DE";
    private static final String PARTY = "ABC";
    private static final String TARIFF_ID = "tariff-1";
    private static final String OUTBOX_COLLECTION = "TariffPublicationOutbox";
    private static final String OLDER_PUBLISHER_THREAD = "older-publisher";

    private TmPlatformClient tmPlatformClient;
    private TMTariffService tariffService;
    private MongoTemplate mongoTemplate;
    private TariffSyncServiceImpl service;
    private OutboxRecord outbox;

    @BeforeEach
    void setUp() {
        tmPlatformClient = mock(TmPlatformClient.class);
        tariffService = mock(TMTariffService.class);
        mongoTemplate = mock(MongoTemplate.class);
        outbox = new OutboxRecord();

        MongoCollectionMapper collectionMapper = mock(MongoCollectionMapper.class);
        when(collectionMapper.getTariffPublicationOutboxCollectionName()).thenReturn(OUTBOX_COLLECTION);

        when(tmPlatformClient.getTariffs(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> List.of(tariff()));

        // Stand in for Mongo: the upsert always writes, the updateFirst only applies when the
        // record still carries the attempt token named in the query.
        doAnswer(invocation -> {
            outbox.applyWrite(invocation.getArgument(1));
            return null;
        }).when(mongoTemplate).upsert(any(Query.class), any(UpdateDefinition.class),
                eq(MongoTariffPublicationOutbox.class), anyString());

        doAnswer(invocation -> {
            outbox.applyConditionalWrite(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(mongoTemplate).updateFirst(any(Query.class), any(UpdateDefinition.class),
                eq(MongoTariffPublicationOutbox.class), anyString());

        service = new TariffSyncServiceImpl(
                tmPlatformClient,
                tariffService,
                mock(HubClientInfoService.class),
                mock(ApplicationConfiguration.class),
                mongoTemplate,
                collectionMapper,
                mock(TariffPublicationOutboxRepository.class));
    }

    @Test
    void marksPublicationDeliveredWhenHubPutSucceeds() {
        service.pullStoreAndBroadcast(COUNTRY, PARTY, from(), to());

        assertEquals(TariffPublicationStatus.DELIVERED, outbox.status());
    }

    @Test
    void marksPublicationPendingWhenHubPutFails() {
        doAnswer(invocation -> {
            throw new IllegalStateException("hub unreachable");
        }).when(tmPlatformClient).putTariffToHub(any(TariffDTO.class));

        service.pullStoreAndBroadcast(COUNTRY, PARTY, from(), to());

        assertEquals(TariffPublicationStatus.PENDING, outbox.status());
    }

    /**
     * The welcome ceremony runs on the async executor while the hourly sync runs on the scheduler
     * thread, so two publications of the same tariff can overlap. Ordering under test: the older PUT
     * is still in flight when a newer publication of the same tariff fails; the older PUT then
     * succeeds. The record must stay PENDING so the retry loop re-publishes the newer tariff.
     */
    @Test
    void lateHubPutDoesNotDeliverOverNewerFailedPublication() throws Exception {
        CountDownLatch olderPutEntered = new CountDownLatch(1);
        CountDownLatch newerPublicationFinished = new CountDownLatch(1);
        AtomicBoolean olderPutSucceeded = new AtomicBoolean(false);

        doAnswer(invocation -> {
            if (OLDER_PUBLISHER_THREAD.equals(Thread.currentThread().getName())) {
                olderPutEntered.countDown();
                if (!newerPublicationFinished.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("newer publication never finished");
                }
                olderPutSucceeded.set(true);
                return null;
            }
            throw new IllegalStateException("hub rejected the newer tariff");
        }).when(tmPlatformClient).putTariffToHub(any(TariffDTO.class));

        Thread older = new Thread(() -> service.pullStoreAndBroadcast(COUNTRY, PARTY, from(), to()),
                OLDER_PUBLISHER_THREAD);
        older.start();
        assertTrue(olderPutEntered.await(5, TimeUnit.SECONDS), "older PUT never started");

        service.pullStoreAndBroadcast(COUNTRY, PARTY, from(), to());
        newerPublicationFinished.countDown();
        older.join(TimeUnit.SECONDS.toMillis(5));

        assertTrue(olderPutSucceeded.get(), "the older PUT was expected to succeed late");
        assertEquals(TariffPublicationStatus.PENDING, outbox.status(),
                "a late PUT must not deliver over a newer publication that failed");
    }

    private TariffDTO tariff() {
        TariffDTO tariff = new TariffDTO();
        tariff.setCountryCode(COUNTRY);
        tariff.setPartyId(PARTY);
        tariff.setId(TARIFF_ID);
        return tariff;
    }

    private LocalDateTime to() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private LocalDateTime from() {
        return to().minusHours(1);
    }

    /** Minimal stand-in for the single outbox document this test exercises. */
    private static final class OutboxRecord {

        private TariffPublicationStatus status;
        private String attemptId;

        synchronized void applyWrite(UpdateDefinition update) {
            Document set = setDocument(update);
            status = (TariffPublicationStatus) set.get("status");
            if (set.containsKey("attemptId")) {
                attemptId = (String) set.get("attemptId");
            }
        }

        synchronized void applyConditionalWrite(Query query, UpdateDefinition update) {
            Document criteria = query.getQueryObject();
            if (criteria.containsKey("attemptId") && !Objects.equals(criteria.get("attemptId"), attemptId)) {
                return;
            }
            applyWrite(update);
        }

        synchronized TariffPublicationStatus status() {
            return status;
        }

        private Document setDocument(UpdateDefinition update) {
            return (Document) update.getUpdateObject().get("$set");
        }
    }
}
