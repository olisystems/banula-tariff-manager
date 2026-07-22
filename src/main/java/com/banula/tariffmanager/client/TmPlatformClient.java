package com.banula.tariffmanager.client;

import com.banula.openlib.ocpi.exception.OCPICustomException;
import com.banula.openlib.ocpi.model.OcpiResponse;
import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.banula.openlib.ocpi.util.Constants;
import com.banula.tariffmanager.config.ApplicationConfiguration;
import com.banula.tariffmanager.model.dto.HubClientInfoDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Banula-platform client: SENDER pulls from parties, RECEIVER puts to the hub (DE/BAN) so the node
 * broadcasts.
 */
@Slf4j
@Component
@AllArgsConstructor
public class TmPlatformClient {

    private static final String OUTFLOW_BASE = "/api/v1/internal/outflow/ocpi";
    private static final String VERSION = "2.2.1";
    private static final int PAGE_LIMIT = 100;

    private final RestTemplate restTemplate;
    private final ApplicationConfiguration applicationConfiguration;

    public List<TariffDTO> getTariffs(String toCountryCode, String toPartyId, LocalDateTime dateFrom,
            LocalDateTime dateTo) {
        List<TariffDTO> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(applicationConfiguration.getPlatformUrl() + OUTFLOW_BASE + "/sender/" + VERSION
                            + "/tariffs")
                    .queryParam("offset", offset)
                    .queryParam("limit", PAGE_LIMIT);
            if (dateFrom != null) {
                builder.queryParam("date_from", dateFrom.toString());
            }
            if (dateTo != null) {
                builder.queryParam("date_to", dateTo.toString());
            }

            OcpiResponse<List<TariffDTO>> response = exchange(
                    builder.encode().toUriString(),
                    HttpMethod.GET,
                    toCountryCode,
                    toPartyId,
                    null,
                    new ParameterizedTypeReference<OcpiResponse<List<TariffDTO>>>() {
                    });

            if (response == null || response.getStatus_code() != Constants.STATUS_CODE_OK) {
                String message = response != null ? response.getStatus_message() : "empty response";
                throw new OCPICustomException(
                        "Failed to pull tariffs from " + toCountryCode + "/" + toPartyId + ": " + message);
            }

            List<TariffDTO> page = response.getData();
            if (page == null || page.isEmpty()) {
                break;
            }
            all.addAll(page);
            if (page.size() < PAGE_LIMIT) {
                break;
            }
            offset += PAGE_LIMIT;
        }
        return all;
    }

    /**
     * GET hubclientinfo from the hub identity (DE/BAN) so tariff-manager can discover connected
     * CPOs and run welcome sync.
     */
    public List<HubClientInfoDTO> getHubClientInfos() {
        String hubCountry = applicationConfiguration.getCountryCode();
        String hubParty = applicationConfiguration.getPartyId();
        String url = applicationConfiguration.getPlatformUrl() + OUTFLOW_BASE + "/sender/" + VERSION
                + "/hubclientinfo";

        OcpiResponse<List<HubClientInfoDTO>> response = exchange(
                url,
                HttpMethod.GET,
                hubCountry,
                hubParty,
                null,
                new ParameterizedTypeReference<OcpiResponse<List<HubClientInfoDTO>>>() {
                });

        if (response == null || response.getStatus_code() != Constants.STATUS_CODE_OK) {
            String message = response != null ? response.getStatus_message() : "empty response";
            throw new OCPICustomException(
                    "Failed to pull hubclientinfo from hub " + hubCountry + "/" + hubParty + ": " + message);
        }
        return response.getData() != null ? response.getData() : Collections.emptyList();
    }

    /**
     * PUT tariff to the hub identity (DE/BAN). The OCN node broadcasts to parties with Tariffs
     * RECEIVER enabled.
     */
    public void putTariffToHub(TariffDTO tariff) {
        String hubCountry = applicationConfiguration.getCountryCode();
        String hubParty = applicationConfiguration.getPartyId();
        String url = UriComponentsBuilder
                .fromHttpUrl(applicationConfiguration.getPlatformUrl() + OUTFLOW_BASE + "/receiver/" + VERSION
                        + "/tariffs")
                .pathSegment(tariff.getCountryCode(), tariff.getPartyId(), tariff.getId())
                .encode()
                .toUriString();

        OcpiResponse<String> response = exchange(
                url,
                HttpMethod.PUT,
                hubCountry,
                hubParty,
                tariff,
                new ParameterizedTypeReference<OcpiResponse<String>>() {
                });

        if (response == null || response.getStatus_code() != Constants.STATUS_CODE_OK) {
            String message = response != null ? response.getStatus_message() : "empty response";
            throw new OCPICustomException(
                    "Failed to put tariff " + tariff.getId() + " to hub " + hubCountry + "/" + hubParty + ": "
                            + message);
        }
    }

    private <T, B> OcpiResponse<T> exchange(
            String url,
            HttpMethod method,
            String toCountryCode,
            String toPartyId,
            B body,
            ParameterizedTypeReference<OcpiResponse<T>> responseType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("Accept", "*/*");
            headers.set("X-Request-ID", UUID.randomUUID().toString());
            headers.set("X-Correlation-ID", UUID.randomUUID().toString());
            headers.set("OCPI-to-country-code", toCountryCode);
            headers.set("OCPI-to-party-id", toPartyId);

            ResponseEntity<OcpiResponse<T>> response = restTemplate.exchange(
                    url,
                    method,
                    new HttpEntity<>(body, headers),
                    responseType);
            return response.getBody();
        } catch (Exception e) {
            log.error("Platform request {} {} failed for {}/{}: {}", method, url, toCountryCode, toPartyId,
                    e.getMessage());
            throw new OCPICustomException("Platform request failed: " + e.getMessage());
        }
    }
}
