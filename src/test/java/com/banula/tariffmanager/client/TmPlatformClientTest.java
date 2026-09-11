package com.banula.tariffmanager.client;

import com.banula.tariffmanager.config.ApplicationConfiguration;
import com.banula.openlib.ocpi.exception.OCPICustomException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TmPlatformClientTest {
    @Test void followsClampedPagesUsingActualCountAndRoutesToCpo() {
        var rest = new RestTemplate(); var server = MockRestServiceServer.createServer(rest);
        var config = mock(ApplicationConfiguration.class);
        when(config.getPlatformUrl()).thenReturn("http://platform");
        when(config.getCountryCode()).thenReturn("DE"); when(config.getPartyId()).thenReturn("BAN");
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo("http://platform/api/v1/internal/outflow/ocpi/sender/2.2.1/tariffs?offset="+i+"&limit=100"))
                .andExpect(header("OCPI-to-country-code","DE")).andExpect(header("OCPI-to-party-id","OLI"))
                .andRespond(withSuccess("{\"status_code\":1000,\"data\":"+(i==2?"[]":"[{\"id\":\""+i+"\",\"country_code\":\"DE\",\"party_id\":\"OLI\"}]")+"}",MediaType.APPLICATION_JSON));
        }
        assertEquals(2,new TmPlatformClient(rest,config).getTariffs("DE","OLI",null,null).size());
        server.verify();
    }
    @Test void repeatedPagesFail() {
        var rest = new RestTemplate(); var server = MockRestServiceServer.createServer(rest);
        var config = mock(ApplicationConfiguration.class); when(config.getPlatformUrl()).thenReturn("http://platform");
        for (int i=0;i<2;i++) server.expect(requestTo("http://platform/api/v1/internal/outflow/ocpi/sender/2.2.1/tariffs?offset="+i+"&limit=100"))
            .andRespond(withSuccess("{\"status_code\":1000,\"data\":[{\"id\":\"same\"}]}",MediaType.APPLICATION_JSON));
        assertThrows(OCPICustomException.class,()->new TmPlatformClient(rest,config).getTariffs("DE","OLI",null,null));
    }

    @Test void parsesQuotedUnquotedAndMultipleLinkRelations() {
        for (String link : new String[]{"<http://next>; rel=next", "<http://next>; rel=\"next\"", "<http://prev>; rel=prev, <http://next>; title=\"last page\"; rel=\"next alternate\""}) {
            var rest = new RestTemplate(); var server = MockRestServiceServer.createServer(rest);
            var config = mock(ApplicationConfiguration.class); when(config.getPlatformUrl()).thenReturn("http://platform");
            server.expect(requestTo("http://platform/api/v1/internal/outflow/ocpi/sender/2.2.1/tariffs?offset=0&limit=100"))
                .andRespond(withSuccess("{\"status_code\":1000,\"data\":[{\"id\":\"one\"}]}",MediaType.APPLICATION_JSON).header("Link",link));
            server.expect(requestTo("http://platform/api/v1/internal/outflow/ocpi/sender/2.2.1/tariffs?offset=1&limit=100"))
                .andRespond(withSuccess("{\"status_code\":1000,\"data\":[{\"id\":\"two\"}]}",MediaType.APPLICATION_JSON).header("Link","<http://prev>; title=\"rel=next\"; rel=prev"));
            assertEquals(2,new TmPlatformClient(rest,config).getTariffs("DE","OLI",null,null).size()); server.verify();
        }
    }

    @Test void formatsDatesWithSecondsAndMillisecondUtcPrecision() {
        var rest = new RestTemplate(); var server = MockRestServiceServer.createServer(rest);
        var config = mock(ApplicationConfiguration.class); when(config.getPlatformUrl()).thenReturn("http://platform");
        server.expect(request -> {
            String query = java.net.URLDecoder.decode(request.getURI().getRawQuery(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(query.contains("date_from=2026-01-01T00:00:00.000Z"),query);
            assertTrue(query.contains("date_to=2026-01-01T00:00:00.123Z"),query);
        }).andRespond(withSuccess("{\"status_code\":1000,\"data\":[]}",MediaType.APPLICATION_JSON));
        new TmPlatformClient(rest,config).getTariffs("DE","OLI",java.time.LocalDateTime.of(2026,1,1,0,0),java.time.LocalDateTime.of(2026,1,1,0,0,0,123456789));
        server.verify();
    }

    @Test void pullsEveryHubPartyPageEvenWhenTheServerClampsThePageSize() {
        var rest = new RestTemplate();
        var server = MockRestServiceServer.createServer(rest);
        var config = mock(ApplicationConfiguration.class);
        when(config.getPlatformUrl()).thenReturn("http://platform");
        when(config.getCountryCode()).thenReturn("DE");
        when(config.getPartyId()).thenReturn("BAN");
        for (int offset = 0; offset < 3; offset++) {
            server.expect(requestTo("http://platform/api/v1/internal/outflow/ocpi/sender/2.2.1/hubclientinfo?offset=" + offset + "&limit=100"))
                .andExpect(header("OCPI-to-country-code", "DE"))
                .andExpect(header("OCPI-to-party-id", "BAN"))
                .andRespond(withSuccess("{\"status_code\":1000,\"data\":" + (offset == 2 ? "[]" : "[{}]") + "}",
                        MediaType.APPLICATION_JSON));
        }
        assertEquals(2, new TmPlatformClient(rest, config).getHubClientInfos().size());
        server.verify();
    }
}
