package com.banula.tariffmanager.controller.nonocpi;

import com.banula.tariffmanager.service.ObjectSearchService;
import com.banula.tariffmanager.service.SearchCriteria;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;

/** Dashboard search only; standard OCPI list endpoints are unchanged. */
@RestController
@RequestMapping("/api/v1/internal/nonocpi/objects")
@RequiredArgsConstructor
public class NonOcpiObjectSearchController {
    private final ObjectSearchService search;

    @GetMapping("/{module}/search")
    public ObjectSearchService.SearchPage search(
            @PathVariable("module") String module,
            @RequestParam(value = "id", required = false) String id,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "country_code", required = false) String countryCode,
            @RequestParam(value = "party_id", required = false) String partyId,
            @RequestParam(value = "date_from", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime dateFrom,
            @RequestParam(value = "date_to", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    OffsetDateTime dateTo,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "25") int limit) {
        return search.search(
                module,
                SearchCriteria.builder()
                        .id(id)
                        .name(name)
                        .countryCode(countryCode)
                        .partyId(partyId)
                        .dateFrom(dateFrom == null ? null : dateFrom.toInstant())
                        .dateTo(dateTo == null ? null : dateTo.toInstant())
                        .offset(offset)
                        .limit(limit)
                        .build());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> invalidSearch(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(
                        Map.of(
                                "error",
                                error.getReason() == null ? "Invalid search" : error.getReason()));
    }
}
