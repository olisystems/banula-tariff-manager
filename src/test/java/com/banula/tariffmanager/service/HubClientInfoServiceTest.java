package com.banula.tariffmanager.service;

import com.banula.openlib.ocpi.exception.OCPICustomException;
import com.banula.openlib.ocpi.model.enums.Role;
import com.banula.tariffmanager.client.TmPlatformClient;
import com.banula.tariffmanager.config.MongoCollectionMapper;
import com.banula.tariffmanager.model.dto.HubClientInfoDTO;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HubClientInfoServiceTest {
    @Test void missingStatusCannotOverwriteAStoredParty() {
        var mongo = mock(MongoTemplate.class);
        var service = new HubClientInfoServiceImpl(mongo, mock(MongoCollectionMapper.class),
            mock(ApplicationEventPublisher.class), mock(TmPlatformClient.class));
        var incomplete = new HubClientInfoDTO();
        incomplete.setRole(Role.CPO);
        assertThrows(OCPICustomException.class,
            () -> service.updateHubClientInfoByPartyIdAndCountryCode("OLI", "DE", incomplete));
        verifyNoInteractions(mongo);
    }

    @Test void unusableRecordsDoNotStopLaterPartiesBeingProcessed() {
        var platform = mock(TmPlatformClient.class);
        var service = spy(new HubClientInfoServiceImpl(mock(MongoTemplate.class), mock(MongoCollectionMapper.class),
            mock(ApplicationEventPublisher.class), platform));
        var bad = new HubClientInfoDTO();
        bad.setCountryCode("DE"); bad.setPartyId("BAD");
        var good = new HubClientInfoDTO();
        good.setCountryCode("DE"); good.setPartyId("OLI");
        when(platform.getHubClientInfos()).thenReturn(Arrays.asList(null, new HubClientInfoDTO(), bad, good));
        doReturn(good).when(service).updateHubClientInfoByPartyIdAndCountryCode("OLI", "DE", good);
        service.syncAllHubClientInfoParties();
        verify(service).updateHubClientInfoByPartyIdAndCountryCode("OLI", "DE", good);
    }
}
