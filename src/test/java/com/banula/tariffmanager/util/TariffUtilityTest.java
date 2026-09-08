package com.banula.tariffmanager.util;

import com.banula.tariffmanager.config.MongoCollectionMapper;
import com.banula.tariffmanager.model.OnChainTariff;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TariffUtilityTest {
    @Test void appliesLiteralSearchAndPartyBeforePagination() {
        var mongo = mock(MongoTemplate.class); var collections = mock(MongoCollectionMapper.class);
        when(collections.getTariffCollectionName()).thenReturn("tariffs");
        new TariffUtility(mongo,collections).findTariffs(null,null,20,10,"de","oli","a.b+");
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(),eq(OnChainTariff.class),eq("tariffs"));
        var value = query.getValue();
        assertEquals("DE",value.getQueryObject().getString("countryCode"));
        assertEquals("OLI",value.getQueryObject().getString("partyId"));
        assertTrue(value.getQueryObject().toJson().contains("\\\\Qa.b+\\\\E"));
        assertEquals(20,value.getSkip()); assertEquals(10,value.getLimit());
        assertFalse(value.getSortObject().isEmpty());
    }
}
