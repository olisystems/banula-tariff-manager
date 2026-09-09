package com.banula.tariffmanager.service;

import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.banula.openlib.ocpi.exception.OCPICustomException;
import com.banula.tariffmanager.client.TmPlatformClient;
import com.banula.tariffmanager.config.*;
import com.banula.tariffmanager.repository.TariffPublicationOutboxRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import com.banula.tariffmanager.model.MongoTariffPublicationOutbox;
import com.banula.tariffmanager.model.TariffPublicationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Query;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TariffSyncServiceTest {
    private final TmPlatformClient platform = mock(TmPlatformClient.class);
    private final TMTariffService tariffs = mock(TMTariffService.class);
    private final HubClientInfoService parties = mock(HubClientInfoService.class);
    private final ApplicationConfiguration config = mock(ApplicationConfiguration.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final MongoCollectionMapper collections = mock(MongoCollectionMapper.class);
    private final TariffPublicationOutboxRepository outbox = mock(TariffPublicationOutboxRepository.class);
    private final TariffSyncService service = new TariffSyncServiceImpl(platform, tariffs, parties, config, mongo, collections, outbox);

    TariffSyncServiceTest() {
        when(config.getTariffPublicationMaxAttempts()).thenReturn(3);
        when(config.getTariffPublicationBackoffSeconds()).thenReturn(3600L);
        when(config.getTariffPublicationBatchSize()).thenReturn(25);
        when(config.getTariffSyncLookbackHours()).thenReturn(1L);
        when(collections.getTariffPublicationOutboxCollectionName()).thenReturn("outbox");
    }

    private TariffDTO tariff(String id) {
        var tariff = new TariffDTO(); tariff.setId(id); tariff.setCountryCode("DE"); tariff.setPartyId("OLI"); return tariff;
    }
    @Test void reportsStorageFailuresAndPendingPublications() {
        var good = tariff("good"); var bad = tariff("bad"); var pending = tariff("pending");
        when(platform.getTariffs("DE", "OLI", null, null)).thenReturn(List.of(good,bad,pending));
        when(tariffs.saveTariff(bad)).thenThrow(new RuntimeException("save failed"));
        doThrow(new RuntimeException("hub unavailable")).when(platform).putTariffToHub(pending);
        assertEquals(new TariffSyncService.SyncResult(3,2,1,1),service.pullStoreAndBroadcast("DE","OLI",null,null));
        verify(platform,never()).putTariffToHub(bad);
        verify(platform).putTariffToHub(good);
    }
    @Test void validatesAllOwnersBeforeWriting() {
        var wrong = tariff("wrong"); wrong.setPartyId("ABC");
        when(platform.getTariffs("DE","OLI",null,null)).thenReturn(List.of(tariff("good"),wrong));
        assertThrows(OCPICustomException.class, () -> service.pullStoreAndBroadcast("DE","OLI",null,null));
        verifyNoInteractions(tariffs);
    }

    @Test void failuresBackOffAndStopAtConfiguredLimit() {
        var record = new MongoTariffPublicationOutbox();
        record.setCountryCode("DE"); record.setPartyId("OLI"); record.setTariffId("retry");
        record.setStatus(TariffPublicationStatus.PENDING);
        var tariff = tariff("retry");
        when(outbox.findDue(any(), any())).thenReturn(List.of(record));
        when(tariffs.getTariff("DE", "OLI", "retry")).thenReturn(tariff);
        doThrow(new RuntimeException("rejected")).when(platform).putTariffToHub(tariff);
        service.syncRecentTariffs();
        assertEquals(1, record.getAttempts());
        assertEquals(record.getLastAttemptAt().plusHours(1), record.getNextAttemptAt());
        service.syncRecentTariffs(); // Even if returned by the repository, not yet due.
        verify(platform, times(1)).putTariffToHub(tariff);
        record.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        service.syncRecentTariffs();
        assertEquals(record.getLastAttemptAt().plusHours(2), record.getNextAttemptAt());
        record.setNextAttemptAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        service.syncRecentTariffs();
        assertEquals(3, record.getAttempts());
        assertEquals(TariffPublicationStatus.FAILED, record.getStatus());
        assertNull(record.getNextAttemptAt());
        service.syncRecentTariffs();
        verify(platform, times(3)).putTariffToHub(tariff);
        var batch = ArgumentCaptor.forClass(Pageable.class);
        verify(outbox, atLeastOnce()).findDue(any(), batch.capture());
        assertEquals(25, batch.getValue().getPageSize());
    }

    @Test void repeatedPullDoesNotResetExhaustedRevisionAndNewRevisionCanPublish() {
        var tariff = tariff("retry");
        tariff.setLastUpdated(LocalDateTime.of(2026, 1, 1, 0, 0));
        var record = new MongoTariffPublicationOutbox();
        record.setTariffLastUpdated(tariff.getLastUpdated());
        record.setAttempts(3); record.setStatus(TariffPublicationStatus.FAILED);
        when(outbox.findByCountryCodeAndPartyIdAndTariffId("DE", "OLI", "retry")).thenReturn(Optional.of(record));
        when(platform.getTariffs("DE", "OLI", null, null)).thenReturn(List.of(tariff));
        service.pullStoreAndBroadcast("DE", "OLI", null, null);
        assertEquals(3, record.getAttempts());
        verify(platform, never()).putTariffToHub(any());
        tariff.setLastUpdated(tariff.getLastUpdated().plusHours(1));
        service.pullStoreAndBroadcast("DE", "OLI", null, null);
        verify(platform).putTariffToHub(tariff);
        var removed = ArgumentCaptor.forClass(Query.class);
        verify(mongo).remove(removed.capture(), eq(MongoTariffPublicationOutbox.class), eq("outbox"));
        assertEquals("retry", removed.getValue().getQueryObject().get("tariffId"));
        assertEquals("OLI", removed.getValue().getQueryObject().get("partyId"));
        assertEquals("DE", removed.getValue().getQueryObject().get("countryCode"));
    }
}
