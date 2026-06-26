package com.banula.tariffmanager.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.banula.openlib.ocpi.annotation.LogRequest;
import com.banula.openlib.ocpi.annotation.OcpiGetCompositeId;
import com.banula.openlib.ocpi.annotation.OcpiPutCompositeId;
import com.banula.openlib.ocpi.exception.OCPICustomException;
import com.banula.openlib.ocpi.model.OcpiResponse;
import com.banula.openlib.ocpi.model.dto.TariffDTO;
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
    @GetMapping("/{countryCode}/{partyId}/{tariffId}")
    @OcpiGetCompositeId
    public ResponseEntity<OcpiResponse<TariffDTO>> getTariff(
            @PathVariable(value = "countryCode") String countryCode,
            @PathVariable(value = "partyId") String partyId,
            @PathVariable(value = "tariffId") String tariffId) {
        log.info("Retrieving tariff for party: " + countryCode + "-" + partyId + " and tariff: " + tariffId);
        TariffDTO tariff = tmTariffService.getTariff(countryCode, partyId, tariffId);
        return ResponseEntity.ok(new OcpiResponse<>(tariff));
    }

    @CrossOrigin
    @DeleteMapping("/{countryCode}/{partyId}/{tariffId}")
    public ResponseEntity<OcpiResponse<String>> deleteTariff(
            @PathVariable(value = "countryCode") String countryCode,
            @PathVariable(value = "partyId") String partyId,
            @PathVariable(value = "tariffId") String tariffId,
            @RequestHeader("ocpi-from-country-code") String fromCountryCode,
            @RequestHeader("ocpi-from-party-id") String fromPartyId) {
        log.info("Delete request for countryCode {}, partyId {}, tariffId {}", countryCode, partyId, tariffId);
        if (!fromCountryCode.equalsIgnoreCase(countryCode) || !fromPartyId.equalsIgnoreCase(partyId)) {
            throw new OCPICustomException(
                    "Caller ocpi-from credentials do not match the tariff's countryCode/partyId", 2001);
        }
        tmTariffService.deleteTariff(countryCode, partyId, tariffId);
        return ResponseEntity.ok(new OcpiResponse<>(null));
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
