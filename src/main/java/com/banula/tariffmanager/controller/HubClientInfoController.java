package com.banula.tariffmanager.controller;

import com.banula.openlib.ocpi.annotation.LogRequest;
import com.banula.openlib.ocpi.model.OcpiResponse;
import com.banula.tariffmanager.model.dto.HubClientInfoDTO;
import com.banula.tariffmanager.service.HubClientInfoService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/ocpi/2.2.1/hubclientinfo")
@AllArgsConstructor
@Slf4j
public class HubClientInfoController {

    private final HubClientInfoService hubClientInfoService;

    @PutMapping("/{countryCode}/{partyId}")
    @LogRequest
    public ResponseEntity<OcpiResponse<HubClientInfoDTO>> updateHubClientInfoByPartyIdAndCountryCode(
            @PathVariable String partyId,
            @PathVariable String countryCode,
            @RequestBody HubClientInfoDTO clientInfoDTO) {
        HubClientInfoDTO updated = hubClientInfoService.updateHubClientInfoByPartyIdAndCountryCode(partyId, countryCode,
                clientInfoDTO);
        return ResponseEntity.ok(new OcpiResponse<>(updated));
    }
}
