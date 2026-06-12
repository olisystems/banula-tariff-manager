package com.banula.tariffmanager.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.banula.openlib.ocpi.annotation.LogRequest;
import com.banula.openlib.ocpi.annotation.OcpiPutCompositeId;
import com.banula.openlib.ocpi.exception.OCPICustomException;
import com.banula.openlib.ocpi.model.OcpiResponse;
import com.banula.openlib.ocpi.model.dto.TariffDTO;
import com.banula.tariffmanager.model.OnChainTariff;
import com.banula.tariffmanager.model.hash.HashVerifyRequest;
import com.banula.tariffmanager.model.hash.HashVerifyResponse;
import com.banula.tariffmanager.service.TMTariffService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/internal/ocpi/2.2.1/tariffs")
@Tag(name = "TMTariffs")
@AllArgsConstructor
@Slf4j
public class TMTariffController {
    private final TMTariffService tmTariffService;

    @CrossOrigin
    @PutMapping("/{countryCode}/{partyId}/{tariffId}")
    @OcpiPutCompositeId
    public ResponseEntity<OcpiResponse<String>> saveTariff(
            @RequestBody TariffDTO tariffDTO,
            @PathVariable(value = "countryCode") String countryCode,
            @PathVariable(value = "partyId") String partyId,
            @PathVariable(value = "tariffId") String tariffId) {
        log.info("Saving tariffDTO: " + tariffDTO);
        log.info("CountryCode {}, partyId {}, tariffId {}", countryCode, partyId, tariffId);
        tmTariffService.saveTariff(tariffDTO);
        return ResponseEntity.ok(new OcpiResponse<>(null));
    }

    @CrossOrigin
    @GetMapping("/{countryCode}/{partyId}/{datetime}")
    public ResponseEntity<OcpiResponse<OnChainTariff>> getLatestTariffFromParty(
            @PathVariable(value = "countryCode") String countryCode,
            @PathVariable(value = "partyId") String partyId,
            @PathVariable(value = "datetime") LocalDateTime datetime) {
        log.info("Retrieving tariff for party: " + countryCode + "-" + partyId + " at " + datetime);
        OnChainTariff tariff = tmTariffService.getLatestTariffForPartyAt(countryCode, partyId, datetime);
        return ResponseEntity.ok(new OcpiResponse<>(tariff));
    }

    @CrossOrigin
    @PostMapping("/{countryCode}/{partyId}/verify")
    public ResponseEntity<OcpiResponse<HashVerifyResponse>> verifyTariff(
            @PathVariable(value = "countryCode") String countryCode,
            @PathVariable(value = "partyId") String partyId,
            @RequestBody HashVerifyRequest verifyRequest) {
        String computedHash = tmTariffService.hashTariff(verifyRequest.getTariff());
        boolean isHashValid = computedHash.equals(verifyRequest.getHash());

        if (!isHashValid) {
            log.error("Hash verification failed for tariff: " + verifyRequest.getTariff());
            throw new OCPICustomException("Computed hash doesn't match provided hash", 2001);
        }

        OnChainTariff tariff = tmTariffService.getTariffByHash(computedHash);

        if (tariff == null) {
            log.error("Hash verification was successful but tariff was not previously appended"
                    + verifyRequest.getTariff());
            throw new OCPICustomException("Hash verification was successful but tariff was not previously appended",
                    2001);
        }

        HashVerifyResponse response = new HashVerifyResponse();
        response.setVerified(isHashValid);
        response.setTransactionId(tariff.getTransactionId());

        return ResponseEntity.ok(new OcpiResponse<>(response));
    }

    @GetMapping
    @LogRequest
    public ResponseEntity<OcpiResponse<List<TariffDTO>>> getTariffs(
            @RequestParam(value = "date_from", required = false) LocalDateTime dateFrom,
            @RequestParam(value = "date_to", required = false) LocalDateTime dateTo,
            @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset,
            @RequestParam(value = "limit", required = false) Integer limit) {

        log.info("Fetching tariffs dateFrom {}, dateTo {}, offset {}, limit {}", dateFrom, dateTo, offset, limit);
        return ResponseEntity
                .ok(new OcpiResponse<>(tmTariffService.findTariffsBetweenDates(dateFrom, dateTo, offset, limit)));
    }

}
