package com.banula.tariffmanager.service;

import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.banula.tariffmanager.client.TmPlatformClient;
import com.banula.tariffmanager.config.ApplicationConfiguration;
import com.banula.tariffmanager.config.MongoCollectionMapper;
import com.banula.tariffmanager.model.MongoTariffPublicationOutbox;
import com.banula.tariffmanager.model.TariffPublicationStatus;
import com.banula.tariffmanager.repository.TariffPublicationOutboxRepository;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Preserves develop's slow-PUT regression with the PR's bounded retry and outbox cleanup behavior. */
class TariffSyncServiceImplTest {
    private static final String OLDER_THREAD = "older-publisher";
    private TmPlatformClient platform;
    private TariffSyncServiceImpl service;
    private OutboxState state;

    @BeforeEach
    void setUp() {
        platform = mock(TmPlatformClient.class);
        var tariffs = mock(TMTariffService.class);
        var mongo = mock(MongoTemplate.class);
        var outbox = mock(TariffPublicationOutboxRepository.class);
        var config = mock(ApplicationConfiguration.class);
        var collections = mock(MongoCollectionMapper.class);
        state = new OutboxState();
        when(config.getTariffPublicationMaxAttempts()).thenReturn(3);
        when(config.getTariffPublicationBackoffSeconds()).thenReturn(3600L);
        when(collections.getTariffPublicationOutboxCollectionName()).thenReturn("outbox");
        when(platform.getTariffs(anyString(), anyString(), any(), any()))
                .thenAnswer(call -> List.of(tariff()));
        when(outbox.findByCountryCodeAndPartyIdAndTariffId(anyString(), anyString(), anyString()))
                .thenAnswer(call -> state.read());
        when(outbox.save(any(MongoTariffPublicationOutbox.class)))
                .thenAnswer(call -> state.save(call.getArgument(0)));
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class),
                eq(MongoTariffPublicationOutbox.class), eq("outbox")))
                .thenAnswer(call -> state.update(call.getArgument(0), call.getArgument(1)));
        when(mongo.remove(any(Query.class), eq(MongoTariffPublicationOutbox.class), eq("outbox")))
                .thenAnswer(call -> state.remove(call.getArgument(0)));
        service = new TariffSyncServiceImpl(platform, tariffs, mock(HubClientInfoService.class),
                config, mongo, collections, outbox);
    }

    @Test
    void successfulPublicationCleansUpItsOutboxRecord() {
        pull();
        assertTrue(state.read().isEmpty());
    }

    @Test
    void failedPublicationKeepsBoundedPendingRetry() {
        doThrow(new IllegalStateException("hub unreachable")).when(platform).putTariffToHub(any());
        pull();
        var pending = state.read().orElseThrow();
        assertEquals(TariffPublicationStatus.PENDING, pending.getStatus());
        assertEquals(1, pending.getAttempts());
        assertNotNull(pending.getNextAttemptAt());
    }

    @Test
    void lateSuccessCannotDeleteNewerFailedPublication() throws Exception {
        verifyLateCompletion(true);
    }

    @Test
    void lateFailureCannotOverwriteNewerRetryState() throws Exception {
        verifyLateCompletion(false);
    }

    private void verifyLateCompletion(boolean olderSucceeds) throws Exception {
        var olderEntered = new CountDownLatch(1);
        var releaseOlder = new CountDownLatch(1);
        var threadError = new AtomicReference<Throwable>();
        doAnswer(call -> {
            if (OLDER_THREAD.equals(Thread.currentThread().getName())) {
                olderEntered.countDown();
                if (!releaseOlder.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timed out");
                if (olderSucceeds) return null;
                throw new IllegalStateException("older failure");
            }
            throw new IllegalStateException("newer failure");
        }).when(platform).putTariffToHub(any());
        var older = new Thread(() -> {
            try { pull(); } catch (Throwable error) { threadError.set(error); }
        }, OLDER_THREAD);
        older.start();
        try {
            assertTrue(olderEntered.await(5, TimeUnit.SECONDS), "older PUT never started");
            pull();
        } finally {
            releaseOlder.countDown();
            older.join(5000);
        }
        assertFalse(older.isAlive());
        assertNull(threadError.get());
        var pending = state.read().orElseThrow();
        assertEquals(TariffPublicationStatus.PENDING, pending.getStatus());
        assertEquals("newer failure", pending.getLastError());
        assertEquals(1, pending.getAttempts());
        assertEquals(LocalDateTime.of(2026, 1, 2, 0, 0), pending.getTariffLastUpdated());
    }

    private void pull() { service.pullStoreAndBroadcast("DE", "ABC", null, null); }

    private TariffDTO tariff() {
        var tariff = new TariffDTO();
        tariff.setCountryCode("DE");
        tariff.setPartyId("ABC");
        tariff.setId("tariff-1");
        tariff.setLastUpdated(LocalDateTime.of(2026, 1,
                OLDER_THREAD.equals(Thread.currentThread().getName()) ? 1 : 2, 0, 0));
        return tariff;
    }

    /** Return detached records and apply compare-and-set conditions like the Mongo operations. */
    private static class OutboxState {
        private MongoTariffPublicationOutbox record;

        synchronized Optional<MongoTariffPublicationOutbox> read() {
            if (record == null) return Optional.empty();
            var copy = new MongoTariffPublicationOutbox();
            BeanUtils.copyProperties(record, copy);
            return Optional.of(copy);
        }

        synchronized MongoTariffPublicationOutbox save(MongoTariffPublicationOutbox value) {
            record = new MongoTariffPublicationOutbox();
            BeanUtils.copyProperties(value, record);
            return value;
        }

        synchronized UpdateResult update(Query query, UpdateDefinition update) {
            if (!matches(query.getQueryObject())) return UpdateResult.acknowledged(0, 0L, null);
            var bean = new BeanWrapperImpl(record);
            Document changes = update.getUpdateObject();
            changes.get("$set", new Document()).forEach(bean::setPropertyValue);
            changes.get("$unset", new Document()).keySet().forEach(key -> bean.setPropertyValue(key, null));
            return UpdateResult.acknowledged(1, 1L, null);
        }

        synchronized DeleteResult remove(Query query) {
            if (!matches(query.getQueryObject())) return DeleteResult.acknowledged(0);
            record = null;
            return DeleteResult.acknowledged(1);
        }

        private boolean matches(Document query) {
            if (record == null) return false;
            var bean = new BeanWrapperImpl(record);
            for (Map.Entry<String, Object> entry : query.entrySet()) {
                if (entry.getKey().equals("$or")) {
                    if (((List<Document>) entry.getValue()).stream().noneMatch(this::matches)) return false;
                } else if (!Objects.equals(entry.getValue(), bean.getPropertyValue(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }
}
