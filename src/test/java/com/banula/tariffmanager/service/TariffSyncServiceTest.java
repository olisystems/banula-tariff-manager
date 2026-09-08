package com.banula.tariffmanager.service;

import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.banula.openlib.ocpi.exception.OCPICustomException;
import com.banula.tariffmanager.client.TmPlatformClient;
import com.banula.tariffmanager.config.*;
import com.banula.tariffmanager.repository.TariffPublicationOutboxRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TariffSyncServiceTest {
    private final TmPlatformClient platform = mock(TmPlatformClient.class);
    private final TMTariffService tariffs = mock(TMTariffService.class);
    private final TariffSyncService service = new TariffSyncServiceImpl(platform, tariffs,
            mock(HubClientInfoService.class), mock(ApplicationConfiguration.class), mock(MongoTemplate.class),
            mock(MongoCollectionMapper.class), mock(TariffPublicationOutboxRepository.class));

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
}
