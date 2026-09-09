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
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
    private static final int MAX_RECORDS = 10_000;
    private static final int MAX_TARIFF_RECORDS = 100_000;
    private static final DateTimeFormatter OCPI_DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'");
    private static final Pattern LINK_PARAMETER = Pattern.compile(";\\s*([^=;\\s]+)\\s*=\\s*(?:\"([^\"]*)\"|([^;\\s,]+))");

    private final RestTemplate restTemplate;
    private final ApplicationConfiguration applicationConfiguration;

    public List<TariffDTO> getTariffs(String toCountryCode, String toPartyId, LocalDateTime dateFrom,
            LocalDateTime dateTo) {
        List<TariffDTO> all = new ArrayList<>();
        int offset = 0;
        Set<List<String>> seen = new HashSet<>();
        while (true) {
            if (offset >= MAX_TARIFF_RECORDS) throw new OCPICustomException("Tariff pull exceeded its item limit");
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(applicationConfiguration.getPlatformUrl() + OUTFLOW_BASE + "/sender/" + VERSION
                            + "/tariffs")
                    .queryParam("offset", offset)
                    .queryParam("limit", PAGE_LIMIT);
            if (dateFrom != null) {
                builder.queryParam("date_from", dateFrom.format(OCPI_DATE_TIME));
            }
            if (dateTo != null) {
                builder.queryParam("date_to", dateTo.format(OCPI_DATE_TIME));
            }

            ResponseEntity<OcpiResponse<List<TariffDTO>>> responseEntity = exchangeEntity(
                    builder.encode().toUriString(),
                    HttpMethod.GET,
                    toCountryCode,
                    toPartyId,
                    null,
                    new ParameterizedTypeReference<OcpiResponse<List<TariffDTO>>>() {
                    });

            OcpiResponse<List<TariffDTO>> response = responseEntity.getBody();
            if (response == null || response.getStatus_code() != Constants.STATUS_CODE_OK) {
                String message = response != null ? response.getStatus_message() : "empty response";
                throw new OCPICustomException(
                        "Failed to pull tariffs from " + toCountryCode + "/" + toPartyId + ": " + message);
            }

            List<TariffDTO> page = response.getData();
            if (page == null) throw new OCPICustomException("Tariff pull returned no data field");
            if (page.isEmpty()) {
                break;
            }
            for (TariffDTO tariff : page) {
                if (tariff == null || tariff.getId() == null || !seen.add(Arrays.asList(tariff.getCountryCode(), tariff.getPartyId(), tariff.getId()))) {
                    throw new OCPICustomException("CPO returned invalid or duplicate tariffs");
                }
            }
            if (page.size() > MAX_TARIFF_RECORDS - all.size()) {
                throw new OCPICustomException("Tariff pull exceeded its item limit");
            }
            all.addAll(page);
            if (!hasNextPage(responseEntity.getHeaders(), offset, page.size())) {
                break;
            }
            offset += page.size();
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
        List<HubClientInfoDTO> all = new ArrayList<>();
        int offset = 0;
        while (true) {
            String url = UriComponentsBuilder
                    .fromHttpUrl(applicationConfiguration.getPlatformUrl() + OUTFLOW_BASE + "/sender/" + VERSION
                            + "/hubclientinfo")
                    .queryParam("offset", offset)
                    .queryParam("limit", PAGE_LIMIT)
                    .encode()
                    .toUriString();

            ResponseEntity<OcpiResponse<List<HubClientInfoDTO>>> responseEntity = exchangeEntity(
                    url,
                    HttpMethod.GET,
                    hubCountry,
                    hubParty,
                    null,
                    new ParameterizedTypeReference<OcpiResponse<List<HubClientInfoDTO>>>() {
                    });

            OcpiResponse<List<HubClientInfoDTO>> response = responseEntity.getBody();
            if (response == null || response.getStatus_code() != Constants.STATUS_CODE_OK) {
                String message = response != null ? response.getStatus_message() : "empty response";
                throw new OCPICustomException(
                        "Failed to pull hubclientinfo from hub " + hubCountry + "/" + hubParty + ": " + message);
            }

            List<HubClientInfoDTO> page = response.getData();
            if (page == null || page.isEmpty()) {
                break;
            }
            int remaining = MAX_RECORDS - all.size();
            if (page.size() > remaining) {
                all.addAll(page.subList(0, remaining));
            } else {
                all.addAll(page);
            }
            if (!hasNextPage(responseEntity.getHeaders(), offset, page.size())) {
                break;
            }
            offset += page.size();
            if (all.size() >= MAX_RECORDS) {
                log.warn("Stopping hubclientinfo pull from hub {}/{} at {} record(s): the server still advertises more",
                        hubCountry, hubParty, all.size());
                break;
            }
        }
        return all;
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
        return exchangeEntity(url, method, toCountryCode, toPartyId, body, responseType).getBody();
    }

    private <T, B> ResponseEntity<OcpiResponse<T>> exchangeEntity(
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
            headers.set("OCPI-from-country-code", applicationConfiguration.getCountryCode());
            headers.set("OCPI-from-party-id", applicationConfiguration.getPartyId());

            return restTemplate.exchange(
                    url,
                    method,
                    new HttpEntity<>(body, headers),
                    responseType);
        } catch (Exception e) {
            log.error("Platform request {} {} failed for {}/{}: {}", method, url, toCountryCode, toPartyId,
                    e.getMessage());
            throw new OCPICustomException("Platform request failed: " + e.getMessage());
        }
    }

    /**
     * Prefer OCPI 2.2.1 pagination headers ({@code Link} / {@code X-Total-Count}). Fall back to a
     * request until an empty page when those headers are absent (upstream may clamp page size).
     */
    private boolean hasNextPage(HttpHeaders headers, int offset, int pageSize) {
        if (headers == null) {
            return pageSize > 0;
        }
        List<String> links = headers.get(HttpHeaders.LINK);
        if (links != null) {
            for (String header : links) {
                for (String link : header.split(",(?=\\s*<)")) {
                    int end = link.indexOf('>');
                    if (end < 0) continue;
                    var params = LINK_PARAMETER.matcher(link.substring(end + 1));
                    while (params.find()) {
                        if (!"rel".equalsIgnoreCase(params.group(1))) continue;
                        String value = params.group(2) != null ? params.group(2) : params.group(3);
                        if (Arrays.stream(value.trim().split("\\s+")).anyMatch("next"::equalsIgnoreCase)) return true;
                    }
                }
            }
            return false;
        }
        String totalCount = headers.getFirst("X-Total-Count");
        if (totalCount != null && !totalCount.isBlank()) {
            try {
                return offset + pageSize < Integer.parseInt(totalCount.trim());
            } catch (NumberFormatException ignored) {
                log.debug("Ignoring unparsable X-Total-Count header: {}", totalCount);
            }
        }
        return pageSize > 0;
    }
}
